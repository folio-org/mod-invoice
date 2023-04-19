package org.folio.services.finance.budget;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.BudgetCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class BudgetServiceTest {
  AutoCloseable closeable;

  @InjectMocks
  private BudgetService budgetService;

  @Mock
  private RestClient restClient;

  @Mock
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    closeable = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void resetMocks() throws Exception {
    closeable.close();
  }

  @Test
  void shouldThrowErrorWhenBudgetIsNotFoundUsingFiscalYearId(VertxTestContext vertxTestContext) {
    String fundId = UUID.randomUUID().toString();
    List<String> fundIds = List.of(fundId);
    String invoiceFiscalYearId = UUID.randomUUID().toString();
    BudgetCollection budgetCollection = new BudgetCollection()
      .withBudgets(List.of())
      .withTotalRecords(0);
    when(restClient.get(any(RequestEntry.class), any(), any()))
      .thenReturn(Future.succeededFuture(budgetCollection));

    Future<List<Budget>> f = budgetService.getBudgetsByFundIds(fundIds, invoiceFiscalYearId, requestContext);

    vertxTestContext.assertFailure(f)
      .onComplete(result -> {
        assertThat(result.cause(), instanceOf(HttpException.class));
        HttpException exception = (HttpException) result.cause();
        assertEquals(404, exception.getCode());
        Errors errors = exception.getErrors();
        Error error = errors.getErrors().get(0);
        assertEquals(BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID.getCode(), error.getCode());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void shouldReturnBudgetsWhenGettingBudgetsUsingFiscalYearId(VertxTestContext vertxTestContext) {
    String fundId = UUID.randomUUID().toString();
    List<String> fundIds = List.of(fundId);
    String invoiceFiscalYearId = UUID.randomUUID().toString();
    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString());
    BudgetCollection budgetCollection = new BudgetCollection()
      .withBudgets(List.of(budget))
      .withTotalRecords(1);
    String query = String.format("fundId==%s and fiscalYearId==%s", fundId, invoiceFiscalYearId);
    ArgumentCaptor<RequestEntry> requestEntryCaptor = ArgumentCaptor.forClass(RequestEntry.class);
    when(restClient.get(requestEntryCaptor.capture(), any(), any()))
      .thenReturn(Future.succeededFuture(budgetCollection));

    Future<List<Budget>> f = budgetService.getBudgetsByFundIds(fundIds, invoiceFiscalYearId, requestContext);

    vertxTestContext.assertComplete(f)
      .onComplete(result -> {
        verify(restClient, times(1)).get(any(RequestEntry.class), any(), any());
        List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
        assertEquals(requestEntries.get(0).getBaseEndpoint(), resourcesPath(BUDGETS));
        assertEquals(requestEntries.get(0).getQueryParams().get("query"), encodeQuery(query));
        List<Budget> budgets = result.result();
        assertEquals(1, budgets.size());
        assertEquals(budget.getId(), budgets.get(0).getId());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void shouldReturnActiveBudgetsWhenGettingBudgetsWithoutUsingFiscalYearId(VertxTestContext vertxTestContext) {
    String fundId = UUID.randomUUID().toString();
    List<String> fundIds = List.of(fundId);
    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString());
    ArgumentCaptor<RequestEntry> requestEntryCaptor = ArgumentCaptor.forClass(RequestEntry.class);
    when(restClient.get(requestEntryCaptor.capture(), any(), any()))
      .thenReturn(Future.succeededFuture(budget));

    Future<List<Budget>> f = budgetService.getBudgetsByFundIds(fundIds, null, requestContext);

    vertxTestContext.assertComplete(f)
      .onComplete(result -> {
        verify(restClient, times(1)).get(any(RequestEntry.class), any(), any());
        List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
        assertEquals(requestEntries.get(0).getBaseEndpoint(), "/finance/funds/{id}/budget");
        assertEquals(requestEntries.get(0).getPathParams().get("id"), fundId);
        List<Budget> budgets = result.result();
        assertEquals(1, budgets.size());
        assertEquals(budget.getId(), budgets.get(0).getId());
        vertxTestContext.completeNow();
      });
  }

}
