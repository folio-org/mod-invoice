package org.folio.services.finance.budget;

import static org.folio.completablefuture.FolioVertxCompletableFuture.allOf;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.INACTIVE_EXPENSE_CLASS;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGET_EXPENSE_CLASSES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Parameter;

public class BudgetExpenseClassService {

  private static final String BUDGET_EXPENSE_CLASS_ENDPOINT = resourcesPath(BUDGET_EXPENSE_CLASSES);

  public static final String FUND_CODE = "fundCode";
  public static final String EXPENSE_CLASS_NAME = "expenseClassName";

  private final RestClient restClient;

  public BudgetExpenseClassService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<List<InvoiceWorkflowDataHolder>> checkExpenseClasses(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {

    return allOf(requestContext.getContext(), holders.stream()
      .filter(holder -> Objects.nonNull(holder.getFundDistribution().getExpenseClassId()))
      .map(holder -> checkExpenseClass(holder, requestContext))
      .toArray(CompletableFuture[]::new)).thenApply(aVoid -> holders);

  }

  private CompletableFuture<Void> checkExpenseClass(InvoiceWorkflowDataHolder holder, RequestContext requestContext) {
    Budget budget = holder.getBudget();
    FundDistribution fundDistribution = holder.getFundDistribution();
    String query = String.format("budgetId==%s and expenseClassId==%s", budget.getId(), fundDistribution.getExpenseClassId());
    RequestEntry requestEntry = new RequestEntry(BUDGET_EXPENSE_CLASS_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(1);
    return restClient.get(requestEntry, requestContext, BudgetExpenseClassCollection.class)
      .thenAccept(budgetExpenseClasses -> {
        checkExpenseClassAssignedToBudget(holder, budgetExpenseClasses);
        checkExpenseClassActive(holder, budgetExpenseClasses);
      });
  }

  private void checkExpenseClassAssignedToBudget(InvoiceWorkflowDataHolder holder,
      BudgetExpenseClassCollection budgetExpenseClasses) {
    if (budgetExpenseClasses.getBudgetExpenseClasses().isEmpty()) {
      throw new HttpException(400, BUDGET_EXPENSE_CLASS_NOT_FOUND.toError()
        .withParameters(getFundIdExpenseClassIdParameters(holder)));
    }
  }

  private void checkExpenseClassActive(InvoiceWorkflowDataHolder holder, BudgetExpenseClassCollection budgetExpenseClasses) {
    if (isInactive(budgetExpenseClasses)) {
      throw new HttpException(400, INACTIVE_EXPENSE_CLASS.toError()
        .withParameters(getFundIdExpenseClassIdParameters(holder)));
    }

  }

  private boolean isInactive(BudgetExpenseClassCollection budgetExpenseClasses) {
    return budgetExpenseClasses
            .getBudgetExpenseClasses().stream()
            .anyMatch(budgetExpenseClass -> budgetExpenseClass.getStatus() == BudgetExpenseClass.Status.INACTIVE);
  }

  private List<Parameter> getFundIdExpenseClassIdParameters(InvoiceWorkflowDataHolder holder) {
    Fund fund = holder.getFund();
    ExpenseClass expenseClass = holder.getExpenseClass();
    return Arrays.asList(new Parameter().withKey(FUND_CODE)
      .withValue(fund.getCode()),
        new Parameter().withKey(EXPENSE_CLASS_NAME)
          .withValue(expenseClass.getName()));
  }

}
