package org.folio.services.finance.transaction;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.TRANSACTION_CREATION_FAILURE;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.services.FundsDistributionService.distributeFunds;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.money.MonetaryAmount;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Parameter;

public class PaymentCreditWorkflowService {

  private static final Logger logger = LogManager.getLogger(PaymentCreditWorkflowService.class);

  public static final String FUND_ID = "fundId";

  private final BaseTransactionService baseTransactionService;

  public PaymentCreditWorkflowService(BaseTransactionService baseTransactionService) {
    this.baseTransactionService = baseTransactionService;
  }

  /**
   * This method processes payment&credit transactions based on information from invoice (invoice lines & fund distributions)
   * @param dataHolders {@link List<InvoiceWorkflowDataHolder>} that should be processed
   * @return {@link CompletableFuture <List<InvoiceWorkflowDataHolder>>}
   */
  public CompletableFuture<List<InvoiceWorkflowDataHolder>> handlePaymentsAndCreditsCreation(
      List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPaymentsCredits(dataHolders);
    return holders.stream()
      .findFirst()
      .map(holder -> isPaymentsAlreadyProcessed(holder.getInvoice().getId(), requestContext)
        .thenCompose(isProcessed -> {
          if (Boolean.TRUE.equals(isProcessed)) {
            return completedFuture(holders);
          }

          distributeFunds(holders);
          return createTransactions(holders, requestContext).thenApply(aVoid -> holders);
        }))
      .orElseGet(() -> completedFuture(holders));
  }

  /**
   * This method check if the payments or credits have already been processed
   * @param invoiceId id of invoice for which payments and credits are verifying
   * @return {@link CompletableFuture<Boolean>} with true if payments or credits have already processed otherwise - with false
   */
  private CompletableFuture<Boolean> isPaymentsAlreadyProcessed(String invoiceId, RequestContext requestContext) {
    String query = String.format("sourceInvoiceId==%s and (transactionType==Payment or transactionType==Credit)", invoiceId);
    return baseTransactionService.getTransactions(query, 0, 0, requestContext)
      .thenApply(transactionCollection -> transactionCollection.getTotalRecords() > 0);
  }

  private List<InvoiceWorkflowDataHolder> withNewPaymentsCredits(List<InvoiceWorkflowDataHolder> dataHolders) {
    return dataHolders.stream().map(holder -> holder.withNewTransaction(buildTransaction(holder))).collect(toList());
  }

  private CompletionStage<Void> createTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    CompletableFuture<Void> future = completedFuture(null);
    for (InvoiceWorkflowDataHolder holder : holders) {
      Transaction tr = holder.getNewTransaction();
      future = future.thenCompose(v -> baseTransactionService.createTransaction(tr, requestContext))
        .thenAccept(t -> {})
        .exceptionally(t -> {
          logger.error("Failed to create transaction for invoice with id - {}", tr.getSourceInvoiceId(), t);
          List<Parameter> parameters = new ArrayList<>();
          parameters.add(new Parameter().withKey("invoiceLineId").withValue(tr.getSourceInvoiceLineId()));
          parameters.add(new Parameter().withKey(FUND_ID)
            .withValue((tr.getTransactionType() == Transaction.TransactionType.PAYMENT) ? tr.getFromFundId() : tr.getToFundId()));
          throw new HttpException(400, TRANSACTION_CREATION_FAILURE.toError().withParameters(parameters));
        });
    }
    return future;
  }

  private Transaction buildTransaction(InvoiceWorkflowDataHolder holder) {
    MonetaryAmount amount = getFundDistributionAmount(holder.getFundDistribution(), holder.getTotal(), holder.getInvoiceCurrency()).with(holder.getConversion());
    Transaction transaction = buildBaseTransaction(holder);
    if (amount.isNegative()) {
      transaction.setToFundId(holder.getFundId());
      transaction.setFromFundId(null);
    }
    return transaction
            .withTransactionType(amount.isPositiveOrZero() ? Transaction.TransactionType.PAYMENT : Transaction.TransactionType.CREDIT)
            .withAmount(convertToDoubleWithRounding(amount.abs()))
            .withPaymentEncumbranceId(holder.getFundDistribution().getEncumbrance())
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
