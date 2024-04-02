package org.folio.services.invoice;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.invoices.utils.ErrorCodes.CANCEL_TRANSACTIONS_ERROR;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.CREDIT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.AWAITING_PAYMENT;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.PARTIALLY_PAID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import one.util.streamex.StreamEx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PurchaseOrder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.voucher.VoucherService;

public class InvoiceCancelService {
  private static final String PO_LINES_WITH_RIGHT_PAYMENT_STATUS_QUERY =
    "paymentStatus==(\"Awaiting Payment\" OR \"Partially Paid\" OR \"Fully Paid\" OR \"Ongoing\")";
  private static final String OPEN_ORDERS_QUERY = "workflowStatus==\"Open\"";

  private static final Logger logger = LogManager.getLogger();

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final VoucherService voucherService;
  private final OrderLineService orderLineService;
  private final OrderService orderService;
  private final InvoiceLineService invoiceLineService;
  private final BaseInvoiceService invoiceService;
  private final InvoiceWorkflowDataHolderBuilder holderBuilder;

  public InvoiceCancelService(BaseTransactionService baseTransactionService,
      EncumbranceService encumbranceService,
      VoucherService voucherService,
      OrderLineService orderLineService,
      OrderService orderService,
      InvoiceLineService invoiceLineService,
      BaseInvoiceService baseInvoiceService,
      InvoiceWorkflowDataHolderBuilder holderBuilder) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.voucherService = voucherService;
    this.orderLineService = orderLineService;
    this.orderService = orderService;
    this.invoiceLineService = invoiceLineService;
    this.invoiceService = baseInvoiceService;
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
   * @param lines lines from the new invoice
   * @return CompletableFuture that indicates when the transition is completed
   */
  public Future<Void> cancelInvoice(Invoice invoiceFromStorage, List<InvoiceLine> lines, RequestContext requestContext) {
    String invoiceId = invoiceFromStorage.getId();
    logger.info("Cancelling invoice {}...", invoiceId);

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
      .compose(v -> unreleaseEncumbrances(lines, invoiceFromStorage, requestContext))
      .compose(v -> updatePoLinePaymentStatus(invoiceFromStorage, lines, requestContext))
      .onSuccess(v -> logger.info("Invoice {} cancelled successfully", invoiceId))
      .onFailure(t -> logger.error("Failed to cancel invoice {}", invoiceId, t));
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
   * @param invoice The invoice.This parameter is necessary to extract the associated budgets.
   * @param lines The list of invoice lines. This parameter is necessary to extract the associated budgets.
   * @param requestContext The request context providing additional information.
   * @return A `Future` of type `Void`, representing the result of the validation. If the future succeeds,
   * it indicates that the validation has been successfully completed, and active budgets have been extracted
   * @throws HttpException If no active budgets are found, an exception is thrown.
   */
  private Future<Void> validateBudgetsStatus(Invoice invoice, List<InvoiceLine> lines, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withBudgets(dataHolders, requestContext)
      .onFailure(t -> logger.error("Could not find an active budget for the invoice with id {}", invoice.getId(), t))
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
    logger.info("Cancelling invoice transactions...");
    return baseTransactionService.batchCancel(transactions, requestContext)
      .recover(t -> {
        logger.error("Failed to cancel transactions for invoice with id {}", invoiceId, t);
        var param = new Parameter().withKey(INVOICE_ID).withValue(invoiceId);
        var causeParam = new Parameter().withKey("cause").withValue(t.getMessage());
        throw new HttpException(500, CANCEL_TRANSACTIONS_ERROR, List.of(param, causeParam));
      });
  }

  private void cancelInvoiceLines(List<InvoiceLine> lines) {
    lines.forEach(line -> line.setInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.CANCELLED));
  }

  private Future<Void> cancelVoucher(String invoiceId, RequestContext requestContext) {
    logger.info("Cancelling voucher...");
    return voucherService.cancelInvoiceVoucher(invoiceId, requestContext);
  }

  private Future<Void> unreleaseEncumbrances(List<InvoiceLine> invoiceLines, Invoice invoiceFromStorage,
      RequestContext requestContext) {
    List<String> poLineIds = invoiceLines.stream()
      .filter(InvoiceLine::getReleaseEncumbrance)
      .map(InvoiceLine::getPoLineId)
      .distinct()
      .toList();
    if (poLineIds.isEmpty()) {
      return succeededFuture();
    }
    logger.info("Unreleasing encumbrances...");
    return getPoLinesByIdAndQuery(poLineIds, this::queryToGetPoLinesWithRightPaymentStatusByIds, requestContext)
      .compose(poLines -> selectPoLinesWithOpenOrders(poLines, requestContext))
      .compose(poLines -> unreleaseEncumbrancesForPoLines(poLines, invoiceFromStorage, requestContext))
      .recover(t -> {
        logger.error("Failed to unrelease encumbrance for po lines", t);
        var param = new Parameter().withKey("cause").withValue(requireNonNullElse(t.getCause(), t).toString());
        var error = ERROR_UNRELEASING_ENCUMBRANCES.toError().withParameters(List.of(param));
        throw new HttpException(500, error);
      });
  }

  private String queryToGetPoLinesWithRightPaymentStatusByIds(List<String> poLineIds) {
    return PO_LINES_WITH_RIGHT_PAYMENT_STATUS_QUERY + " AND " + convertIdsToCqlQuery(poLineIds);
  }

  private Future<List<PoLine>> selectPoLinesWithOpenOrders(List<PoLine> poLines, RequestContext requestContext) {
    if (poLines.isEmpty())
      return succeededFuture(emptyList());
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
    return OPEN_ORDERS_QUERY + " AND " + convertIdsToCqlQuery(orderIds);
  }

  private Future<Void> unreleaseEncumbrancesForPoLines(List<PoLine> poLines, Invoice invoiceFromStorage,
      RequestContext requestContext) {
    if (poLines.isEmpty())
      return succeededFuture(null);
    List<String> poLineIds = poLines.stream().map(PoLine::getId).toList();
    String fiscalYearId = invoiceFromStorage.getFiscalYearId();
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, fiscalYearId, requestContext)
      .map(transactions -> transactions.stream()
        .filter(tr -> RELEASED.equals(tr.getEncumbrance().getStatus()))
        .toList())
      .compose(transactions -> {
        if (transactions.isEmpty())
          return succeededFuture(null);
        return baseTransactionService.batchUnrelease(transactions, requestContext);
      });
  }

  private Future<Void> updatePoLinePaymentStatus(Invoice invoiceFromStorage, List<InvoiceLine> invoiceLines,
      RequestContext requestContext) {
    if (!Invoice.Status.PAID.equals(invoiceFromStorage.getStatus()) || invoiceFromStorage.getFiscalYearId() == null) {
      // in the unlikely case the fiscal year is undefined, the payment status update is skipped
      // (the MODINVOSTO-177 script should fill it up for pre-Poppy invoices)
      logger.info("The invoice was not paid or the fiscal year is unknown; skipping po line paymentStatus update.");
      return succeededFuture();
    }
    List<String> allPoLineIds = invoiceLines.stream()
      .map(InvoiceLine::getPoLineId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (allPoLineIds.isEmpty()) {
      return succeededFuture();
    }
    logger.info("Retrieving linked po lines that might need a paymentStatus update...");
    return getPoLinesByIdAndQuery(allPoLineIds, this::queryToGetPoLinesWithFullyOrPartiallyPaidPaymentStatusByIds, requestContext)
      .compose(filteredPoLines -> {
        if (filteredPoLines.isEmpty()) {
          logger.info("No matching po line, no paymentStatus update needed.");
          return succeededFuture();
        }
        logger.info("There are po lines linked to the invoice that might need a paymentStatus update;" +
          " retrieving paid invoice lines linked to the po lines...");
        List<String> filteredPoLineIds = filteredPoLines.stream().map(PoLine::getId).toList();
        return getInvoiceLinesByPoLineIdsAndQuery(filteredPoLineIds,
            ids -> queryToGetRelatedPaidInvoiceLinesByPoLineIds(invoiceFromStorage.getId(), ids), requestContext)
          .compose(relatedInvoiceLines -> {
            if (relatedInvoiceLines.isEmpty()) {
              logger.info("No linked paid invoice line; setting paymentStatus to Awaiting Payment...");
              return updateRelatedPoLines(relatedInvoiceLines, filteredPoLines, emptyList(), requestContext);
            }
            logger.info("There are linked paid invoice lines; retrieving the related invoices to select only invoices" +
              " with the same fiscal year as the cancelled invoice...");
            List<String> relatedInvoiceIds = relatedInvoiceLines.stream().map(InvoiceLine::getInvoiceId).distinct().toList();
            String invoiceQuery = "status==Paid AND fiscalYearId==" + invoiceFromStorage.getFiscalYearId();
            return getInvoicesByIdsAndQuery(relatedInvoiceIds, invoiceQuery, requestContext)
              .compose(relatedInvoices -> updateRelatedPoLines(relatedInvoiceLines, filteredPoLines, relatedInvoices, requestContext));
          });
      });
  }

  private String queryToGetPoLinesWithFullyOrPartiallyPaidPaymentStatusByIds(List<String> poLineIds) {
    return "paymentStatus==(\"Fully Paid\" OR \"Partially Paid\") AND " + convertIdsToCqlQuery(poLineIds);
  }

  private String queryToGetRelatedPaidInvoiceLinesByPoLineIds(String invoiceId, List<String> poLineIds) {
    return "invoiceId<>" + invoiceId + " AND invoiceLineStatus==(\"Paid\") AND " +
      convertIdsToCqlQuery(poLineIds, "poLineId", true);
  }

  private Future<List<PoLine>> getPoLinesByIdAndQuery(List<String> poLineIds, Function<List<String>, String> queryFunction,
      RequestContext requestContext) {
    List<Future<List<PoLine>>> futureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(queryFunction)
      .map(query -> orderLineService.getPoLines(query, requestContext))
      .toList();

    return collectResultsOnSuccess(futureList)
      .map(col -> col.stream().flatMap(List::stream).toList());
  }

  private Future<List<InvoiceLine>> getInvoiceLinesByPoLineIdsAndQuery(List<String> poLineIds,
      Function<List<String>, String> queryFunction, RequestContext requestContext) {
    List<Future<List<InvoiceLine>>> futureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(queryFunction)
      .map(query -> invoiceLineService.getInvoiceLinesByQuery(query, requestContext))
      .toList();

    return collectResultsOnSuccess(futureList)
      .map(col -> col.stream().flatMap(List::stream).toList());
  }

  private Future<List<Invoice>> getInvoicesByIdsAndQuery(List<String> invoiceIds, String query, RequestContext requestContext) {
    String query2 = "(" + query + ") AND " + convertIdsToCqlQuery(invoiceIds);
    return invoiceService.getInvoices(query2, 0, Integer.MAX_VALUE, requestContext)
      .map(InvoiceCollection::getInvoices);
  }

  private Future<Void> updateRelatedPoLines(List<InvoiceLine> allRelatedInvoiceLines, List<PoLine> filteredPoLines,
      List<Invoice> relatedInvoices, RequestContext requestContext) {
    List<String> relatedInvoiceIds = relatedInvoices.stream().map(Invoice::getId).toList();
    List<InvoiceLine> filteredInvoiceLines = allRelatedInvoiceLines.stream()
      .filter(invoiceLine -> relatedInvoiceIds.contains(invoiceLine.getInvoiceId()))
      .toList();
    Map<String, List<InvoiceLine>> poLineIdToInvoiceLines = filteredInvoiceLines.stream()
      .collect(groupingBy(InvoiceLine::getPoLineId));
    List<PoLine> modifiedPoLines = new ArrayList<>();
    filteredPoLines.forEach(poLine -> {
      List<InvoiceLine> relatedInvoiceLines = poLineIdToInvoiceLines.get(poLine.getId());
      if (relatedInvoiceLines == null || relatedInvoiceLines.isEmpty()) {
        poLine.setPaymentStatus(AWAITING_PAYMENT);
        modifiedPoLines.add(poLine);
      } else if (relatedInvoiceLines.stream().noneMatch(InvoiceLine::getReleaseEncumbrance) &&
          !PARTIALLY_PAID.equals(poLine.getPaymentStatus())) {
        poLine.setPaymentStatus(PARTIALLY_PAID);
        modifiedPoLines.add(poLine);
      }
    });
    if (modifiedPoLines.isEmpty()) {
      logger.info("No po line paymentStatus update needed.");
      return succeededFuture();
    }
    logger.info("{} po lines need a paymentStatus update. Updating...", modifiedPoLines.size());
    List<CompositePoLine> compositePoLines = modifiedPoLines.stream()
      .map(poLine -> JsonObject.mapFrom(poLine).mapTo(CompositePoLine.class))
      .toList();
    return orderLineService.updateCompositePoLines(compositePoLines, requestContext);
  }
}
