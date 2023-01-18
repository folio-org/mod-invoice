package org.folio.rest.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.rest.resource.interfaces.PostDeployVerticle;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * The class initializes event bus handlers
 */
public class InitEventBus implements PostDeployVerticle {
  private final Logger logger = LogManager.getLogger(InitEventBus.class);

  @Autowired
  @Qualifier("batchVoucherProcessHandler")
  Handler<Message<JsonObject>> batchVoucherProcessHandler;

  public InitEventBus() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(blockingCodeFuture -> {
      EventBus eb = vertx.eventBus();

      // Create consumers and assign handlers
      Promise<Void> batchVoucherPersistRegistrationHandler = Promise.promise();
      MessageConsumer<JsonObject> batchVoucherPersist = eb.localConsumer(MessageAddress.BATCH_VOUCHER_PERSIST_TOPIC.address);
      batchVoucherPersist.handler(batchVoucherProcessHandler)
        .completionHandler(batchVoucherPersistRegistrationHandler);

      batchVoucherPersistRegistrationHandler.future().onComplete(result -> {
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
