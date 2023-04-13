package org.folio.services.finance.budget;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.INACTIVE_EXPENSE_CLASS;
import static org.folio.services.finance.budget.BudgetExpenseClassService.EXPENSE_CLASS_NAME;
import static org.folio.services.finance.budget.BudgetExpenseClassService.FUND_CODE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.BudgetExpenseClass;
import org.folio.rest.acq.model.finance.BudgetExpenseClassCollection;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.finance.budget.BudgetExpenseClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class BudgetExpenseClassTest {

    private BudgetExpenseClassService budgetExpenseClassService;

    @Mock
    private RestClient restClient;

    @Mock
    private RequestContext requestContext;

    @BeforeEach
    public void initMocks() {
        MockitoAnnotations.openMocks(this);
        budgetExpenseClassService = new BudgetExpenseClassService(restClient);
    }

    @Test
    void shouldThrowExceptionWithInactiveExpenseClassCodeWhenCheckExpenseClassesWithInactiveExpenseClass(VertxTestContext vertxTestContext) {
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

        List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();
        InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
                .withInvoice(invoice)
                .withInvoiceLine(invoiceLine)
                .withFundDistribution(fundDistributionWithInactiveExpenseClass)
                .withExpenseClass(inactiveExpenseClass)
                .withBudget(new Budget().withFundId(UUID.randomUUID().toString()))
                .withFund(inactiveExpenseClassFund);

        InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
                .withInvoice(invoice)
                .withInvoiceLine(invoiceLine)
                .withBudget(new Budget().withFundId(UUID.randomUUID().toString()))
                .withFundDistribution(fundDistributionWithoutExpenseClass);

        InvoiceWorkflowDataHolder holder3 = new InvoiceWorkflowDataHolder()
                .withInvoice(invoice)
                .withAdjustment(adjustment)
                .withFundDistribution(fundDistributionWithActiveExpenseClass)
                .withBudget(new Budget().withFundId(UUID.randomUUID().toString()))
                .withExpenseClass(new ExpenseClass().withId(activeExpenseClassId));

        holders.add(holder1);
        holders.add(holder2);
        holders.add(holder3);

        when(restClient.get(any(RequestEntry.class), any(), any()))
                .thenAnswer(invocation -> {
                    RequestEntry requestEntry = invocation.getArgument(0);
                    BudgetExpenseClass bec = requestEntry.buildEndpoint().contains(activeExpenseClassId) ? active : inactive;
                    return succeededFuture(new BudgetExpenseClassCollection()
                                                          .withBudgetExpenseClasses(Collections.singletonList(bec)).withTotalRecords(1));
                });

        when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

        Future<List<InvoiceWorkflowDataHolder>> future = budgetExpenseClassService.checkExpenseClasses(holders, requestContext);
      vertxTestContext.assertFailure(future)
        .onComplete(result -> {
          assertThat(result.cause(), instanceOf(HttpException.class));

          HttpException exception = (HttpException) result.cause();

          assertEquals(400, exception.getCode());

          Errors errors = exception.getErrors();
          Error error = errors.getErrors().get(0);

          assertEquals(INACTIVE_EXPENSE_CLASS.getCode(), error.getCode());
          String expenseClassNameFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(EXPENSE_CLASS_NAME)).findFirst().get().getValue();
          assertEquals(inactiveExpenseClass.getName(), expenseClassNameFromError);
          String fundCodeFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(FUND_CODE)).findFirst().get().getValue();
          assertEquals(inactiveExpenseClassFund.getCode(), fundCodeFromError);
          vertxTestContext.completeNow();
        });

    }

    @Test
    void shouldThrowExceptionWithBudgetExpenseClassNotFoundCodeWhenCheckExpenseClassesWithoutExpenseClasses(VertxTestContext vertxTestContext) {
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

        List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();
        InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
                .withInvoice(invoice)
                .withInvoiceLine(invoiceLine)
                .withFundDistribution(fundDistributionWithActiveExpenseClass)
                .withBudget(new Budget().withFundId(UUID.randomUUID().toString()))
                .withExpenseClass(new ExpenseClass().withId(activeExpenseClassId));

        InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
                .withInvoice(invoice)
                .withInvoiceLine(invoiceLine)
                .withBudget(new Budget().withFundId(UUID.randomUUID().toString()))
                .withFundDistribution(fundDistributionWithoutExpenseClass);

        InvoiceWorkflowDataHolder holder3 = new InvoiceWorkflowDataHolder()
                .withInvoice(invoice)
                .withAdjustment(adjustment)
                .withFundDistribution(fundDistributionWithNotAssignedExpenseClass)
                .withExpenseClass(notAssignedExpenseClass)
                .withBudget(new Budget().withFundId(UUID.randomUUID().toString()))
                .withFund(noExpenseClassFund);

        holders.add(holder1);
        holders.add(holder2);
        holders.add(holder3);

        when(restClient.get(any(RequestEntry.class), any(), any()))
            .thenAnswer(invocation -> {
                RequestEntry requestEntry = invocation.getArgument(0);
                List<BudgetExpenseClass> budgetExpenseClasses = requestEntry.buildEndpoint().contains(notAssignedExpenseClassId)
                    ? Collections.emptyList() : Collections.singletonList(active);
                return succeededFuture(new BudgetExpenseClassCollection()
                                                             .withBudgetExpenseClasses(budgetExpenseClasses).withTotalRecords(1));
            });
        when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());


        Future<List<InvoiceWorkflowDataHolder>> future = budgetExpenseClassService.checkExpenseClasses(holders, requestContext);
        vertxTestContext.assertFailure(future)
          .onComplete(result -> {
            assertThat(result.cause(), instanceOf(HttpException.class));

            HttpException exception = (HttpException) result.cause();

            assertEquals(400, exception.getCode());
            Errors errors = exception.getErrors();

            Error error = errors.getErrors().get(0);
            assertEquals(BUDGET_EXPENSE_CLASS_NOT_FOUND.getCode(), error.getCode());
            String expenseClassNameFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(EXPENSE_CLASS_NAME)).findFirst().get().getValue();
            assertEquals(notAssignedExpenseClass.getName(), expenseClassNameFromError);
            String fundCodeFromError = error.getParameters().stream().filter(parameter -> parameter.getKey().equals(FUND_CODE)).findFirst().get().getValue();
            assertEquals(noExpenseClassFund.getCode(), fundCodeFromError);
            vertxTestContext.completeNow();
          });
    }

}
