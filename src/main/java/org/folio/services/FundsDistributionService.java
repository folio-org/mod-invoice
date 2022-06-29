package org.folio.services;

import static javax.money.Monetary.getDefaultRounding;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.javamoney.moneta.Money;

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

      MonetaryAmount expectedTotal = Money.of(invoiceLine.getTotal(), invoiceCurrency)
        .with(conversion)
        .with(getDefaultRounding());

      MonetaryAmount calculatedTotal = invoiceWorkflowDataHolder.stream()
        .map(h -> {
          Transaction transaction = h.getNewTransaction();
          if (expectedTotal.isZero() && transaction.getTransactionType().equals(TransactionType.CREDIT)) {
            return transaction.getAmount() * -1;
          } else {
            return transaction.getAmount();
          }
        })
        .map(aDouble -> Money.of(aDouble, conversion.getCurrency()))
        .reduce(Money::add)
        .orElse(Money.zero(conversion.getCurrency()));

      int calculatedTotalSignum = calculatedTotal.signum();

      final MonetaryAmount remainder = expectedTotal.abs().subtract(calculatedTotal.abs());
      Optional<Transaction> resultTransaction = Optional.of(invoiceWorkflowDataHolder)
        .map(holder -> {
          if (remainder.isNegative()) {
            return holder.get(0);
          } else {
            return holder.get(holder.size() - 1);
          }
        })
        .map(InvoiceWorkflowDataHolder::getNewTransaction);

      resultTransaction
        .ifPresent(tr -> {
          MonetaryAmount resultReminder = tr.getTransactionType().equals(TransactionType.CREDIT) ? remainder : remainder.multiply(calculatedTotalSignum);
          tr.setAmount(Money.of(tr.getAmount(), conversion.getCurrency()).add(resultReminder).getNumber().doubleValue());
        });
    });
    return holders;
  }
}
