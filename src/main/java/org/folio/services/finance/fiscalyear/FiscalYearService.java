package org.folio.services.finance.fiscalyear;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import static org.folio.invoices.utils.ResourcePathResolver.FISCAL_YEARS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class FiscalYearService {

  private static final String FISCAL_YEAR_BY_ID_ENDPOINT = resourcesPath(FISCAL_YEARS) + "/{id}";

  private final RestClient restClient;

  public FiscalYearService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<FiscalYear> getFiscalYear(String fiscalYearId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(FISCAL_YEAR_BY_ID_ENDPOINT).withId(fiscalYearId);
    return restClient.get(requestEntry, requestContext, FiscalYear.class);
  }

}
