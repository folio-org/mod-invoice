package org.folio.services.finance.budget;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;

import io.vertx.core.Future;

public class BudgetService {

  private static final String ACTIVE_BUDGET_ENDPOINT = "/finance/funds/{id}/budget";

  private final RestClient restClient;

  public BudgetService(RestClient restClient) {
      this.restClient = restClient;
  }

  public Future<List<Budget>> fetchBudgetsByFundIds(Collection<String> fundIds, RequestContext requestContext) {
    List<Future<Budget>> futures = fundIds.stream()
            .distinct()
            .map(fundId -> getActiveBudgetByFundId(fundId, requestContext))
            .collect(toList());

    return collectResultsOnSuccess(futures);
  }

  private Future<Budget> getActiveBudgetByFundId(String fundId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ACTIVE_BUDGET_ENDPOINT)
        .withId(fundId);
    return restClient.get(requestEntry, Budget.class, requestContext)
            .recover(t -> {
                Throwable cause = Objects.isNull(t.getCause()) ? t : t.getCause();
                if (cause instanceof HttpException) {
                    throw new HttpException(404, BUDGET_NOT_FOUND
                            .toError().withParameters(Collections.singletonList(new Parameter().withKey("fund").withValue(fundId))));
                }
                throw new CompletionException(t.getCause());
            });
  }
}
