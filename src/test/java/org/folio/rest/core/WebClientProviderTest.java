package org.folio.rest.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.vertx.core.Vertx;
import io.vertx.core.http.PoolOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class WebClientProviderTest {

  @Test
  void shouldReturnNonNullWebClient(Vertx vertx) {
    var options = new WebClientOptions();
    var poolOptions = new PoolOptions();

    var client = WebClientProvider.getWebClient(vertx, options, poolOptions);

    assertNotNull(client);
  }

  @Test
  void shouldReturnSameInstanceForSameVertx(Vertx vertx) {
    var options = new WebClientOptions();
    var poolOptions = new PoolOptions();

    var client1 = WebClientProvider.getWebClient(vertx, options, poolOptions);
    var client2 = WebClientProvider.getWebClient(vertx, options, poolOptions);

    assertSame(client1, client2);
  }

  @Test
  void shouldReturnDifferentInstancesForDifferentVertx() {
    var vertx1 = Vertx.vertx();
    var vertx2 = Vertx.vertx();

    try {
      var options = new WebClientOptions();
      var poolOptions = new PoolOptions();

      var client1 = WebClientProvider.getWebClient(vertx1, options, poolOptions);
      var client2 = WebClientProvider.getWebClient(vertx2, options, poolOptions);

      assertNotNull(client1);
      assertNotNull(client2);
      assertNotSame(client1, client2);
    } finally {
      vertx1.close();
      vertx2.close();
    }
  }
}
