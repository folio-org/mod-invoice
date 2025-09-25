package org.folio.utils;

import lombok.experimental.UtilityClass;
import org.javamoney.moneta.Money;
import javax.money.CurrencyUnit;

@UtilityClass
public final class MoneyUtils {

  public static Double sumMoney(Double addend, Double amount, CurrencyUnit currency) {
    return Money.of(addend, currency)
      .add(Money.of(amount, currency))
      .getNumber()
      .doubleValue();
  }
}
