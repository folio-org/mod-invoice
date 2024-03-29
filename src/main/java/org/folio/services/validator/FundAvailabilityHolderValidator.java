package org.folio.services.validator;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.jaxrs.model.Parameter;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

public class FundAvailabilityHolderValidator implements HolderValidator {

  @Override
  public void validate(List<InvoiceWorkflowDataHolder> dataHolders) {
    Map<Budget, List<InvoiceWorkflowDataHolder>> budgetHoldersMap = dataHolders.stream()
      .filter(InvoiceWorkflowDataHolder::isRestrictExpenditures)
      .collect(groupingBy(InvoiceWorkflowDataHolder::getBudget));

    Map<String, String> fundHoldersMap =dataHolders.stream()
      .filter(InvoiceWorkflowDataHolder::isRestrictExpenditures)
      .map(InvoiceWorkflowDataHolder::getFund)
      .filter(Objects::nonNull).collect(Collectors.toMap(Fund::getId, Fund::getCode,(fundEntityKey, fundEntityDupKey) -> fundEntityKey));

    List<String> failedBudgetIds = budgetHoldersMap.entrySet()
      .stream()
      .filter(entry -> Objects.nonNull(entry.getKey()
        .getAllowableExpenditure()))
      .filter(entry -> {
        MonetaryAmount totalExpendedAmount = calculateTotalExpendedAmount(entry.getValue());
        return isRemainingAmountExceed(entry.getKey(), totalExpendedAmount);
      })
      .map(Map.Entry::getKey)
      .map(Budget::getFundId)
      .collect(toList());

    if (!failedBudgetIds.isEmpty()) {
      Parameter parameter = new Parameter().withKey(FUNDS)
        .withValue(failedBudgetIds.stream().map(fundHoldersMap::get)
        .collect(toList()).toString());
      throw new HttpException(422, FUND_CANNOT_BE_PAID.toError()
        .withParameters(Collections.singletonList(parameter)));
    }
  }

  private MonetaryAmount calculateTotalExpendedAmount(List<InvoiceWorkflowDataHolder> dataHolders) {
    CurrencyUnit currency = Monetary.getCurrency(dataHolders.get(0)
      .getFyCurrency());
    return dataHolders.stream()
      .map(holder -> {
        MonetaryAmount newTransactionAmount = Money.of(holder.getNewTransaction()
          .getAmount(), holder.getFyCurrency());

        MonetaryAmount existingTransactionAmount = Optional.ofNullable(holder.getExistingTransaction())
          .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
          .orElseGet(() -> Money.zero(Monetary.getCurrency(holder.getFyCurrency())));

        return newTransactionAmount.subtract(existingTransactionAmount);
      })
      .reduce(MonetaryFunctions::sum)
      .orElseGet(() -> Money.zero(currency));
  }

  private boolean isRemainingAmountExceed(Budget budget, MonetaryAmount totalExpendedAmount) {
    // [remaining amount we can expend] = (totalFunding * allowableExpenditure) - expended
    // where expended = awaitingPayment + expenditure
    CurrencyUnit currency = totalExpendedAmount.getCurrency();
    Money totalFundings = Money.of(budget.getTotalFunding(), currency);
    Money expended = Money.of(budget.getAwaitingPayment(), currency)
      .add(Money.of(budget.getExpenditures(), currency));
    BigDecimal allowableExpenditures = BigDecimal.valueOf(budget.getAllowableExpenditure())
      .movePointLeft(2);
    Money totalAmountCanBeExpended = totalFundings.multiply(allowableExpenditures);
    Money afterApproveExpended = expended.add(totalExpendedAmount);
    return afterApproveExpended.isGreaterThan(totalAmountCanBeExpended);
  }

}
