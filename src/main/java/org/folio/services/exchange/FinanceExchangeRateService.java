package org.folio.services.exchange;

import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_EXCHANGE_RATE;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.concurrent.CompletableFuture;


public class FinanceExchangeRateService {
  private static final String ROE_QUERY_PARANS = ResourcePathResolver.resourcesPath(FINANCE_EXCHANGE_RATE) + "?from=%s&to=%s";
  private final RestClient exchangeRateRestClient;

  public FinanceExchangeRateService(RestClient exchangeRateRestClient) {
    this.exchangeRateRestClient = exchangeRateRestClient;
  }

  public CompletableFuture<ExchangeRate> getExchangeRate(String from, String to, RequestContext requestContext) {
    String roeQuery = String.format(ROE_QUERY_PARANS, from, to);
    return exchangeRateRestClient.get(roeQuery, requestContext, ExchangeRate.class);
  }

  public CompletableFuture<ExchangeRate> getExchangeRate(Invoice invoice, String fiscalYearCurrency, RequestContext requestContext) {
    CurrencyUnit invoiceCurrency = Monetary.getCurrency(invoice.getCurrency());
    CurrencyUnit systemCurrency = Monetary.getCurrency(fiscalYearCurrency);
    if (invoiceCurrency.equals(systemCurrency)) {
      invoice.setExchangeRate(1d);
      return CompletableFuture.completedFuture(new ExchangeRate().withExchangeRate(1d)
        .withFrom(fiscalYearCurrency)
        .withTo(fiscalYearCurrency));
    }
    if (invoice.getExchangeRate() == null || invoice.getExchangeRate() == 0d) {
      return getExchangeRate(invoice.getCurrency(), fiscalYearCurrency, requestContext)
        .thenApply(exchangeRate -> {
          invoice.setExchangeRate(exchangeRate.getExchangeRate());
          return exchangeRate;
        });
    }

    return CompletableFuture.completedFuture(new ExchangeRate().withExchangeRate(invoice.getExchangeRate())
      .withFrom(invoice.getCurrency())
      .withTo(fiscalYearCurrency));
  }

}
