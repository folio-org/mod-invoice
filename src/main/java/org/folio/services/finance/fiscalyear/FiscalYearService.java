package org.folio.services.finance.fiscalyear;

import static org.folio.invoices.utils.ResourcePathResolver.FISCAL_YEARS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.Future;

public class FiscalYearService {

  private static final String FISCAL_YEAR_BY_ID_ENDPOINT = resourcesPath(FISCAL_YEARS) + "/{id}";

  private final RestClient restClient;

  public FiscalYearService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<FiscalYear> getFiscalYear(String fiscalYearId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(FISCAL_YEAR_BY_ID_ENDPOINT).withId(fiscalYearId);
    return restClient.get(requestEntry, FiscalYear.class, requestContext);
  }

}
