package org.folio.services.transaction;

import static org.folio.invoices.utils.ErrorCodes.PENDING_PAYMENT_ERROR;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRateProvider;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.models.PendingPaymentHolder;
import org.folio.models.TransactionDataHolder;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.finance.BudgetValidationService;
import org.folio.services.finance.CurrentFiscalYearService;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class PendingPaymentWorkflowService {

  private static final Logger logger = LoggerFactory.getLogger(PendingPaymentWorkflowService.class);

  private final BaseTransactionService baseTransactionService;
  private final CurrentFiscalYearService currentFiscalYearService;
  private final ExchangeRateProviderResolver exchangeRateProviderResolver;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  private final BudgetValidationService budgetValidationService;

  public PendingPaymentWorkflowService(BaseTransactionService baseTransactionService,
                                       CurrentFiscalYearService currentFiscalYearService,
                                       ExchangeRateProviderResolver exchangeRateProviderResolver,
                                       InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                       BudgetValidationService budgetValidationService) {
    this.baseTransactionService = baseTransactionService;
    this.currentFiscalYearService = currentFiscalYearService;
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
    this.budgetValidationService = budgetValidationService;
  }

  public CompletableFuture<Void> handlePendingPaymentsCreation(List<InvoiceLine> invoiceLines, Invoice invoice, RequestContext requestContext) {

    return buildPendingPaymentTransactions(invoiceLines, invoice, requestContext)
      .thenCompose(pendingPayments -> {
        InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(invoice, pendingPayments.size());
        return invoiceTransactionSummaryService.createInvoiceTransactionSummary(summary, requestContext)
          .thenCompose(s -> createPendingPayments(pendingPayments, requestContext));
      });
  }

  public CompletableFuture<Void> handlePendingPaymentsUpdate(Invoice invoice, List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    return retrievePendingPayments(invoice, requestContext)
      .thenCompose(existingTransactions -> buildPendingPaymentTransactions(invoiceLines, invoice ,requestContext)
        .thenCompose(newTransactions -> budgetValidationService.checkEnoughMoneyInBudget(newTransactions, existingTransactions, requestContext)
          .thenApply(aVoid -> newTransactions))
        .thenApply(newTransactions -> mapNewTransactionToExistingIds(newTransactions, existingTransactions)))
      .thenCompose(transactions -> {
        InvoiceTransactionSummary invoiceTransactionSummary = buildInvoiceTransactionsSummary(invoice, transactions.size());
        return invoiceTransactionSummaryService.updateInvoiceTransactionSummary(invoiceTransactionSummary, requestContext)
          .thenCompose(aVoid -> updateTransactions(transactions, requestContext));
      });
  }

  private InvoiceTransactionSummary buildInvoiceTransactionsSummary(Invoice invoice, int numPendingPayments) {
    return new InvoiceTransactionSummary()
      .withId(invoice.getId())
      .withNumPendingPayments(numPendingPayments)
      .withNumPaymentsCredits(numPendingPayments);
  }

  private CompletableFuture<Void> createPendingPayments(List<Transaction> pendingPayments, RequestContext requestContext) {
    return VertxCompletableFuture.allOf(requestContext.getContext(), pendingPayments.stream()
      .map(pendingPayment -> baseTransactionService.createTransaction(pendingPayment, requestContext)
        .exceptionally(t -> {
          logger.error("Failed to create pending payment with id {}", t, pendingPayment.getId());
          throw new HttpException(400, PENDING_PAYMENT_ERROR.toError());
        }))
      .toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<List<Transaction>> buildPendingPaymentTransactions(List<InvoiceLine> invoiceLines, Invoice invoice, RequestContext requestContext) {
    TransactionDataHolder holder = new PendingPaymentHolder(invoice, invoiceLines);
    return withFiscalYear(holder, requestContext)
      .thenApply(transactionDataHolder -> withCurrencyConversion(transactionDataHolder, requestContext))
      .thenApply(TransactionDataHolder::toTransactions);
  }

  private TransactionDataHolder withCurrencyConversion(TransactionDataHolder transactionDataHolder, RequestContext requestContext) {
    Invoice invoice = transactionDataHolder.getInvoice();
    String fiscalYearCurrency = transactionDataHolder.getCurrency();

      ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, fiscalYearCurrency);
      ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, requestContext);
      invoice.setExchangeRate(exchangeRateProvider.getExchangeRate(conversionQuery).getFactor().doubleValue());
      return transactionDataHolder.withCurrencyConversion(exchangeRateProvider.getCurrencyConversion(conversionQuery));
  }

  private CompletableFuture<TransactionDataHolder> withFiscalYear(TransactionDataHolder holder, RequestContext requestContext) {
      String fundId = holder.getFundIds().get(0);
      return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContext)
        .thenApply(holder::withFiscalYear);
  }

  private CompletableFuture<Void> updateTransactions(List<Transaction> transactions, RequestContext requestContext) {
    return VertxCompletableFuture.allOf(requestContext.getContext(), transactions.stream()
    .map(transaction -> baseTransactionService.updateTransaction(transaction, requestContext))
    .toArray(CompletableFuture[]::new));
  }

  private List<Transaction> mapNewTransactionToExistingIds(List<Transaction> newTransactions, List<Transaction> existingTransactions) {
    existingTransactions.forEach(existingTransaction -> newTransactions.stream()
      .filter(newTransaction -> isSamePendingPayments(existingTransaction, newTransaction))
      .forEach(newTransaction -> existingTransaction.setAmount(newTransaction.getAmount())));
    return existingTransactions;
  }

  private boolean isSamePendingPayments(Transaction transaction1, Transaction transaction2) {
    return Objects.equals(transaction1.getFromFundId(), transaction2.getFromFundId())
      && Objects.equals(transaction1.getSourceInvoiceId(), transaction2.getSourceInvoiceId())
      && Objects.equals(transaction1.getSourceInvoiceLineId(), transaction2.getSourceInvoiceLineId())
      && Objects.equals(transaction1.getFiscalYearId(), transaction2.getFiscalYearId());
  }

  private CompletableFuture<List<Transaction>> retrievePendingPayments(Invoice invoice, RequestContext requestContext) {
    String query = String.format("sourceInvoiceId==%s AND transactionType==Pending payment", invoice.getId());
    return baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE,requestContext)
      .thenApply(TransactionCollection::getTransactions);
  }
}
