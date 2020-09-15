package org.folio.services.transaction;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.invoices.utils.ErrorCodes.TRANSACTION_CREATION_FAILURE;
import static org.folio.services.finance.BudgetExpenseClassService.FUND_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.models.PaymentCreditHolder;
import org.folio.models.TransactionDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.FinanceExchangeRateService;
import org.folio.services.finance.CurrentFiscalYearService;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class PaymentCreditWorkflowService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentCreditWorkflowService.class);

  private final BaseTransactionService baseTransactionService;
  private final CurrentFiscalYearService currentFiscalYearService;
  private final ExchangeRateProviderResolver exchangeRateProviderResolver;
  private final FinanceExchangeRateService financeExchangeRateService;

  public PaymentCreditWorkflowService(BaseTransactionService baseTransactionService,
                                      CurrentFiscalYearService currentFiscalYearService,
                                      ExchangeRateProviderResolver exchangeRateProviderResolver,
                                      FinanceExchangeRateService financeExchangeRateService) {
    this.baseTransactionService = baseTransactionService;
    this.currentFiscalYearService = currentFiscalYearService;
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
    this.financeExchangeRateService = financeExchangeRateService;
  }

  /**
   * This method processes payment&credit transactions based on information from invoice (invoice lines & fund distributions)
   * @param invoice {@link Invoice} that should be processed
   * @return {@link CompletableFuture <Void>}
   */
  public CompletableFuture<Void> handlePaymentsAndCreditsCreation(Invoice invoice, List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    return isPaymentsAlreadyProcessed(invoice.getId(), requestContext).thenCompose(isProcessed -> {
      if (Boolean.TRUE.equals(isProcessed)) {
        return completedFuture(null);
      }

      return  buildTransactions(invoiceLines, invoice, requestContext)
        .thenCompose(transactions -> createTransactions(transactions, requestContext));

    });
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

  private CompletableFuture<List<Transaction>> buildTransactions(List<InvoiceLine> invoiceLines, Invoice invoice, RequestContext requestContext) {
    PaymentCreditHolder holder = new PaymentCreditHolder(invoice, invoiceLines);
    return withFiscalYear(holder, requestContext)
      .thenCompose(transactionDataHolder -> withCurrencyConversion(transactionDataHolder, requestContext))
      .thenApply(TransactionDataHolder::toTransactions);
  }

  private CompletableFuture<TransactionDataHolder> withFiscalYear(TransactionDataHolder holder, RequestContext requestContext) {
    String fundId = holder.getFundIds().get(0);
    return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContext)
      .thenApply(holder::withFiscalYear);
  }

  private CompletionStage<TransactionDataHolder> withCurrencyConversion(TransactionDataHolder transactionDataHolder, RequestContext requestContext) {
    Invoice invoice = transactionDataHolder.getInvoice();
    String fiscalYearCurrency = transactionDataHolder.getCurrency();
    return financeExchangeRateService.getExchangeRate(invoice, fiscalYearCurrency, requestContext)
      .thenApply(HelperUtils::buildConversionQuery)
      .thenApply(conversionQuery -> exchangeRateProviderResolver.resolve(conversionQuery).getCurrencyConversion(conversionQuery))
      .thenApply(transactionDataHolder::withCurrencyConversion);
  }

  private CompletionStage<Void> createTransactions(List<Transaction> transactions, RequestContext requestContext) {
    return VertxCompletableFuture.allOf(requestContext.getContext(), transactions.stream()
      .map(tr -> baseTransactionService.createTransaction(tr, requestContext)
      .exceptionally(t -> {
        logger.error("Failed to create transaction for invoice with id - {}", t, tr.getSourceInvoiceId());
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter().withKey("invoiceLineId").withValue(tr.getSourceInvoiceLineId()));
        parameters.add(new Parameter().withKey(FUND_ID)
          .withValue((tr.getTransactionType() == Transaction.TransactionType.PAYMENT) ? tr.getFromFundId() : tr.getToFundId()));
        throw new HttpException(400, TRANSACTION_CREATION_FAILURE.toError().withParameters(parameters));
      }))
      .toArray(CompletableFuture[]::new));
  }

}
