package org.folio.services.caches;

import static org.folio.TestUtils.setInternalState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.folio.CopilotGenerated;
import org.folio.rest.core.models.RequestContext;
import org.folio.services.settings.CommonSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.benmanes.caffeine.cache.AsyncCache;

import io.vertx.core.Future;

@CopilotGenerated(partiallyGenerated = true, model = "Claude Sonnet 4")
@ExtendWith(MockitoExtension.class)
class CommonSettingsCacheTest {

  @InjectMocks
  private CommonSettingsCache commonSettingsCache;
  @Mock
  private CommonSettingsService commonSettingsService;
  @Mock
  private AsyncCache<String, String> voucherNumberPrefixCache;
  @Mock
  private AsyncCache<String, String> systemCurrencyCache;

  @BeforeEach
  void setUp() {
    setInternalState(commonSettingsCache, "voucherNumberPrefixCache", voucherNumberPrefixCache);
    setInternalState(commonSettingsCache, "systemCurrencyCache", systemCurrencyCache);
  }

  @Test
  void voucherNumberPrefixCachedValueReturned() {
    RequestContext requestContext = mock(RequestContext.class);

    when(voucherNumberPrefixCache.get(anyString(), any(BiFunction.class)))
      .thenReturn(CompletableFuture.completedFuture("INV"));

    Future<String> result = commonSettingsCache.getVoucherNumberPrefix(requestContext);

    assertEquals("INV", result.result());
  }

  @Test
  void systemCurrencyCachedValueReturned() {
    RequestContext requestContext = mock(RequestContext.class);

    when(systemCurrencyCache.get(anyString(), any(BiFunction.class)))
      .thenReturn(CompletableFuture.completedFuture("EUR"));

    Future<String> result = commonSettingsCache.getSystemCurrency(requestContext);

    assertEquals("EUR", result.result());
  }

  @Test
  void voucherNumberPrefixServiceCallFailureHandled() {
    RequestContext requestContext = mock(RequestContext.class);

    when(voucherNumberPrefixCache.get(anyString(), any(BiFunction.class)))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Service error")));

    Future<String> result = commonSettingsCache.getVoucherNumberPrefix(requestContext);

    assertTrue(result.failed());
  }

  @Test
  void systemCurrencyServiceCallFailureHandled() {
    RequestContext requestContext = mock(RequestContext.class);

    when(systemCurrencyCache.get(anyString(), any(BiFunction.class)))
      .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Service error")));

    Future<String> result = commonSettingsCache.getSystemCurrency(requestContext);

    assertTrue(result.failed());
  }

}
