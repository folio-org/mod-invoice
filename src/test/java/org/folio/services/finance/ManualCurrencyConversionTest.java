package org.folio.services.finance;

import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;
import static org.mockito.Mockito.doReturn;

import javax.money.MonetaryAmount;
import javax.money.convert.ConversionContext;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ExchangeRateProvider;

import org.folio.services.exchange.ManualCurrencyConversion;
import org.folio.services.exchange.ManualExchangeRateProvider;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.convert.ExchangeRateBuilder;
import org.javamoney.moneta.spi.DefaultNumberValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ManualCurrencyConversionTest {

  @Test
  public void testShouldReturnExchangeRateFromRateProvider()
  {
    //Given
    String systemCurrency = "USD";
    String toCurrency = "USD";
    ConversionQuery actQuery = ConversionQueryBuilder.of().setBaseCurrency(systemCurrency).setTermCurrency(toCurrency).set(RATE_KEY, 2d).build();
    ExchangeRate expRate = buildExchangeRate(actQuery);
    ExchangeRateProvider exchangeRateProvider = Mockito.mock(ManualExchangeRateProvider.class);
    ManualCurrencyConversion manualCurrencyConversion = new ManualCurrencyConversion(actQuery, exchangeRateProvider, ConversionContext.of());
    MonetaryAmount monetaryAmount = Money.of(20, systemCurrency);
    doReturn(expRate).when(exchangeRateProvider).getExchangeRate(actQuery);
    //When
    ExchangeRate exchangeRate = manualCurrencyConversion.getExchangeRate(monetaryAmount);
    //Then
    Assertions.assertEquals(systemCurrency, exchangeRate.getBaseCurrency().getCurrencyCode());
    Assertions.assertEquals(toCurrency, exchangeRate.getCurrency().getCurrencyCode());
    Assertions.assertEquals(new DefaultNumberValue(2d).doubleValue(), exchangeRate.getFactor().doubleValue(), 0);
  }

  @Test
  public void testShouldReturnExchangeRateProvider()
  {
    //Given
    String systemCurrency = "USD";
    String toCurrency = "USD";
    ConversionQuery actQuery = ConversionQueryBuilder.of().setBaseCurrency(systemCurrency).setTermCurrency(toCurrency).set(RATE_KEY, 2d).build();
    ExchangeRateProvider exchangeRateProvider = Mockito.mock(ManualExchangeRateProvider.class);
    ManualCurrencyConversion manualCurrencyConversion = new ManualCurrencyConversion(actQuery, exchangeRateProvider, ConversionContext.of());
    //When
    ExchangeRateProvider provider = manualCurrencyConversion.getExchangeRateProvider();
    //Then
    Assertions.assertNotNull(provider);
  }


  private ExchangeRate buildExchangeRate(ConversionQuery conversionQuery) {
    ExchangeRateBuilder builder = new ExchangeRateBuilder(ConversionContext.of());
    builder.setBase(conversionQuery.getBaseCurrency());
    builder.setTerm(conversionQuery.getCurrency());
    builder.setFactor(DefaultNumberValue.of(conversionQuery.get(RATE_KEY, Double.class)));
    return builder.build();
  }

}
