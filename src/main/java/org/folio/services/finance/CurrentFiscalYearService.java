package org.folio.services.finance;

import static org.folio.invoices.utils.ErrorCodes.CURRENT_FISCAL_YEAR_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.isNotFound;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Parameter;

public class CurrentFiscalYearService {

  private final RestClient currentFiscalYearRestClient;
  private final FundService fundService;

  public CurrentFiscalYearService(RestClient currentFiscalYearRestClient, FundService fundService) {
    this.currentFiscalYearRestClient = currentFiscalYearRestClient;
    this.fundService = fundService;
  }

  public CompletableFuture<FiscalYear> getCurrentFiscalYearByFund(String fundId, RequestContext requestContext) {
    return fundService.getFundById(fundId, requestContext)
      .thenCompose(fund -> getCurrentFiscalYear(fund.getLedgerId(), requestContext));
  }

  public CompletableFuture<FiscalYear> getCurrentFiscalYear(String ledgerId, RequestContext requestContext) {
    return currentFiscalYearRestClient.getById(ledgerId, requestContext, FiscalYear.class)
      .exceptionally(t -> {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        if (isNotFound(cause)) {
          List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(ledgerId).withKey("ledgerId"));
          throw new HttpException(400, CURRENT_FISCAL_YEAR_NOT_FOUND.toError().withParameters(parameters));
        }
        throw new CompletionException(cause);
      });

  }

}
