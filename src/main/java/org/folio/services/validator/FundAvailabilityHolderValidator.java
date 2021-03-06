package org.folio.services.validator;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.jaxrs.model.Parameter;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

public class FundAvailabilityHolderValidator implements HolderValidator {

  @Override
  public void validate(List<InvoiceWorkflowDataHolder> dataHolders) {

    Map<Budget, List<InvoiceWorkflowDataHolder>> budgetHoldersMap = dataHolders.stream()
      .filter(InvoiceWorkflowDataHolder::isRestrictExpenditures)
      .collect(groupingBy(InvoiceWorkflowDataHolder::getBudget));

    List<String> failedBudgetIds = budgetHoldersMap.entrySet()
      .stream()
      .filter(entry -> Objects.nonNull(entry.getKey()
        .getAllowableExpenditure()))
      .filter(entry -> {
        MonetaryAmount newExpendedAmount = calculateNewExpendedAmount(entry.getValue());
        MonetaryAmount totalExpendedAmount = calculateTotalExpendedAmount(entry.getValue());
        return isRemainingAmountExceed(entry.getKey(), newExpendedAmount, totalExpendedAmount);
      })
      .map(Map.Entry::getKey)
      .map(Budget::getId)
      .collect(toList());

    if (!failedBudgetIds.isEmpty()) {
      Parameter parameter = new Parameter().withKey(BUDGETS)
        .withValue(failedBudgetIds.toString());
      throw new HttpException(422, FUND_CANNOT_BE_PAID.toError()
        .withParameters(Collections.singletonList(parameter)));
    }
  }

  private MonetaryAmount calculateTotalExpendedAmount(List<InvoiceWorkflowDataHolder> dataHolders) {
    CurrencyUnit currency = Monetary.getCurrency(dataHolders.get(0)
      .getTenantCurrency());
    return dataHolders.stream()
      .map(holder -> {
        MonetaryAmount newTransactionAmount = Money.of(holder.getNewTransaction()
          .getAmount(), holder.getTenantCurrency());

        MonetaryAmount existingTransactionAmount = Optional.ofNullable(holder.getExistingTransaction())
          .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
          .orElseGet(() -> Money.zero(Monetary.getCurrency(holder.getTenantCurrency())));

        return newTransactionAmount.subtract(existingTransactionAmount);
      })
      .reduce(MonetaryFunctions::sum)
      .orElseGet(() -> Money.zero(currency));
  }
    
  private boolean isRemainingAmountExceed(Budget budget, MonetaryAmount newExpendedAmount, MonetaryAmount totalExpendedAmount) {
    // [remaining amount we can expend] = (totalFunding * allowableExpenditure) - unavailable
    // where unavailable = awaitingPayment + encumbered + expenditure
    CurrencyUnit currency = newExpendedAmount.getCurrency();
    Money totalFundings = Money.of(budget.getTotalFunding(), currency);
    Money expended = Money.of(budget.getAwaitingPayment(), currency)
      .add(Money.of(budget.getExpenditures(), currency));
    BigDecimal allowableExpenditures = BigDecimal.valueOf(budget.getAllowableExpenditure())
      .movePointLeft(2);
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money totalAmountCanBeExpended = totalFundings.multiply(allowableExpenditures);
    Money amountCanBeExpended = totalAmountCanBeExpended.subtract(unavailable);

    Money afterApproveExpended = expended.add(totalExpendedAmount);

    return newExpendedAmount.isGreaterThan(amountCanBeExpended) || afterApproveExpended.isGreaterThan(totalAmountCanBeExpended);
  }

  private MonetaryAmount calculateNewExpendedAmount(List<InvoiceWorkflowDataHolder> dataHolders) {
    CurrencyUnit currency = Monetary.getCurrency(dataHolders.get(0).getTenantCurrency());
    return dataHolders.stream()
      .map(holder -> {
          MonetaryAmount newTransactionAmount = Money.of(holder.getNewTransaction().getAmount(), holder.getTenantCurrency());

          MonetaryAmount existingTransactionAmount = Optional.ofNullable(holder.getExistingTransaction())
                  .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
                  .orElseGet(() -> Money.zero(Monetary.getCurrency(holder.getTenantCurrency())));
          MonetaryAmount encumbranceAmount = Optional.ofNullable(holder.getEncumbrance())
                  .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
                  .orElseGet(() -> Money.zero(Monetary.getCurrency(holder.getTenantCurrency())));

          MonetaryAmount transactionAmountDif = newTransactionAmount.subtract(existingTransactionAmount);
          MonetaryAmount newExpendedAmount = transactionAmountDif;
          if (transactionAmountDif.isPositive()) {
              newExpendedAmount = MonetaryFunctions.max().apply(transactionAmountDif.subtract(encumbranceAmount), Money.zero(currency));
              MonetaryAmount encumbranceReminder = MonetaryFunctions.max().apply(encumbranceAmount.subtract(newExpendedAmount).add(existingTransactionAmount), Money.zero(currency));
              Optional.ofNullable(holder.getEncumbrance()).ifPresent(transaction -> transaction.setAmount(encumbranceReminder.getNumber().doubleValue()));
          }

          return newExpendedAmount;
      })
      .reduce(MonetaryFunctions::sum)
      .orElseGet(() -> Money.zero(currency));
  }
  
}
