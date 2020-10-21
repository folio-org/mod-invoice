package org.folio.services.finance;

import static org.folio.invoices.utils.ErrorCodes.BUDGET_EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.INACTIVE_EXPENSE_CLASS;
import static org.folio.services.finance.BudgetExpenseClassService.EXPENSE_CLASS_NAME;
import static org.folio.services.finance.BudgetExpenseClassService.FUND_CODE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.BudgetExpenseClass;
import org.folio.rest.acq.model.finance.BudgetExpenseClassCollection;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Vertx;

public class BudgetExpenseClassTest {

    private BudgetExpenseClassService budgetExpenseClassService;

    @Mock
    private RestClient budgetExpenseClassRestClient;

    @Mock
    private FundService fundService;

    @Mock
    private ExpenseClassRetrieveService expenseClassRetrieveService;

    @Mock
    private RestClient activeBudgetRestClient;

    @Mock
    private RequestContext requestContext;

    @BeforeEach
    public void initMocks() {
        MockitoAnnotations.openMocks(this);
        budgetExpenseClassService = new BudgetExpenseClassService(budgetExpenseClassRestClient,
                fundService,
                expenseClassRetrieveService,
                activeBudgetRestClient);
    }

    @Test
    void shouldThrowExceptionWithInactiveExpenseClassCodeWhenCheckExpenseClassesWithInactiveExpenseClass() {
        String inactiveExpenseClassId = UUID.randomUUID().toString();
        String activeExpenseClassId = UUID.randomUUID().toString();

        FundDistribution fundDistributionWithInactiveExpenseClass = new FundDistribution()
                .withFundId(UUID.randomUUID().toString())
                .withExpenseClassId(inactiveExpenseClassId);

        FundDistribution fundDistributionWithActiveExpenseClass = new FundDistribution()
                .withFundId(UUID.randomUUID().toString())
                .withExpenseClassId(activeExpenseClassId);

        FundDistribution fundDistributionWithoutExpenseClass = new FundDistribution()
                .withFundId(UUID.randomUUID().toString());

        InvoiceLine invoiceLine = new InvoiceLine()
                .withFundDistributions(Arrays.asList(fundDistributionWithInactiveExpenseClass, fundDistributionWithoutExpenseClass));

        List<InvoiceLine> invoiceLines = Collections.singletonList(invoiceLine);
        Adjustment adjustment = new Adjustment()
                .withFundDistributions(Collections.singletonList(fundDistributionWithActiveExpenseClass));
        Invoice invoice = new Invoice().withAdjustments(Collections.singletonList(adjustment));

        BudgetExpenseClass active = new BudgetExpenseClass().withBudgetId(UUID.randomUUID().toString())
                .withExpenseClassId(activeExpenseClassId)
                .withStatus(BudgetExpenseClass.Status.ACTIVE)
                .withId(UUID.randomUUID().toString());
        BudgetExpenseClass inactive = new BudgetExpenseClass().withBudgetId(UUID.randomUUID().toString())
                .withExpenseClassId(inactiveExpenseClassId)
                .withStatus(BudgetExpenseClass.Status.INACTIVE)
                .withId(UUID.randomUUID().toString());

        Fund inactiveExpenseClassFund = new Fund().withCode("inactive fund");
        ExpenseClass inactiveExpenseClass = new ExpenseClass().withName("inactive class");

        when(activeBudgetRestClient.getById(anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Budget().withId(UUID.randomUUID().toString())));

        when(budgetExpenseClassRestClient.get(contains(inactiveExpenseClassId), anyInt(), anyInt(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(new BudgetExpenseClassCollection()
                        .withBudgetExpenseClasses(Collections.singletonList(inactive)).withTotalRecords(1)));

        when(budgetExpenseClassRestClient.get(contains(activeExpenseClassId), anyInt(), anyInt(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(new BudgetExpenseClassCollection()
                        .withBudgetExpenseClasses(Collections.singletonList(active)).withTotalRecords(1)));
        when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

        when(fundService.getFundById(anyString(), any())).thenReturn(CompletableFuture.completedFuture(inactiveExpenseClassFund));
        when(expenseClassRetrieveService.getExpenseClassById(anyString(), any())).thenReturn(CompletableFuture.completedFuture(inactiveExpenseClass));

        CompletableFuture<Void> future = budgetExpenseClassService.checkExpenseClasses(invoiceLines, invoice, requestContext);

        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);

        assertThat(executionException.getCause(), instanceOf(HttpException.class));

        HttpException exception = (HttpException) executionException.getCause();

        assertEquals(400, exception.getCode());
        Errors errors = exception.getErrors();

        Error error = errors.getErrors().get(0);
        assertEquals(INACTIVE_EXPENSE_CLASS.getCode(), error.getCode());
        String expenseClassNameFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(EXPENSE_CLASS_NAME)).findFirst().get().getValue();
        assertEquals(inactiveExpenseClass.getName(), expenseClassNameFromError);
        String fundCodeFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(FUND_CODE)).findFirst().get().getValue();
        assertEquals(inactiveExpenseClassFund.getCode(), fundCodeFromError);
    }

    @Test
    void shouldThrowExceptionWithBudgetExpenseClassNotFoundCodeWhenCheckExpenseClassesWithoutExpenseClasses() {
        String notAssignedExpenseClassId = UUID.randomUUID().toString();
        String activeExpenseClassId = UUID.randomUUID().toString();

        FundDistribution fundDistributionWithActiveExpenseClass = new FundDistribution()
                .withFundId(UUID.randomUUID().toString())
                .withExpenseClassId(activeExpenseClassId);

        FundDistribution fundDistributionWithNotAssignedExpenseClass = new FundDistribution()
                .withFundId(UUID.randomUUID().toString())
                .withExpenseClassId(notAssignedExpenseClassId);

        FundDistribution fundDistributionWithoutExpenseClass = new FundDistribution()
                .withFundId(UUID.randomUUID().toString());

        InvoiceLine invoiceLine = new InvoiceLine()
                .withFundDistributions(Arrays.asList(fundDistributionWithActiveExpenseClass, fundDistributionWithoutExpenseClass));

        List<InvoiceLine> invoiceLines = Collections.singletonList(invoiceLine);
        Adjustment adjustment = new Adjustment()
                .withFundDistributions(Collections.singletonList(fundDistributionWithNotAssignedExpenseClass));
        Invoice invoice = new Invoice().withAdjustments(Collections.singletonList(adjustment));

        BudgetExpenseClass active = new BudgetExpenseClass().withBudgetId(UUID.randomUUID().toString())
                .withExpenseClassId(activeExpenseClassId)
                .withStatus(BudgetExpenseClass.Status.ACTIVE)
                .withId(UUID.randomUUID().toString());

        Fund noExpenseClassFund = new Fund().withCode("no expense class fund");
        ExpenseClass notAssignedExpenseClass = new ExpenseClass().withName("not assigned class");

        when(activeBudgetRestClient.getById(anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new Budget().withId(UUID.randomUUID().toString())));

        when(budgetExpenseClassRestClient.get(contains(notAssignedExpenseClassId), anyInt(), anyInt(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(new BudgetExpenseClassCollection()
                        .withTotalRecords(0)));

        when(budgetExpenseClassRestClient.get(contains(activeExpenseClassId), anyInt(), anyInt(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(new BudgetExpenseClassCollection()
                        .withBudgetExpenseClasses(Collections.singletonList(active)).withTotalRecords(1)));
        when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

        when(fundService.getFundById(anyString(), any())).thenReturn(CompletableFuture.completedFuture(noExpenseClassFund));
        when(expenseClassRetrieveService.getExpenseClassById(anyString(), any())).thenReturn(CompletableFuture.completedFuture(notAssignedExpenseClass));

        CompletableFuture<Void> future = budgetExpenseClassService.checkExpenseClasses(invoiceLines, invoice, requestContext);

        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);

        assertThat(executionException.getCause(), instanceOf(HttpException.class));

        HttpException exception = (HttpException) executionException.getCause();

        assertEquals(400, exception.getCode());
        Errors errors = exception.getErrors();

        Error error = errors.getErrors().get(0);
        assertEquals(BUDGET_EXPENSE_CLASS_NOT_FOUND.getCode(), error.getCode());
        String expenseClassNameFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(EXPENSE_CLASS_NAME)).findFirst().get().getValue();
        assertEquals(notAssignedExpenseClass.getName(), expenseClassNameFromError);
        String fundCodeFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(FUND_CODE)).findFirst().get().getValue();
        assertEquals(noExpenseClassFund.getCode(), fundCodeFromError);
    }

}
