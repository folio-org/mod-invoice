package org.folio.services.transaction;

import static org.folio.invoices.utils.ErrorCodes.PENDING_PAYMENT_ERROR;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.models.PendingPaymentHolder;
import org.folio.models.TransactionDataHolder;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.FinanceExchangeRateService;
import org.folio.services.finance.CurrentFiscalYearService;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class PendingPaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PendingPaymentService.class);

  private final BaseTransactionService baseTransactionService;
  private final CurrentFiscalYearService currentFiscalYearService;
  private final ExchangeRateProviderResolver exchangeRateProviderResolver;
  private final FinanceExchangeRateService financeExchangeRateService;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;

  public PendingPaymentService(BaseTransactionService baseTransactionService,
                               CurrentFiscalYearService currentFiscalYearService,
                               ExchangeRateProviderResolver exchangeRateProviderResolver,
                               FinanceExchangeRateService financeExchangeRateService,
                               InvoiceTransactionSummaryService invoiceTransactionSummaryService) {
    this.baseTransactionService = baseTransactionService;
    this.currentFiscalYearService = currentFiscalYearService;
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
    this.financeExchangeRateService = financeExchangeRateService;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
  }

  public CompletableFuture<Void> handlePendingPayments(List<InvoiceLine> invoiceLines, Invoice invoice, RequestContext requestContext) {

    return buildPendingPaymentTransactions(invoiceLines, invoice, requestContext)
      .thenCompose(pendingPayments -> {
        InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(invoice, pendingPayments.size());
        return invoiceTransactionSummaryService.createInvoiceTransactionSummary(summary, requestContext)
          .thenCompose(s -> createPendingPayments(pendingPayments, requestContext));
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

  public CompletableFuture<List<Transaction>> buildPendingPaymentTransactions(List<InvoiceLine> invoiceLines, Invoice invoice, RequestContext requestContext) {
    TransactionDataHolder holder = new PendingPaymentHolder(invoice, invoiceLines);
    return withFiscalYear(holder, requestContext)
      .thenCompose(transactionDataHolder -> withCurrencyConversion(transactionDataHolder, requestContext))
      .thenApply(TransactionDataHolder::toTransactions);
  }

  private CompletionStage<TransactionDataHolder> withCurrencyConversion(TransactionDataHolder transactionDataHolder, RequestContext requestContext) {
    Invoice invoice = transactionDataHolder.getInvoice();
    String fiscalYearCurrency = transactionDataHolder.getCurrency();
    return getExchangeRate(invoice, fiscalYearCurrency, requestContext)
      .thenApply(HelperUtils::buildConversionQuery)
      .thenApply(conversionQuery -> exchangeRateProviderResolver.resolve(conversionQuery).getCurrencyConversion(conversionQuery))
      .thenApply(transactionDataHolder::withCurrencyConversion);
  }

  private CompletableFuture<ExchangeRate> getExchangeRate(Invoice invoice, String fiscalYearCurrency, RequestContext requestContext) {
    CurrencyUnit invoiceCurrency = Monetary.getCurrency(invoice.getCurrency());
    CurrencyUnit systemCurrency = Monetary.getCurrency(fiscalYearCurrency);
    if (invoiceCurrency.equals(systemCurrency)) {
      invoice.setExchangeRate(1d);
      return CompletableFuture.completedFuture(new ExchangeRate().withExchangeRate(1d)
        .withFrom(fiscalYearCurrency)
        .withTo(fiscalYearCurrency));
    }
    if (invoice.getExchangeRate() == null || invoice.getExchangeRate() == 0d) {
      return financeExchangeRateService.getExchangeRate(invoice.getCurrency(), fiscalYearCurrency, requestContext)
        .thenApply(exchangeRate -> {
          invoice.setExchangeRate(exchangeRate.getExchangeRate());
          return exchangeRate;
        });
    }

    return CompletableFuture.completedFuture(new ExchangeRate().withExchangeRate(invoice.getExchangeRate())
      .withFrom(invoice.getCurrency())
      .withTo(fiscalYearCurrency));
  }

  private CompletableFuture<TransactionDataHolder> withFiscalYear(TransactionDataHolder holder, RequestContext requestContext) {
      String fundId = holder.getFundIds().get(0);
      return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContext)
        .thenApply(holder::withFiscalYear);
  }

}
