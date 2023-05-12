package org.folio.services.finance.budget;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.BudgetCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;

import io.vertx.core.Future;

public class BudgetService {
  private static final Logger log = LogManager.getLogger();

  private static final String BUDGETS_ENDPOINT = resourcesPath(BUDGETS);
  private static final String ACTIVE_BUDGET_ENDPOINT = "/finance/funds/{id}/budget";
  private static final String QUERY_BY_FUND_ID_AND_FISCAL_YEAR_ID = "budgetStatus==Active AND fundId==%s AND fiscalYearId==%s";

  private final RestClient restClient;

  public BudgetService(RestClient restClient) {
      this.restClient = restClient;
  }

  public Future<List<Budget>> getBudgetsByFundIds(Collection<String> fundIds, String invoiceFiscalYearId,
      RequestContext requestContext) {
    List<Future<Budget>> futures = fundIds.stream()
      .distinct()
      .map(fundId -> getBudgetByFundId(fundId, invoiceFiscalYearId, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(futures);
  }

  private Future<Budget> getBudgetByFundId(String fundId, String invoiceFiscalYearId, RequestContext requestContext) {
    if (invoiceFiscalYearId == null)
      return getActiveBudgetByFundId(fundId, requestContext);
    return getBudgetByFundIdAndFiscalYearId(fundId, invoiceFiscalYearId, requestContext);
  }

  private Future<Budget> getBudgetByFundIdAndFiscalYearId(String fundId, String fiscalYearId,
      RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(BUDGETS_ENDPOINT)
      .withQuery(String.format(QUERY_BY_FUND_ID_AND_FISCAL_YEAR_ID, fundId, fiscalYearId));
    return restClient.get(requestEntry, BudgetCollection.class, requestContext)
      .map(budgetCollection -> {
        if (budgetCollection.getBudgets().isEmpty()) {
          List<Parameter> parameters = List.of(
            new Parameter().withKey("fundId").withValue(fundId),
            new Parameter().withKey("fiscalYearId").withValue(fiscalYearId)
          );
          throw new HttpException(404, BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID.toError().withParameters(parameters));
        }
        return budgetCollection.getBudgets().get(0);
      })
      .onFailure(t -> log.error("Error getting budget by fundId and fiscalYearId, fundId={}, fiscalYearId={}",
        fundId, fiscalYearId, t));
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

  public Future<List<Budget>> getActiveBudgetListByFundIds(List<String> fundIds, RequestContext requestContext) {
    String queryIds = convertIdsToCqlQuery(fundIds, "fundId", true);
    String queryActive = "budgetStatus==Active";
    String query = String.format("%s AND %s", queryIds, queryActive);
    RequestEntry requestEntry = new RequestEntry(BUDGETS_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, BudgetCollection.class, requestContext)
      .map(BudgetCollection::getBudgets)
      .onFailure(t -> log.error("Failed to get budget list by fund ids, query={}", query, t));
  }
}
