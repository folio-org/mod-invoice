package org.folio.rest.impl;

import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.rest.resource.interfaces.PostDeployVerticle;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * The class initializes event bus handlers
 */
public class InitEventBus implements PostDeployVerticle {
  private final Logger logger = LoggerFactory.getLogger(InitEventBus.class);

  @Autowired
  @Qualifier("invoiceSummaryHandler")
  Handler<Message<JsonObject>> orderStatusHandler;

  public InitEventBus() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(blockingCodeFuture -> {
      EventBus eb = vertx.eventBus();

      // Create consumers and assign handlers
      Future<Void> invoiceSummaryRegistrationHandler = Future.future();

      MessageConsumer<JsonObject> invoiceSummaryConsumer = eb.localConsumer(MessageAddress.INVOICE_TOTALS.address);
      invoiceSummaryConsumer.handler(orderStatusHandler)
        .completionHandler(invoiceSummaryRegistrationHandler);

      invoiceSummaryRegistrationHandler.setHandler(result -> {
        if (result.succeeded()) {
          blockingCodeFuture.complete();
        } else {
          blockingCodeFuture.fail(result.cause());
        }
      });
    }, result -> {
      if (result.succeeded()) {
        resultHandler.handle(Future.succeededFuture(true));
      } else {
        logger.error(result.cause());
        resultHandler.handle(Future.failedFuture(result.cause()));
      }
    });
  }
}
