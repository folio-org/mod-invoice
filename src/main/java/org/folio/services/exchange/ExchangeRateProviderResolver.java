package org.folio.services.exchange;

import static org.javamoney.moneta.convert.ExchangeRateType.ECB;
import static org.javamoney.moneta.convert.ExchangeRateType.IDENTITY;
import static org.javamoney.moneta.convert.ExchangeRateType.IMF;

import java.util.Objects;
import java.util.Optional;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRateProvider;
import javax.money.convert.MonetaryConversions;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ExchangeRateProviderResolver {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  public static final String RATE_KEY = "factor";

  public ExchangeRateProvider resolve(ConversionQuery conversionQuery){
    ExchangeRateProvider exchangeRateProvider = Optional.ofNullable(conversionQuery)
            .map(query -> query.get(RATE_KEY, Double.class))
            .filter(Objects::nonNull)
            .map(rate -> (ExchangeRateProvider) new ManualExchangeRateProvider())
            .orElse(MonetaryConversions.getExchangeRateProvider(IDENTITY, ECB, IMF));
    logger.debug("Created ExchangeRateProvider name: {}", exchangeRateProvider.getContext().getProviderName());
    return exchangeRateProvider;
  }
}
