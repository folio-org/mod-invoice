package org.folio.services.finance.transaction;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.PENDING_PAYMENT_ERROR;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.services.FundsDistributionService.distributeFunds;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.money.MonetaryAmount;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.validator.HolderValidator;

public class PendingPaymentWorkflowService {

  private static final Logger logger = LogManager.getLogger(PendingPaymentWorkflowService.class);

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  private final HolderValidator holderValidator;


  public PendingPaymentWorkflowService(BaseTransactionService baseTransactionService,
                                       EncumbranceService encumbranceService,
                                       InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                       HolderValidator validator) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
    this.holderValidator = validator;

  }

  public CompletableFuture<Void> handlePendingPaymentsCreation(List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPendingPayments(dataHolders);
    holderValidator.validate(holders);
    InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(holders);
    return invoiceTransactionSummaryService.createInvoiceTransactionSummary(summary, requestContext)
      .thenCompose(s -> createPendingPayments(holders, requestContext))
      .thenCompose(s -> cleanupOldEncumbrances(holders, requestContext));
  }

  public CompletableFuture<Void> handlePendingPaymentsUpdate(List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPendingPayments(dataHolders);
    holderValidator.validate(holders);
    InvoiceTransactionSummary invoiceTransactionSummary = buildInvoiceTransactionsSummary(holders);
    return invoiceTransactionSummaryService.updateInvoiceTransactionSummary(invoiceTransactionSummary, requestContext)
      .thenCompose(aVoid -> updateTransactions(holders, requestContext));
  }

  private InvoiceTransactionSummary buildInvoiceTransactionsSummary(List<InvoiceWorkflowDataHolder> holders) {
    return holders.stream().findFirst().map(InvoiceWorkflowDataHolder::getInvoice).map(Invoice::getId)
            .map(invoiceId -> new InvoiceTransactionSummary()
                    .withId(invoiceId)
                    .withNumPendingPayments(holders.size())
                    .withNumPaymentsCredits(holders.size()))
            .orElse(null);
  }

  private CompletableFuture<Void> createPendingPayments(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    return FolioVertxCompletableFuture.allOf(requestContext.getContext(), holders.stream()
      .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .map(pendingPayment -> baseTransactionService.createTransaction(pendingPayment, requestContext)
        .exceptionally(t -> {
          logger.error("Failed to create pending payment with id {}", pendingPayment.getId(), t);
          throw new HttpException(400, PENDING_PAYMENT_ERROR.toError());
        }))
      .toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<Void> cleanupOldEncumbrances(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<String> poLineIds = holders.stream().filter(holder -> holder.getInvoiceLine() != null).map(holder -> holder.getInvoiceLine().getPoLineId()).collect(toList());
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, requestContext)
      .thenCompose(transactions -> cleanupOldEncumbrances(transactions, holders, requestContext));
  }

  private CompletableFuture<Void> cleanupOldEncumbrances(List<Transaction> poLineTransactions, List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (Transaction transaction : poLineTransactions) {
      boolean encumbranceIsNoLongerRelevant = holders.stream().noneMatch(holder -> sameFundAndPoLine(transaction, holder));
      if (encumbranceIsNoLongerRelevant) {
        futures.add(baseTransactionService.releaseEncumbrance(transaction, requestContext));
      }
    }

    return collectResultsOnSuccess(futures)
      .thenAccept(result -> logger.debug(
          "Number of encumbrances released due to invoice lines fund distributions being different from PO lines fund distributions: {}", result.size()));
  }

  private boolean sameFundAndPoLine(Transaction transaction, InvoiceWorkflowDataHolder holder) {
    return transaction.getFromFundId().equals(holder.getFundId()) && transaction.getEncumbrance().getSourcePoLineId().equals(holder.getInvoiceLine().getPoLineId());
  }

  private List<InvoiceWorkflowDataHolder> withNewPendingPayments(List<InvoiceWorkflowDataHolder> dataHolders) {
    List<InvoiceWorkflowDataHolder> invoiceWorkflowDataHolders = dataHolders.stream()
      .map(holder -> holder.withNewTransaction(buildTransaction(holder)))
      .collect(toList());
    return distributeFunds(invoiceWorkflowDataHolders);
  }

  private CompletableFuture<Void> updateTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    return FolioVertxCompletableFuture.allOf(requestContext.getContext(), holders.stream()
    .map(InvoiceWorkflowDataHolder::getNewTransaction)
    .map(transaction -> baseTransactionService.updateTransaction(transaction, requestContext))
    .toArray(CompletableFuture[]::new));
  }

  private Transaction buildTransaction(InvoiceWorkflowDataHolder holder)  {
    MonetaryAmount amount = getFundDistributionAmount(holder.getFundDistribution(), holder.getTotal(), holder.getInvoiceCurrency()).with(holder.getConversion());
    AwaitingPayment awaitingPayment = null;
    FundDistribution fundDistribution = holder.getFundDistribution();

    if (fundDistribution.getEncumbrance() != null) {
      boolean releaseEncumbrance = Optional.ofNullable(holder.getInvoiceLine()).map(InvoiceLine::getReleaseEncumbrance).orElse(false);
      awaitingPayment = new AwaitingPayment()
              .withEncumbranceId(fundDistribution.getEncumbrance())
              .withReleaseEncumbrance(releaseEncumbrance);
    }
    String transactionId = Optional.ofNullable(holder.getExistingTransaction()).map(Transaction::getId).orElse(null);
    return buildBaseTransaction(holder)
            .withId(transactionId)
            .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
            .withAwaitingPayment(awaitingPayment)
            .withAmount(convertToDoubleWithRounding(amount))
            .withSourceInvoiceLineId(holder.getInvoiceLineId());

  }

  protected Transaction buildBaseTransaction(InvoiceWorkflowDataHolder holder) {

    return new Transaction()
            .withSource(Transaction.Source.INVOICE)
            .withCurrency(holder.getFyCurrency())
            .withFiscalYearId(holder.getFiscalYear().getId())
            .withSourceInvoiceId(holder.getInvoice().getId())
            .withFromFundId(holder.getFundId())
            .withExpenseClassId(holder.getExpenseClassId());
  }

}
