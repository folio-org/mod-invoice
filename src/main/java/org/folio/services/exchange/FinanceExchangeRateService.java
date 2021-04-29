package org.folio.services.exchange;

import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_EXCHANGE_RATE;

import java.util.concurrent.CompletableFuture;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

public class FinanceExchangeRateService {

  private static final String EXCHANGE_RATE_ENDPOINT = ResourcePathResolver.resourcesPath(FINANCE_EXCHANGE_RATE);

  private final RestClient restClient;

  public FinanceExchangeRateService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<ExchangeRate> getExchangeRate(String from, String to, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(EXCHANGE_RATE_ENDPOINT)
        .withQueryParameter("from", from)
        .withQueryParameter("to", to);
    return restClient.get(requestEntry, requestContext, ExchangeRate.class);
  }

}
