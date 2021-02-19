package org.folio.rest.impl;

import org.folio.config.ApplicationConfig;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.spring.SpringContextUtil;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.SerializationConfig;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.jackson.DatabindCodec;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.folio.verticles.DataImportConsumerVerticle;
import org.springframework.beans.factory.annotation.Value;

/**
 * The class initializes vertx context adding spring context
 */
public class InitAPIs implements InitAPI {
  private final Logger logger = LogManager.getLogger(InitAPIs.class);

  @Value("${mod.invoice.kafka.DataImportConsumerVerticle.instancesNumber:1}")
  private int dataImportConsumerVerticleNumber;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.<String>executeBlocking(
      handler -> {
        SerializationConfig serializationConfig = ObjectMapperTool.getMapper().getSerializationConfig();
        DeserializationConfig deserializationConfig = ObjectMapperTool.getMapper().getDeserializationConfig();

        DatabindCodec.mapper().setConfig(serializationConfig);
        DatabindCodec.prettyMapper().setConfig(serializationConfig);
        DatabindCodec.mapper().setConfig(deserializationConfig);
        DatabindCodec.prettyMapper().setConfig(deserializationConfig);

        SpringContextUtil.init(vertx, context, ApplicationConfig.class);
        SpringContextUtil.autowireDependencies(this, context);

        deployDataImportConsumerVerticle(vertx)
          .onComplete(ar -> handler.handle(ar));
      },
      result -> {
        if (result.succeeded()) {
          resultHandler.handle(Future.succeededFuture(true));
        } else {
          logger.error("Failure to init API", result.cause());
          resultHandler.handle(Future.failedFuture(result.cause()));
        }
      });
  }

  private Future<String> deployDataImportConsumerVerticle(Vertx vertx) {
    Promise<String> promise = Promise.promise();
    // todo: fix it
    DataImportConsumerVerticle.setSpringGlobalContext(vertx.getOrCreateContext().get("springContext"));

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setInstances(dataImportConsumerVerticleNumber)
      .setWorker(true);
    vertx.deployVerticle(DataImportConsumerVerticle.class.getName(), deploymentOptions, promise);
    return promise.future();
  }
}
