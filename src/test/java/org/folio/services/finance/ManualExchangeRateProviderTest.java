package org.folio.services.finance;

import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;

import javax.money.convert.ConversionContext;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ExchangeRateProvider;

import org.folio.services.exchange.ManualExchangeRateProvider;
import org.javamoney.moneta.convert.ExchangeRateBuilder;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ManualExchangeRateProviderTest {
  @Test
  public void testShouldReturnExchangeRateFromRateProvider()
  {
    //Given
    String systemCurrency = "USD";
    String toCurrency = "USD";
    ConversionQuery actQuery = ConversionQueryBuilder.of().setBaseCurrency(systemCurrency).setTermCurrency(toCurrency).set(RATE_KEY, 2d).build();
    ExchangeRate expRate = buildExchangeRate(actQuery);
    ExchangeRateProvider exchangeRateProvider = new ManualExchangeRateProvider();
    //When
    ExchangeRate exchangeRate = exchangeRateProvider.getExchangeRate(actQuery);
    //Then
    Assertions.assertEquals(systemCurrency, exchangeRate.getBaseCurrency().getCurrencyCode());
    Assertions.assertEquals(toCurrency, exchangeRate.getCurrency().getCurrencyCode());
    Assertions.assertEquals(new DefaultNumberValue(2d).doubleValue(), exchangeRate.getFactor().doubleValue(), 0);
  }

  private ExchangeRate buildExchangeRate(ConversionQuery conversionQuery) {
    ExchangeRateBuilder builder = new ExchangeRateBuilder(ConversionContext.of());
    builder.setBase(conversionQuery.getBaseCurrency());
    builder.setTerm(conversionQuery.getCurrency());
    builder.setFactor(DefaultNumberValue.of(conversionQuery.get(RATE_KEY, Double.class)));
    return builder.build();
  }
}
