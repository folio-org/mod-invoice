package org.folio.services.finance;

import static java.util.stream.Collectors.toList;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.allOf;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.INACTIVE_EXPENSE_CLASS;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.BudgetExpenseClass;
import org.folio.rest.acq.model.finance.BudgetExpenseClassCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.expence.ExpenseClassRetrieveService;

public class BudgetExpenseClassService {

  public static final String FUND_CODE = "fundCode";
  public static final String EXPENSE_CLASS_NAME = "expenseClassName";

  private final RestClient budgetExpenseClassRestClient;
  private final FundService fundService;
  private final ExpenseClassRetrieveService expenseClassRetrieveService;

  public BudgetExpenseClassService(RestClient budgetExpenseClassRestClient, FundService fundService, ExpenseClassRetrieveService expenseClassRetrieveService) {
    this.budgetExpenseClassRestClient = budgetExpenseClassRestClient;
    this.fundService = fundService;
    this.expenseClassRetrieveService = expenseClassRetrieveService;
  }

  public CompletableFuture<Void> checkExpenseClasses(List<InvoiceLine> invoiceLines, Invoice invoice, RequestContext requestContext) {
    List<FundDistribution> fundDistributionsWithExpenseClasses = getFundDistributionsWithExpenseClasses(invoiceLines, invoice);

    return allOf(requestContext.getContext(), fundDistributionsWithExpenseClasses.stream()
      .map(fundDistribution -> checkExpenseClass(fundDistribution, requestContext))
      .toArray(CompletableFuture[]::new));

  }

  private CompletableFuture<Void> checkExpenseClass(FundDistribution fundDistribution, RequestContext requestContext) {
    String query = String.format("budget.fundId==%s and expenseClassId==%s", fundDistribution.getFundId(), fundDistribution.getExpenseClassId());
    return budgetExpenseClassRestClient.get(query, 0, 1, requestContext, BudgetExpenseClassCollection.class)
      .thenCompose(budgetExpenseClasses -> checkExpenseClassAssignedToBudget(fundDistribution, budgetExpenseClasses, requestContext)
              .thenCompose(aVoid -> checkExpenseClassActive(fundDistribution, budgetExpenseClasses, requestContext)));
  }

  private CompletableFuture<Void> checkExpenseClassAssignedToBudget(FundDistribution fundDistribution,
                                                                    BudgetExpenseClassCollection budgetExpenseClasses,
                                                                    RequestContext requestContext) {
    if (budgetExpenseClasses.getTotalRecords() == 0) {
      return getFundIdExpenseClassIdParameters(fundDistribution, requestContext)
              .thenAccept(parameters -> {
                throw new HttpException(400, BUDGET_EXPENSE_CLASS_NOT_FOUND.toError()
                        .withParameters(parameters));
              });
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> checkExpenseClassActive(FundDistribution fundDistribution,
                                                          BudgetExpenseClassCollection budgetExpenseClasses,
                                                          RequestContext requestContext) {
    if (isInactive(budgetExpenseClasses)) {
      return getFundIdExpenseClassIdParameters(fundDistribution, requestContext)
              .thenAccept(parameters -> {
                throw new HttpException(400, INACTIVE_EXPENSE_CLASS.toError()
                        .withParameters(parameters));
              });
    }
    return CompletableFuture.completedFuture(null);
  }

  private boolean isInactive(BudgetExpenseClassCollection budgetExpenseClasses) {
    return budgetExpenseClasses
            .getBudgetExpenseClasses().stream()
            .anyMatch(budgetExpenseClass -> budgetExpenseClass.getStatus() == BudgetExpenseClass.Status.INACTIVE);
  }

  private CompletableFuture<List<Parameter>> getFundIdExpenseClassIdParameters(FundDistribution fundDistribution, RequestContext requestContext) {
    return fundService.getFundById(fundDistribution.getFundId(), requestContext)
            .thenCompose(fund -> expenseClassRetrieveService.getExpenseClassById(fundDistribution.getExpenseClassId(), requestContext)
            .thenApply(expenseClass -> Arrays.asList(
                    new Parameter().withKey(FUND_CODE).withValue(fund.getCode()),
                    new Parameter().withKey(EXPENSE_CLASS_NAME).withValue(expenseClass.getName())
            )));
  }

  private List<FundDistribution> getFundDistributionsWithExpenseClasses(List<InvoiceLine> invoiceLines, Invoice invoice) {

    List<FundDistribution> fdFromInvoiceLines = invoiceLines.stream()
      .flatMap(lines -> lines.getFundDistributions().stream())
      .filter(fundDistribution -> Objects.nonNull(fundDistribution.getExpenseClassId()))
      .collect(toList());

    List<FundDistribution> fdFromAdjustments = invoice.getAdjustments().stream()
      .flatMap(adj -> adj.getFundDistributions().stream())
      .filter(fundDistribution -> Objects.nonNull(fundDistribution.getExpenseClassId()))
      .collect(toList());

    fdFromInvoiceLines.addAll(fdFromAdjustments);
    return fdFromInvoiceLines;
  }

}
