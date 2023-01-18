package org.folio.services.invoice;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.CANCEL_TRANSACTIONS_ERROR;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.CREDIT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PENDING_PAYMENT;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PurchaseOrder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.InvoiceTransactionSummaryService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.voucher.VoucherService;

import io.vertx.core.Future;

public class InvoiceCancelService {
  private static final String PO_LINES_WITH_RIGHT_PAYMENT_STATUS_QUERY =
    "paymentStatus==(\"Awaiting Payment\" OR \"Partially Paid\" OR \"Fully Paid\")";
  private static final String OPEN_ORDERS_QUERY = "workflowStatus==\"Open\"";

  private static final Logger logger = LogManager.getLogger(InvoiceCancelService.class);

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  private final VoucherService voucherService;
  private final OrderLineService orderLineService;
  private final OrderService orderService;

  public InvoiceCancelService(BaseTransactionService baseTransactionService,
      EncumbranceService encumbranceService,
      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
      VoucherService voucherService,
      OrderLineService orderLineService,
      OrderService orderService) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
    this.voucherService = voucherService;
    this.orderLineService = orderLineService;
    this.orderService = orderService;
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

    return Future.succeededFuture()
      .map(v -> {
        validateCancelInvoice(invoiceFromStorage);
        return null;
      })
      .compose(v -> getTransactions(invoiceId, requestContext))
      .compose(transactions -> cancelTransactions(invoiceId, transactions, requestContext))
      .map(v -> {
        cancelInvoiceLines(lines);
        return null;
      })
      .compose(v -> cancelVoucher(invoiceId, requestContext))
      .compose(v -> unreleaseEncumbrances(lines, requestContext));
  }

  private void validateCancelInvoice(Invoice invoiceFromStorage) {
    List<Invoice.Status> cancellable = List.of(Invoice.Status.APPROVED, Invoice.Status.PAID);
    if (!cancellable.contains(invoiceFromStorage.getStatus())) {
      List<Parameter> parameters = Collections.singletonList(
        new Parameter().withKey("invoiceId").withValue(invoiceFromStorage.getId()));
      Error error = CANNOT_CANCEL_INVOICE.toError()
        .withParameters(parameters);
      throw new HttpException(422, error);
    }
  }

  private Future<List<Transaction>> getTransactions(String invoiceId, RequestContext requestContext) {
    String query = String.format("sourceInvoiceId==%s", invoiceId);
    List<TransactionType> relevantTransactionTypes = List.of(PENDING_PAYMENT, PAYMENT, CREDIT);
    return baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext)
      .map(TransactionCollection::getTransactions)
      .map(transactions -> transactions.stream()
        .filter(tr -> relevantTransactionTypes.contains(tr.getTransactionType())).collect(toList()));
  }

  private Future<Void> cancelTransactions(String invoiceId, List<Transaction> transactions,
      RequestContext requestContext) {
    if (transactions.size() == 0)
      return succeededFuture(null);
    transactions.forEach(tr -> tr.setInvoiceCancelled(true));
    InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(invoiceId, transactions);
    return invoiceTransactionSummaryService.updateInvoiceTransactionSummary(summary, requestContext)
      .compose(s -> baseTransactionService.updateTransactions(transactions, requestContext))
      .recover(t -> {
        logger.error("Failed to cancel transactions for invoice with id {}", invoiceId, t);
        List<Parameter> parameters = Collections.singletonList(
          new Parameter().withKey("invoiceId").withValue(invoiceId));
        throw new HttpException(500, CANCEL_TRANSACTIONS_ERROR.toError().withParameters(parameters));
      });
  }

  private InvoiceTransactionSummary buildInvoiceTransactionsSummary(String invoiceId, List<Transaction> transactions) {
    List<TransactionType> paymentOrCredit = List.of(PENDING_PAYMENT, PAYMENT, CREDIT);
    return new InvoiceTransactionSummary()
      .withId(invoiceId)
      .withNumPendingPayments((int)transactions.stream().filter(tr -> tr.getTransactionType() == PENDING_PAYMENT).count())
      .withNumPaymentsCredits((int)transactions.stream().filter(tr -> paymentOrCredit.contains(tr.getTransactionType())).count());
  }

  private void cancelInvoiceLines(List<InvoiceLine> lines) {
    lines.forEach(line -> line.setInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.CANCELLED));
  }

  private Future<Void> cancelVoucher(String invoiceId, RequestContext requestContext) {
    return voucherService.cancelInvoiceVoucher(invoiceId, requestContext);
  }

  private Future<Void> unreleaseEncumbrances(List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    List<String> poLineIds = invoiceLines.stream()
      .filter(InvoiceLine::getReleaseEncumbrance)
      .map(InvoiceLine::getPoLineId)
      .distinct()
      .collect(toList());
    if (poLineIds.isEmpty())
      return succeededFuture(null);
    return orderLineService.getPoLines(queryToGetPoLinesWithRightPaymentStatusByIds(poLineIds), requestContext)
      .compose(poLines -> selectPoLinesWithOpenOrders(poLines, requestContext))
      .compose(poLines -> unreleaseEncumbrancesForPoLines(poLines, requestContext))
      .recover(t -> {
        Throwable cause = requireNonNullElse(t.getCause(), t);
        List<Parameter> parameters = Collections.singletonList(
          new Parameter().withKey("cause").withValue(cause.toString()));
        Error error = ERROR_UNRELEASING_ENCUMBRANCES.toError().withParameters(parameters);
        logger.error(error.getMessage(), cause);
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
      .collect(toList());
    return orderService.getOrders(queryToGetOpenOrdersByIds(orderIds), requestContext)
      .map(orders -> {
        List<String> openOrderIds = orders.stream().map(PurchaseOrder::getId).collect(toList());
        return poLines.stream()
          .filter(poLine -> openOrderIds.contains(poLine.getPurchaseOrderId()))
          .collect(toList());
      });
  }

  private String queryToGetOpenOrdersByIds(List<String> orderIds) {
    return OPEN_ORDERS_QUERY + " AND " + convertIdsToCqlQuery(orderIds);
  }

  private Future<Void> unreleaseEncumbrancesForPoLines(List<PoLine> poLines, RequestContext requestContext) {
    if (poLines.isEmpty())
      return succeededFuture(null);
    List<String> poLineIds = poLines.stream().map(PoLine::getId).collect(toList());
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, requestContext)
      .map(transactions -> transactions.stream()
        .filter(tr -> RELEASED.equals(tr.getEncumbrance().getStatus()))
        .peek(tr -> tr.getEncumbrance().setStatus(UNRELEASED))
        .collect(toList()))
      .compose(transactions -> {
        if (transactions.isEmpty())
          return succeededFuture(null);
        return encumbranceService.unreleaseEncumbrances(transactions, requestContext);
      });
  }

}
