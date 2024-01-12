package org.folio.common.dao;

import io.vertx.core.Vertx;
import org.folio.rest.persist.PostgresClient;

public class PostgresClientFactory {

  private Vertx vertx;

  public PostgresClientFactory(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Creates instance of Postgres Client
   *
   * @param tenantId tenant id
   * @return Postgres Client
   */
  public PostgresClient createInstance(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId);
  }
}