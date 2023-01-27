package org.folio.services.finance.fiscalyear;

import static org.folio.invoices.utils.ErrorCodes.CURRENT_FISCAL_YEAR_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.isNotFound;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.finance.FundService;

import io.vertx.core.Future;

public class CurrentFiscalYearService {

  private static final String CURRENT_FISCAL_YEAR_ENDPOINT = "/finance/ledgers/{id}/current-fiscal-year";

  private final RestClient restClient;
  private final FundService fundService;

  public CurrentFiscalYearService(RestClient restClient, FundService fundService) {
    this.restClient = restClient;
    this.fundService = fundService;
  }

  public Future<FiscalYear> getCurrentFiscalYearByFund(String fundId, RequestContext requestContext) {
    return fundService.getFundById(fundId, requestContext)
      .compose(fund -> getCurrentFiscalYear(fund.getLedgerId(), requestContext));
  }

  public Future<FiscalYear> getCurrentFiscalYear(String ledgerId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(CURRENT_FISCAL_YEAR_ENDPOINT)
        .withId(ledgerId);
    return restClient.get(requestEntry, FiscalYear.class, requestContext)
      .recover(t -> {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        if (isNotFound(cause)) {
          List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(ledgerId).withKey("ledgerId"));
          throw new HttpException(400, CURRENT_FISCAL_YEAR_NOT_FOUND.toError().withParameters(parameters));
        }
        throw new CompletionException(cause);
      });

  }

}
