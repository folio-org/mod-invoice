package org.folio.services;

import static javax.money.Monetary.getDefaultRounding;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;

import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.acq.model.finance.Transaction;

import org.folio.rest.jaxrs.model.FundDistribution;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.javamoney.moneta.function.MonetaryOperators;

public class FundsDistributionService {

  public static <T extends InvoiceWorkflowDataHolder> List<T> distributeFunds(List<T> holders) {
    Map<InvoiceLine, List<InvoiceWorkflowDataHolder>> lineHoldersMap = holders.stream()
      .filter(holder -> Objects.nonNull(holder.getInvoiceLine()))
      .collect(Collectors.groupingBy(InvoiceWorkflowDataHolder::getInvoiceLine));

    lineHoldersMap.forEach((invoiceLine, invoiceWorkflowDataHolder) -> {
      CurrencyUnit invoiceCurrency = Monetary.getCurrency(holders.get(0).getInvoiceCurrency());
      CurrencyConversion conversion = invoiceWorkflowDataHolder.stream()
        .map(InvoiceWorkflowDataHolder::getConversion)
        .findFirst()
        .get();

      MonetaryAmount expectedTotal = Money.of(invoiceLine.getSubTotal(), invoiceCurrency)
        .with(conversion)
        .with(getDefaultRounding());
      MonetaryAmount calculatedTotal = invoiceWorkflowDataHolder.stream()
        .map(InvoiceWorkflowDataHolder::getFundDistribution)
        .map(fundDistribution -> getDistributionAmount(fundDistribution, expectedTotal, invoiceCurrency, conversion))
        .reduce((money, money2) -> Money.from(MonetaryFunctions.sum(money, money2)))
        .orElseGet(() -> Money.zero(invoiceCurrency));

      MonetaryAmount remainder = expectedTotal.abs()
        .subtract(calculatedTotal.abs());
      int remainderSignum = remainder.signum();
      MonetaryAmount smallestUnit = getSmallestUnit(expectedTotal, remainderSignum);

      for (ListIterator<InvoiceWorkflowDataHolder> iterator = getIterator(invoiceWorkflowDataHolder,
          remainderSignum); isIteratorHasNext(iterator, remainderSignum);) {

        final InvoiceWorkflowDataHolder holder = iteratorNext(iterator, remainderSignum);
        CurrencyUnit fyCurrency = Monetary.getCurrency(holder.getFyCurrency());
        MonetaryAmount initialAmount = getDistributionAmount(holder.getFundDistribution(), expectedTotal, invoiceCurrency,
          conversion);

        if (!remainder.isZero()) {
          initialAmount = initialAmount.add(smallestUnit);
          remainder = remainder.abs()
            .subtract(smallestUnit.abs())
            .multiply(remainderSignum);
        }

        MonetaryAmount expended = Optional.of(holder)
          .map(InvoiceWorkflowDataHolder::getNewTransaction)
          .map(Transaction::getEncumbrance)
          .map(Encumbrance::getAmountExpended)
          .map(aDouble -> Money.of(aDouble, fyCurrency))
          .orElse(Money.zero(fyCurrency));

        MonetaryAmount awaitingPayment = Optional.of(holder)
          .map(InvoiceWorkflowDataHolder::getNewTransaction)
          .map(Transaction::getEncumbrance)
          .map(Encumbrance::getAmountAwaitingPayment)
          .map(aDouble -> Money.of(aDouble, fyCurrency))
          .orElse(Money.zero(fyCurrency));

        MonetaryAmount amount = MonetaryFunctions.max()
          .apply(initialAmount.subtract(expended)
            .subtract(awaitingPayment), Money.zero(fyCurrency));

        holder.getNewTransaction().setAmount(amount.getNumber().doubleValue());
        if (holder.getNewTransaction().getTransactionType() == Transaction.TransactionType.ENCUMBRANCE) {
          holder.getNewTransaction()
            .getEncumbrance()
            .setInitialAmountEncumbered(initialAmount.getNumber().doubleValue());
        }
      }
    });
    return holders;
  }

  private static MonetaryAmount getDistributionAmount(FundDistribution fundDistribution, MonetaryAmount total,
    CurrencyUnit currency, CurrencyConversion conversion) {
    if (fundDistribution.getDistributionType() == FundDistribution.DistributionType.AMOUNT) {
      return Money.of(fundDistribution.getValue(), currency)
        .with(conversion)
        .with(getDefaultRounding());
    }
    return total.with(MonetaryOperators.percent(fundDistribution.getValue()))
      .with(getDefaultRounding());
  }

  private static MonetaryAmount getSmallestUnit(MonetaryAmount expectedAdjustmentValue, int remainderSignum) {
    CurrencyUnit currencyUnit = expectedAdjustmentValue.getCurrency();
    int decimalPlaces = currencyUnit.getDefaultFractionDigits();
    int smallestUnitSignum = expectedAdjustmentValue.signum() * remainderSignum;
    return Money.of(1 / Math.pow(10, decimalPlaces), currencyUnit)
      .multiply(smallestUnitSignum);
  }

  private static ListIterator<InvoiceWorkflowDataHolder> getIterator(List<InvoiceWorkflowDataHolder> holders, int remainder) {
    return remainder > 0 ? holders.listIterator(holders.size()) : holders.listIterator();
  }

  private static boolean isIteratorHasNext(ListIterator<InvoiceWorkflowDataHolder> iterator, int remainder) {
    return remainder > 0 ? iterator.hasPrevious() : iterator.hasNext();
  }

  private static InvoiceWorkflowDataHolder iteratorNext(ListIterator<InvoiceWorkflowDataHolder> iterator, int remainder) {
    return remainder > 0 ? iterator.previous() : iterator.next();
  }
}
