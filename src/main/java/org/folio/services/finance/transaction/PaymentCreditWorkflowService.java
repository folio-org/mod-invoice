package org.folio.services.finance.transaction;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.TRANSACTION_CREATION_FAILURE;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.services.FundsDistributionService.distributeFunds;

import java.util.List;

import javax.money.MonetaryAmount;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Parameter;

import io.vertx.core.Future;

public class PaymentCreditWorkflowService {

  private static final Logger logger = LogManager.getLogger(PaymentCreditWorkflowService.class);

  private final BaseTransactionService baseTransactionService;

  public PaymentCreditWorkflowService(BaseTransactionService baseTransactionService) {
    this.baseTransactionService = baseTransactionService;
  }

  /**
   * This method processes payment&credit transactions based on information from invoice (invoice lines & fund distributions)
   * @param dataHolders {@link List<InvoiceWorkflowDataHolder>} that should be processed
   * @return {@link Future <List<InvoiceWorkflowDataHolder>>}
   */
  public Future<List<InvoiceWorkflowDataHolder>> handlePaymentsAndCreditsCreation(
      List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPaymentsCredits(dataHolders);
    return holders.stream()
      .findFirst()
      .map(holder -> isPaymentsAlreadyProcessed(holder.getInvoice().getId(), requestContext)
        .compose(isProcessed -> {
          if (Boolean.TRUE.equals(isProcessed)) {
            return succeededFuture(holders);
          }

          distributeFunds(holders);
          return createTransactions(holders, requestContext).map(aVoid -> holders);
        }))
      .orElseGet(() -> succeededFuture(holders));
  }

  /**
   * This method check if the payments or credits have already been processed
   * @param invoiceId id of invoice for which payments and credits are verifying
   * @return {@link Future<Boolean>} with true if payments or credits have already processed otherwise - with false
   */
  private Future<Boolean> isPaymentsAlreadyProcessed(String invoiceId, RequestContext requestContext) {
    String query = String.format("sourceInvoiceId==%s and (transactionType==Payment or transactionType==Credit)", invoiceId);
    return baseTransactionService.getTransactions(query, 0, 0, requestContext)
      .map(transactionCollection -> transactionCollection.getTotalRecords() > 0);
  }

  private List<InvoiceWorkflowDataHolder> withNewPaymentsCredits(List<InvoiceWorkflowDataHolder> dataHolders) {
    return dataHolders.stream().map(holder -> holder.withNewTransaction(buildTransaction(holder))).collect(toList());
  }

  private Future<Void> createTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<Transaction> transactionsToCreate = holders.stream()
      .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .toList();
    if (transactionsToCreate.isEmpty()) {
      return succeededFuture();
    }
    return baseTransactionService.batchCreate(transactionsToCreate, requestContext)
      .recover(t -> {
        if (t instanceof HttpException he) {
          return Future.failedFuture(new HttpException(he.getCode(), he.getErrors()));
        }
        String invoiceId = transactionsToCreate.get(0).getSourceInvoiceId();
        logger.error("Failed to create transactions for invoice with id - {}", invoiceId, t);
        return Future.failedFuture(new HttpException(500, TRANSACTION_CREATION_FAILURE.toError().withParameters(
          List.of(new Parameter().withKey("invoiceId").withValue(invoiceId))
        )));
      });
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
