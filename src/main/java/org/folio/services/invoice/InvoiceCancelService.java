package org.folio.services.invoice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.InvoiceTransactionSummaryService;
import org.folio.services.voucher.VoucherCommandService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.folio.invoices.utils.ErrorCodes.*;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.*;

public class InvoiceCancelService {
  private static final Logger logger = LogManager.getLogger(InvoiceCancelService.class);
  private final BaseTransactionService baseTransactionService;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  private final VoucherCommandService voucherCommandService;

  public InvoiceCancelService(BaseTransactionService baseTransactionService,
      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
      VoucherCommandService voucherCommandService) {
    this.baseTransactionService = baseTransactionService;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
    this.voucherCommandService = voucherCommandService;
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
      .thenCompose(v -> cancelVoucher(invoiceId, requestContext));
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
        .filter(tr -> relevantTransactionTypes.contains(tr.getTransactionType())).collect(Collectors.toList()));
  }

  private CompletableFuture<Void> cancelTransactions(String invoiceId, List<Transaction> transactions,
      RequestContext requestContext) {
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
}
