package org.folio.services.finance;

import static org.folio.services.exchange.CentralExchangeRateProvider.RATE_KEY;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ExchangeRateProvider;

import org.folio.services.exchange.CentralExchangeRateProvider;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CentralExchangeRateProviderTest {

  @Test
  public void testShouldReturnExchangeRateFromRateProvider() {
    // Given
    String systemCurrency = "USD";
    String toCurrency = "USD";
    ConversionQuery actQuery = ConversionQueryBuilder.of().setBaseCurrency(systemCurrency).setTermCurrency(toCurrency).set(RATE_KEY, 2d).build();
    ExchangeRateProvider exchangeRateProvider = new CentralExchangeRateProvider();

    // When
    ExchangeRate exchangeRate = exchangeRateProvider.getExchangeRate(actQuery);

    // Then
    Assertions.assertEquals(systemCurrency, exchangeRate.getBaseCurrency().getCurrencyCode());
    Assertions.assertEquals(toCurrency, exchangeRate.getCurrency().getCurrencyCode());
    Assertions.assertEquals(new DefaultNumberValue(2d).doubleValue(), exchangeRate.getFactor().doubleValue(), 0);
  }
}
