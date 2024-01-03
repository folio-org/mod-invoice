package org.folio.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.json.JsonObject;

public final class LoggingHelper {

  private static final Logger logger = LogManager.getLogger();

  private LoggingHelper() {
  }

  public static void logQuery(String method, RequestEntry requestEntry) {
    if (logger.isInfoEnabled()) {
      logger.info("Query for {} is [{}]", method, requestEntry.getQueryParams());
    }
  }

  public static void debugAsJson(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry).encodePrettily());
    }
  }

  public static void infoAsJson(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry).encodePrettily());
    }
  }

}
