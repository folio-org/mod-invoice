package org.folio.services.caches;

import static org.folio.invoices.utils.ResourcePathResolver.CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.SETTINGS_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.services.settings.CommonSettingsService.SETTINGS_QUERY;
import static org.folio.services.settings.CommonSettingsService.VOUCHER_NUMBER_PREFIX_CONFIG_QUERY;
import static org.folio.utils.CacheUtils.buildAsyncCache;

import java.util.function.BiFunction;

import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.settings.CommonSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.AsyncCache;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@RequiredArgsConstructor
public class CommonSettingsCache {

  private static final String UNIQUE_CACHE_KEY_PATTERN = "%s_%s_%s";

  @Value("${mod.invoice.cache.settings-entries.expiration-time.seconds:30}")
  private long cacheExpirationTime;

  private final CommonSettingsService commonSettingsService;
  private AsyncCache<String, String> voucherNumberPrefixCache;
  private AsyncCache<String, String> systemCurrencyCache;

  @PostConstruct
  void init() {
    var context = Vertx.currentContext();
    this.voucherNumberPrefixCache = buildAsyncCache(context, cacheExpirationTime);
    this.systemCurrencyCache = buildAsyncCache(context, cacheExpirationTime);
  }

  public Future<String> getVoucherNumberPrefix(RequestContext requestContext) {
    return loadSettingsData(requestContext, resourcesPath(CONFIGURATION_ENTRIES), VOUCHER_NUMBER_PREFIX_CONFIG_QUERY,
      voucherNumberPrefixCache, commonSettingsService::getVoucherNumberPrefix);
  }

  public Future<String> getSystemCurrency(RequestContext requestContext) {
    return loadSettingsData(requestContext, resourcesPath(SETTINGS_ENTRIES), SETTINGS_QUERY,
      systemCurrencyCache, commonSettingsService::getSystemCurrency);
  }

  private <T> Future<T> loadSettingsData(RequestContext requestContext,
                                         String entriesUrl, String query,
                                         AsyncCache<String, T> cache,
                                         BiFunction<RequestEntry, RequestContext, Future<T>> configExtractor) {
    var requestEntry = new RequestEntry(entriesUrl).withQuery(query).withOffset(0).withLimit(Integer.MAX_VALUE);
    var cacheKey = buildUniqueKey(requestEntry, requestContext);
    return Future.fromCompletionStage(cache.get(cacheKey, (key, executor) ->
      configExtractor.apply(requestEntry, requestContext)
        .onFailure(t -> log.error("Error loading tenant configuration, tenantId: '{}'", TenantTool.tenantId(requestContext.getHeaders()), t))
        .toCompletionStage().toCompletableFuture()));
  }

  private String buildUniqueKey(RequestEntry requestEntry, RequestContext requestContext) {
    var endpoint = requestEntry.buildEndpoint();
    var tenantId = TenantTool.tenantId(requestContext.getHeaders());
    var userId = HelperUtils.getCurrentUserId(requestContext.getHeaders());
    return String.format(UNIQUE_CACHE_KEY_PATTERN, tenantId, userId, endpoint);
  }

}
