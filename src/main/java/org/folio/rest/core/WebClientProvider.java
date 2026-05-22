package org.folio.rest.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Vertx;
import io.vertx.core.http.PoolOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.experimental.UtilityClass;

/**
 * Provides a cached {@link WebClient} instance per {@link Vertx} instance.
 * The WebClient is created on the first call for a given Vertx instance and reused for all
 * subsequent calls. The {@code options} and {@code poolOptions} parameters are only applied
 * during initial creation; they are ignored if a WebClient already exists for the given
 * Vertx instance.
 */
@UtilityClass
public class WebClientProvider {

  private static final Map<Vertx, WebClient> WEB_CLIENTS = new ConcurrentHashMap<>();

  /**
   * Returns a cached {@link WebClient} for the given {@link Vertx} instance.
   * If no WebClient exists yet for the provided Vertx instance, a new one is created
   * using the supplied {@code options} and {@code poolOptions}. Otherwise, the existing
   * WebClient is returned and the {@code options} and {@code poolOptions} are ignored.
   *
   * @param vertx       the Vertx instance to associate the WebClient with
   * @param options     the WebClient options (used only on first creation)
   * @param poolOptions the connection pool options (used only on first creation)
   * @return a cached WebClient instance
   */
  public static WebClient getWebClient(Vertx vertx, WebClientOptions options, PoolOptions poolOptions) {
    return WEB_CLIENTS.computeIfAbsent(vertx, v -> WebClient.create(v, options, poolOptions));
  }
}
