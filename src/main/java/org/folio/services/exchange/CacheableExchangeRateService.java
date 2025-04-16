package org.folio.services.exchange;

import com.github.benmanes.caffeine.cache.AsyncCache;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.folio.invoices.utils.ResourcePathResolver.EXCHANGE_RATE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.utils.CacheUtils.buildAsyncCache;

@Log4j2
@Service
public class CacheableExchangeRateService {

  private static final String FROM = "from";
  private static final String TO = "to";

  @Value("${mod.invoice.exchange-rate.expiration.time.seconds:60}")
  private long cacheExpirationTime;

  private final RestClient restClient;
  private AsyncCache<String, Optional<ExchangeRate>> asyncCache;

  public CacheableExchangeRateService(RestClient restClient) {
    this.restClient = restClient;
  }

  @PostConstruct
  void init() {
    this.asyncCache = buildAsyncCache(Vertx.currentContext(), cacheExpirationTime);
  }

  public Future<ExchangeRate> getExchangeRate(String from, String to, Number customExchangeRate, RequestContext requestContext) {
    if (StringUtils.equals(from, to)) {
      return Future.succeededFuture(createDefaultExchangeRate(from, to, 1d));
    }
    if (Objects.nonNull(customExchangeRate)) {
      log.info("getExchangeRate:: Retrieving an exchange rate, {} -> {}, customExchangeRate: {}", from, to, customExchangeRate);
      return Future.succeededFuture(createDefaultExchangeRate(from, to, customExchangeRate));
    }
    try {
      var cacheKey = String.format("%s-%s", from, to);
      return Future.fromCompletionStage(asyncCache.get(cacheKey, (key, executor) -> getExchangeRateFromRemote(from, to, requestContext)))
        .compose(exchangeRateOptional -> exchangeRateOptional.map(exchangeRate -> {
            log.info("getExchangeRate:: Retrieving an exchange rate, {} -> {}, exchangeRate: {}", from, to, exchangeRate.getExchangeRate());
            return Future.succeededFuture(exchangeRate);
          })
          .orElseGet(() -> Future.failedFuture("Cannot retrieve exchange rate from API")));
    } catch (Exception e) {
      log.error("Error when retrieving cacheable exchange rate", e);
      return Future.failedFuture(e);
    }
  }

  private ExchangeRate createDefaultExchangeRate(String from, String to, Number exchangeRateValue) {
    return new ExchangeRate().withFrom(from).withTo(to).withExchangeRate(exchangeRateValue.doubleValue());
  }

  private CompletableFuture<Optional<ExchangeRate>> getExchangeRateFromRemote(String from, String to, RequestContext requestContext) {
    var requestEntry = new RequestEntry(resourcesPath(EXCHANGE_RATE))
      .withQueryParameter(FROM, from)
      .withQueryParameter(TO, to);
    return restClient.get(requestEntry, ExchangeRate.class, requestContext)
      .map(Optional::ofNullable)
      .toCompletionStage().toCompletableFuture();
  }
}
