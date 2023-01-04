package org.folio.services.invoice;

import one.util.streamex.StreamEx;
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
import org.folio.services.voucher.VoucherCommandService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.CANCEL_TRANSACTIONS_ERROR;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.CREDIT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PENDING_PAYMENT;

public class InvoiceCancelService {
  private static final String PO_LINES_WITH_RIGHT_PAYMENT_STATUS_QUERY =
    "paymentStatus==(\"Awaiting Payment\" OR \"Partially Paid\" OR \"Fully Paid\")";
  private static final String OPEN_ORDERS_QUERY = "workflowStatus==\"Open\"";

  private static final Logger logger = LogManager.getLogger(InvoiceCancelService.class);

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  private final VoucherCommandService voucherCommandService;
  private final OrderLineService orderLineService;
  private final OrderService orderService;

  public InvoiceCancelService(BaseTransactionService baseTransactionService,
      EncumbranceService encumbranceService,
      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
      VoucherCommandService voucherCommandService,
      OrderLineService orderLineService,
      OrderService orderService) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
    this.voucherCommandService = voucherCommandService;
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
  public CompletableFuture<Void> cancelInvoice(Invoice invoiceFromStorage, List<InvoiceLine> lines,
      RequestContext requestContext) {
    validateCancelInvoice(invoiceFromStorage);
    String invoiceId = invoiceFromStorage.getId();
    return getTransactions(invoiceId, requestContext)
      .thenCompose(transactions -> cancelTransactions(invoiceId, transactions, requestContext))
      .thenAccept(v -> cancelInvoiceLines(lines))
      .thenCompose(v -> cancelVoucher(invoiceId, requestContext))
      .thenCompose(v -> unreleaseEncumbrances(lines, requestContext));
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

  private CompletableFuture<List<Transaction>> getTransactions(String invoiceId, RequestContext requestContext) {
    String query = String.format("sourceInvoiceId==%s", invoiceId);
    List<TransactionType> relevantTransactionTypes = List.of(PENDING_PAYMENT, PAYMENT, CREDIT);
    return baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext)
      .thenApply(TransactionCollection::getTransactions)
      .thenApply(transactions -> transactions.stream()
        .filter(tr -> relevantTransactionTypes.contains(tr.getTransactionType())).collect(toList()));
  }

  private CompletableFuture<Void> cancelTransactions(String invoiceId, List<Transaction> transactions,
      RequestContext requestContext) {
    if (transactions.size() == 0)
      return completedFuture(null);
    transactions.forEach(tr -> tr.setInvoiceCancelled(true));
    InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(invoiceId, transactions);
    return invoiceTransactionSummaryService.updateInvoiceTransactionSummary(summary, requestContext)
      .thenCompose(s -> baseTransactionService.updateTransactions(transactions, requestContext))
      .exceptionally(t -> {
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

  private CompletableFuture<Void> cancelVoucher(String invoiceId, RequestContext requestContext) {
    return voucherCommandService.cancelInvoiceVoucher(invoiceId, requestContext);
  }

  private CompletableFuture<Void> unreleaseEncumbrances(List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    List<String> poLineIds = invoiceLines.stream()
      .filter(InvoiceLine::getReleaseEncumbrance)
      .map(InvoiceLine::getPoLineId)
      .distinct()
      .collect(toList());
    if (poLineIds.isEmpty())
      return completedFuture(null);
    List<CompletableFuture<List<PoLine>>> futureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(this::queryToGetPoLinesWithRightPaymentStatusByIds)
      .map(query -> orderLineService.getPoLines(query, requestContext))
      .collect(toList());
    List<PoLine> poLinesList = new ArrayList<>();
    collectResultsOnSuccess(futureList)
      .thenAccept(col -> col.forEach(poLinesList::addAll));

    return selectPoLinesWithOpenOrders(poLinesList, requestContext)
      .thenCompose(poLines -> unreleaseEncumbrancesForPoLines(poLines, requestContext))
      .exceptionally(t -> {
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

  private CompletableFuture<List<PoLine>> selectPoLinesWithOpenOrders(List<PoLine> poLines, RequestContext requestContext) {
    if (poLines.isEmpty())
      return completedFuture(emptyList());
    List<String> orderIds = poLines.stream()
      .map(PoLine::getPurchaseOrderId)
      .distinct()
      .collect(toList());
    return orderService.getOrders(queryToGetOpenOrdersByIds(orderIds), requestContext)
      .thenApply(orders -> {
        List<String> openOrderIds = orders.stream().map(PurchaseOrder::getId).collect(toList());
        return poLines.stream()
          .filter(poLine -> openOrderIds.contains(poLine.getPurchaseOrderId()))
          .collect(toList());
      });
  }

  private String queryToGetOpenOrdersByIds(List<String> orderIds) {
    return OPEN_ORDERS_QUERY + " AND " + convertIdsToCqlQuery(orderIds);
  }

  private CompletableFuture<Void> unreleaseEncumbrancesForPoLines(List<PoLine> poLines, RequestContext requestContext) {
    if (poLines.isEmpty())
      return completedFuture(null);
    List<String> poLineIds = poLines.stream().map(PoLine::getId).collect(toList());
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, requestContext)
      .thenApply(transactions -> transactions.stream()
        .filter(tr -> RELEASED.equals(tr.getEncumbrance().getStatus()))
        .peek(tr -> tr.getEncumbrance().setStatus(UNRELEASED))
        .collect(toList()))
      .thenCompose(transactions -> {
        if (transactions.isEmpty())
          return completedFuture(null);
        return encumbranceService.unreleaseEncumbrances(transactions, requestContext);
      });
  }

}
