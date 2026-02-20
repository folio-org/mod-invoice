package org.folio.services.caches;

import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_STORAGE_SETTINGS;
import static org.folio.invoices.utils.ResourcePathResolver.LOCALE_SETTINGS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
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
  @Value("${mod.invoice.cache.settings-entries.bypass-cache:false}")
  private boolean byPassCache;

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
    return cacheData(resourcesPath(INVOICE_STORAGE_SETTINGS), null,
      voucherNumberPrefixCache, commonSettingsService::getVoucherNumberPrefix, requestContext);
  }

  public Future<String> getSystemCurrency(RequestContext requestContext) {
    return cacheData(resourcesPath(LOCALE_SETTINGS), null,
      systemCurrencyCache, commonSettingsService::getSystemCurrency, requestContext);
  }

  private <T> Future<T> cacheData(String url, String query, AsyncCache<String, T> cache,
                                  BiFunction<RequestEntry, RequestContext, Future<T>> configExtractor,
                                  RequestContext requestContext) {
    var requestEntry = new RequestEntry(url).withQuery(query).withOffset(0).withLimit(Integer.MAX_VALUE);
    var cacheKey = buildUniqueKey(requestEntry, requestContext);
    log.debug("loadSettingsData:: Loading setting data, url: '{}', query: '{}', bypass-cache mode: '{}'", url, query, byPassCache);
    if (byPassCache) {
      return extractData(configExtractor, requestContext, requestEntry);
    }
    return Future.fromCompletionStage(cache.get(cacheKey, (key, executor) ->
      extractData(configExtractor, requestContext, requestEntry)
        .toCompletionStage().toCompletableFuture()));
  }

  private <T> Future<T> extractData(BiFunction<RequestEntry, RequestContext, Future<T>> extractor,
                                    RequestContext requestContext, RequestEntry requestEntry) {
    var tenantId = TenantTool.tenantId(requestContext.getHeaders());
    return extractor.apply(requestEntry, requestContext)
      .onFailure(t -> log.error("Error loading configuration, tenantId: '{}'", tenantId, t));
  }

  private String buildUniqueKey(RequestEntry requestEntry, RequestContext requestContext) {
    var endpoint = requestEntry.buildEndpoint();
    var tenantId = TenantTool.tenantId(requestContext.getHeaders());
    var userId = HelperUtils.getCurrentUserId(requestContext.getHeaders());
    return String.format(UNIQUE_CACHE_KEY_PATTERN, tenantId, userId, endpoint);
  }

}
