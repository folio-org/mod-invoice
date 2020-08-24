package org.folio.services.exchange;

import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_EXCHANGE_RATE;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import java.util.concurrent.CompletableFuture;


public class RateOfExchangeService {
  private static final String ROE_QUERY_PARANS = ResourcePathResolver.resourcesPath(FINANCE_EXCHANGE_RATE) + "?from=%s&to=%s";
  private final RestClient exchangeRateRestClient;

  public RateOfExchangeService(RestClient exchangeRateRestClient) {
    this.exchangeRateRestClient = exchangeRateRestClient;
  }

  public CompletableFuture<ExchangeRate> getExchangeRate(String from, String to, RequestContext requestContext) {
    String roeQuery = String.format(ROE_QUERY_PARANS, from, to);
    return exchangeRateRestClient.get(roeQuery, requestContext, ExchangeRate.class);
  }

}
