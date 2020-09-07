package org.folio.services.finance;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.FiscalYearCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

public class FiscalYearService {

  private final RestClient fiscalYearRestClient;

  public FiscalYearService(RestClient fiscalYearRestClient) {
    this.fiscalYearRestClient = fiscalYearRestClient;
  }

  public CompletableFuture<FiscalYearCollection> getFiscalYears(int limit, int offset, String query,
      RequestContext requestContext) {
    return fiscalYearRestClient.get(query, offset, limit, requestContext, FiscalYearCollection.class);
  }

  public CompletableFuture<FiscalYear> getFiscalYear(String fiscalYearId, RequestContext requestContext) {
    return fiscalYearRestClient.getById(fiscalYearId, requestContext, FiscalYear.class);
  }

}
