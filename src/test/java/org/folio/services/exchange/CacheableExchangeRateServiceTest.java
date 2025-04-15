package org.folio.services.exchange;

import com.github.benmanes.caffeine.cache.AsyncCache;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.CopilotGenerated;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.acq.model.finance.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
@CopilotGenerated(partiallyGenerated = true, model = "o3-mini")
public class CacheableExchangeRateServiceTest {

  private RestClient restClient;
  private RequestContext requestContext;
  private CacheableExchangeRateService service;

  @BeforeEach
  void setUp() {
    restClient = mock(RestClient.class);
    requestContext = mock(RequestContext.class);
    service = new CacheableExchangeRateService(restClient, 1L);
  }

  @Test
  void testSameCurrencyReturnsDefaultRate(VertxTestContext testContext) {
    var from = "USD";
    var to = "USD";

    service.getExchangeRate(from, to, null, requestContext)
      .onComplete(ar -> testContext.verify(() -> {
        var rate = ar.result();
        assertEquals(from, rate.getFrom());
        assertEquals(to, rate.getTo());
        assertEquals(1d, rate.getExchangeRate());
        testContext.completeNow();
      }));
  }

  @Test
  void testCustomExchangeRateReturnsProvidedRate(VertxTestContext testContext) {
    var from = "USD";
    var to = "EUR";
    var customExchangeRate = 2d;

    service.getExchangeRate(from, to, customExchangeRate, requestContext)
      .onComplete(ar -> testContext.verify(() -> {
        var rate = ar.result();
        assertEquals(from, rate.getFrom());
        assertEquals(to, rate.getTo());
        assertEquals(customExchangeRate, rate.getExchangeRate());
        testContext.completeNow();
      }));
  }

  @Test
  void testRemoteExchangeRateCall(VertxTestContext testContext) {
    var from = "USD";
    var to = "EUR";
    var remoteRate = new ExchangeRate().withFrom(from).withTo(to).withExchangeRate(1.5);

    when(restClient.get(any(org.folio.rest.core.models.RequestEntry.class), eq(ExchangeRate.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(remoteRate));

    service.getExchangeRate(from, to, null, requestContext)
      .onComplete(ar -> testContext.verify(() -> {
        var rate = ar.result();
        assertEquals(from, rate.getFrom());
        assertEquals(to, rate.getTo());
        assertEquals(1.5, rate.getExchangeRate());
        testContext.completeNow();
      }));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetExchangeRateHandlesException(VertxTestContext testContext) throws IllegalAccessException, NoSuchFieldException {
    var from = "USD";
    var to = "EUR";
    var customException = new RuntimeException("Test Exception");
    var requestContext = mock(RequestContext.class);
    var restClient = mock(RestClient.class);
    var service = new CacheableExchangeRateService(restClient, 1L);

    // Create and inject a mocked asyncCache that throws an exception when get is called.
    var asyncCacheMock = mock(AsyncCache.class);
    when(asyncCacheMock.get(any(String.class), any(BiFunction.class))).thenThrow(customException);

    // Inject the mocked asyncCache into the service instance via reflection.
    var field = CacheableExchangeRateService.class.getDeclaredField("asyncCache");
    field.setAccessible(true);
    field.set(service, asyncCacheMock);

    var future = service.getExchangeRate(from, to, null, requestContext);
    future.onComplete(ar -> testContext.verify(() -> {
      assertTrue(ar.failed());
      assertEquals(customException, ar.cause());
      testContext.completeNow();
    }));
  }
}
