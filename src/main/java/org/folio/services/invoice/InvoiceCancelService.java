package org.folio.services.invoice;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static org.folio.invoices.utils.ErrorCodes.CANCEL_TRANSACTIONS_ERROR;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.CREDIT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PENDING_PAYMENT;

import java.util.List;

import io.vertx.core.Future;
import lombok.extern.log4j.Log4j2;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PurchaseOrder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.finance.EncumbranceUtils;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.voucher.VoucherService;
import org.springframework.util.CollectionUtils;

@Log4j2
public class InvoiceCancelService {

  private static final String PO_LINES_WITH_RIGHT_PAYMENT_STATUS_QUERY =
    "paymentStatus==(\"Awaiting Payment\" OR \"Partially Paid\" OR \"Fully Paid\" OR \"Ongoing\" OR \"Payment Not Required\")";
  private static final String OPEN_ORDERS_QUERY = "workflowStatus==\"Open\"";
  private static final String AND = " AND ";

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final VoucherService voucherService;
  private final OrderLineService orderLineService;
  private final OrderService orderService;
  private final PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService;
  private final InvoiceWorkflowDataHolderBuilder holderBuilder;

  public InvoiceCancelService(BaseTransactionService baseTransactionService,
                              EncumbranceService encumbranceService,
                              VoucherService voucherService,
                              OrderLineService orderLineService,
                              OrderService orderService,
                              PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService,
                              InvoiceWorkflowDataHolderBuilder holderBuilder) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.voucherService = voucherService;
    this.orderLineService = orderLineService;
    this.orderService = orderService;
    this.poLinePaymentStatusUpdateService = poLinePaymentStatusUpdateService;
    this.holderBuilder = holderBuilder;
  }

  /**
   * Handles transition of given invoice to {@link Invoice.Status#CANCELLED} status.
   * The following are set to cancelled, in order:
   * - pending payments and payments/credits transactions
   * - invoice lines
   * - vouchers
   *
   * @param invoiceFromStorage invoice from storage
   * @param lines              lines from the new invoice
   * @return CompletableFuture that indicates when the transition is completed
   */
  public Future<Void> cancelInvoice(Invoice invoiceFromStorage, List<InvoiceLine> lines, String poLinePaymentStatus,
                                    RequestContext requestContext) {
    String invoiceId = invoiceFromStorage.getId();
    log.info("cancelInvoice:: Cancelling invoice {}...", invoiceId);

    return Future.succeededFuture()
      .map(v -> {
        validateCancelInvoice(invoiceFromStorage);
        return null;
      })
      .compose(v -> validateBudgetsStatus(invoiceFromStorage, lines, requestContext))
      .compose(v -> getTransactions(invoiceId, requestContext))
      .compose(transactions -> cancelTransactions(invoiceId, transactions, requestContext))
      .map(v -> {
        cancelInvoiceLines(lines);
        return null;
      })
      .compose(v -> cancelVoucher(invoiceId, requestContext))
      .compose(v -> updateOrUnreleaseEncumbrances(lines, invoiceFromStorage, requestContext))
      .compose(v -> poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToCancelInvoice(invoiceFromStorage,
        lines, poLinePaymentStatus, requestContext))
      .onSuccess(v -> log.info("cancelInvoice:: Invoice {} cancelled successfully", invoiceId))
      .onFailure(t -> log.error("cancelInvoice:: Failed to cancel invoice {}", invoiceId, t));
  }

  private void validateCancelInvoice(Invoice invoiceFromStorage) {
    List<Invoice.Status> cancellable = List.of(Invoice.Status.APPROVED, Invoice.Status.PAID);
    if (!cancellable.contains(invoiceFromStorage.getStatus())) {
      var param = new Parameter().withKey(INVOICE_ID).withValue(invoiceFromStorage.getId());
      throw new HttpException(422, CANNOT_CANCEL_INVOICE, List.of(param));
    }
  }

  /**
   * Performs validation of budget statuses associated with an invoice.
   * Associated budgets should have {@link org.folio.rest.acq.model.finance.Budget.BudgetStatus#ACTIVE} status
   * to pass validation successfully.
   *
   * @param invoice        The invoice.This parameter is necessary to extract the associated budgets.
   * @param lines          The list of invoice lines. This parameter is necessary to extract the associated budgets.
   * @param requestContext The request context providing additional information.
   * @return A `Future` of type `Void`, representing the result of the validation. If the future succeeds,
   * it indicates that the validation has been successfully completed, and active budgets have been extracted
   * @throws HttpException If no active budgets are found, an exception is thrown.
   */
  private Future<Void> validateBudgetsStatus(Invoice invoice, List<InvoiceLine> lines, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withBudgets(dataHolders, requestContext)
      .onFailure(t -> log.error("validateBudgetsStatus:: Could not find an active budget for the invoice with id {}",
        invoice.getId(), t))
      .mapEmpty();
  }

  private Future<List<Transaction>> getTransactions(String invoiceId, RequestContext requestContext) {
    String query = String.format("sourceInvoiceId==%s", invoiceId);
    List<TransactionType> relevantTransactionTypes = List.of(PENDING_PAYMENT, PAYMENT, CREDIT);
    return baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext)
      .map(TransactionCollection::getTransactions)
      .map(transactions -> transactions.stream()
        .filter(tr -> relevantTransactionTypes.contains(tr.getTransactionType())).toList());
  }

  private Future<Void> cancelTransactions(String invoiceId, List<Transaction> transactions, RequestContext requestContext) {
    if (transactions.isEmpty()) {
      return succeededFuture(null);
    }
    log.info("cancelTransactions:: Cancelling invoice transactions, invoiceId={}...", invoiceId);
    return baseTransactionService.batchCancel(transactions, requestContext)
      .recover(t -> {
        log.error("cancelTransactions:: Failed to cancel transactions for invoice with id {}", invoiceId, t);
        var param = new Parameter().withKey(INVOICE_ID).withValue(invoiceId);
        var causeParam = new Parameter().withKey("cause").withValue(t.getMessage());
        throw new HttpException(500, CANCEL_TRANSACTIONS_ERROR, List.of(param, causeParam));
      });
  }

  private void cancelInvoiceLines(List<InvoiceLine> lines) {
    lines.forEach(line -> line.setInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.CANCELLED));
  }

  private Future<Void> cancelVoucher(String invoiceId, RequestContext requestContext) {
    log.info("cancelVoucher:: Cancelling voucher, invoiceId={}...", invoiceId);
    return voucherService.cancelInvoiceVoucher(invoiceId, requestContext);
  }

  private Future<Void> updateOrUnreleaseEncumbrances(List<InvoiceLine> invoiceLines, Invoice invoiceFromStorage,
                                                     RequestContext requestContext) {
    var poLineIds = invoiceLines.stream()
      .map(InvoiceLine::getPoLineId)
      .distinct()
      .toList();
    if (poLineIds.isEmpty()) {
      return succeededFuture();
    }
    var invoiceId = invoiceFromStorage.getId();
    log.info("updateOrUnreleaseEncumbrances:: Updating or unreleasing encumbrances, invoiceId={}...", invoiceId);
    return orderLineService.getPoLinesByIdAndQuery(poLineIds, this::queryToGetPoLinesWithRightPaymentStatusByIds, requestContext)
      .compose(poLines -> selectPoLinesWithOpenOrders(poLines, requestContext))
      .compose(poLines -> unreleaseEncumbrancesForPoLines(invoiceLines, poLines, invoiceFromStorage, requestContext))
      .recover(t -> {
        log.error("updateOrUnreleaseEncumbrances:: Failed to update or unrelease encumbrance for po lines, invoiceId={}", invoiceId, t);
        var causeParam = new Parameter().withKey("cause").withValue(requireNonNullElse(t.getCause(), t).toString());
        var invoiceIdParam = new Parameter().withKey("invoiceId").withValue(invoiceId);
        var error = ERROR_UNRELEASING_ENCUMBRANCES.toError().withParameters(List.of(causeParam, invoiceIdParam));
        throw new HttpException(500, error);
      });
  }

  private String queryToGetPoLinesWithRightPaymentStatusByIds(List<String> poLineIds) {
    return PO_LINES_WITH_RIGHT_PAYMENT_STATUS_QUERY + AND + convertIdsToCqlQuery(poLineIds);
  }

  private Future<List<PoLine>> selectPoLinesWithOpenOrders(List<PoLine> poLines, RequestContext requestContext) {
    if (poLines.isEmpty()) {
      return succeededFuture(emptyList());
    }
    List<String> orderIds = poLines.stream()
      .map(PoLine::getPurchaseOrderId)
      .distinct()
      .toList();
    return orderService.getOrders(queryToGetOpenOrdersByIds(orderIds), requestContext)
      .map(orders -> {
        List<String> openOrderIds = orders.stream().map(PurchaseOrder::getId).toList();
        return poLines.stream()
          .filter(poLine -> openOrderIds.contains(poLine.getPurchaseOrderId()))
          .toList();
      });
  }

  private String queryToGetOpenOrdersByIds(List<String> orderIds) {
    return OPEN_ORDERS_QUERY + AND + convertIdsToCqlQuery(orderIds);
  }

  private Future<Void> unreleaseEncumbrancesForPoLines(List<InvoiceLine> invoiceLines, List<PoLine> poLines,
                                                       Invoice invoiceFromStorage, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(poLines)) {
      return succeededFuture(null);
    }
    var poLineIds = invoiceLines.stream()
      .filter(InvoiceLine::getReleaseEncumbrance)
      .map(InvoiceLine::getPoLineId)
      .distinct()
      .toList();
    var fiscalYearId = invoiceFromStorage.getFiscalYearId();
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, fiscalYearId, requestContext)
      .map(encumbrances -> encumbrances.stream()
        .filter(encumbrance -> RELEASED.equals(encumbrance.getEncumbrance().getStatus()))
        // only unrelease encumbrances with expended + credited + awaiting payment = 0
        .filter(EncumbranceUtils::allowEncumbranceToUnrelease)
        .toList())
      .compose(encumbrances -> {
        if (CollectionUtils.isEmpty(encumbrances)) {
          return succeededFuture(null);
        }
        return baseTransactionService.batchUnrelease(encumbrances, requestContext);
      });
  }
}
