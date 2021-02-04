package org.folio.services.exchange;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.util.Optional;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRateProvider;
import org.folio.rest.core.models.RequestContext;

public class ExchangeRateProviderResolver {
  private final Logger logger = LogManager.getLogger(this.getClass());
  public static final String RATE_KEY = "factor";

  public ExchangeRateProvider resolve(ConversionQuery conversionQuery, RequestContext requestContext){
    ExchangeRateProvider exchangeRateProvider = Optional.ofNullable(conversionQuery)
            .map(query -> query.get(RATE_KEY, Double.class))
            .map(rate -> (ExchangeRateProvider) new ManualExchangeRateProvider())
            .orElseGet(() -> new FinanceApiExchangeRateProvider(requestContext));
    logger.debug("Created ExchangeRateProvider name: {}", exchangeRateProvider.getContext().getProviderName());
    return exchangeRateProvider;
  }
}
