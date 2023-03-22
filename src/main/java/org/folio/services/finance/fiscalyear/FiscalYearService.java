package org.folio.services.finance.fiscalyear;

import static org.folio.invoices.utils.ResourcePathResolver.FISCAL_YEARS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.FiscalYearCollection;

public class FiscalYearService {
  private static final Logger log = LogManager.getLogger();

  private static final String FISCAL_YEARS_ENDPOINT = resourcesPath(FISCAL_YEARS);
  private static final String FISCAL_YEAR_BY_ID_ENDPOINT = resourcesPath(FISCAL_YEARS) + "/{id}";

  private final RestClient restClient;

  public FiscalYearService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<FiscalYear> getFiscalYear(String fiscalYearId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(FISCAL_YEAR_BY_ID_ENDPOINT).withId(fiscalYearId);
    return restClient.get(requestEntry, FiscalYear.class, requestContext);
  }

  public Future<FiscalYearCollection> getFiscalYearCollectionByQuery(String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(FISCAL_YEARS_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, FiscalYearCollection.class, requestContext)
      .onFailure(t -> log.error("Failed to get fiscal year collection, query={}", query, t));
  }

}
