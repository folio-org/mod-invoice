package org.folio.services.exchange;

import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_EXCHANGE_RATE;

import java.util.concurrent.CompletableFuture;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;


public class FinanceExchangeRateService {
  private static final String ROE_QUERY_PARAMS = ResourcePathResolver.resourcesPath(FINANCE_EXCHANGE_RATE) + "?from=%s&to=%s";
  private final RestClient restClient;

  public FinanceExchangeRateService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<ExchangeRate> getExchangeRate(String from, String to, RequestContext requestContext) {
    String roeQuery = String.format(ROE_QUERY_PARAMS, from, to);
    return restClient.get(roeQuery, requestContext, ExchangeRate.class);
  }

}
