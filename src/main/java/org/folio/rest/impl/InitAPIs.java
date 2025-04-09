package org.folio.rest.impl;

import javax.money.convert.MonetaryConversions;

import lombok.extern.log4j.Log4j2;
import org.folio.config.ApplicationConfig;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.spring.SpringContextUtil;
import org.folio.verticles.DataImportConsumerVerticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.AbstractApplicationContext;

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

/**
 * The class initializes vertx context adding spring context
 */
@Log4j2
public class InitAPIs implements InitAPI {

  @Value("${mod.invoice.kafka.DataImportConsumerVerticle.instancesNumber:1}")
  private int dataImportConsumerVerticleNumber;

  @Value("${dataimport.consumer.verticle.mandatory:false}")
  private boolean isConsumerVerticleMandatory;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(handler -> {
      SerializationConfig serializationConfig = ObjectMapperTool.getMapper().getSerializationConfig();
      DeserializationConfig deserializationConfig = ObjectMapperTool.getMapper().getDeserializationConfig();

      DatabindCodec.mapper().setConfig(serializationConfig);
      DatabindCodec.prettyMapper().setConfig(serializationConfig);
      DatabindCodec.mapper().setConfig(deserializationConfig);
      DatabindCodec.prettyMapper().setConfig(deserializationConfig);

      SpringContextUtil.init(vertx, context, ApplicationConfig.class);
      SpringContextUtil.autowireDependencies(this, context);

      initJavaMoney();

      deployDataImportConsumerVerticle(vertx).onComplete(ar -> {
        if (ar.failed() && isConsumerVerticleMandatory) {
          handler.fail(ar.cause());
        } else {
          handler.complete();
        }
      });
    },
    result -> {
      if (result.succeeded()) {
        resultHandler.handle(Future.succeededFuture(true));
      } else {
        log.error("Failure to init API", result.cause());
        resultHandler.handle(Future.failedFuture(result.cause()));
      }
    });
  }

  private Future<String> deployDataImportConsumerVerticle(Vertx vertx) {
    Promise<String> promise = Promise.promise();
    AbstractApplicationContext springContext = vertx.getOrCreateContext().get("springContext");

    DeploymentOptions deploymentOptions = new DeploymentOptions()
      .setInstances(dataImportConsumerVerticleNumber)
      .setWorker(true);
    vertx.deployVerticle(() -> springContext.getBean(DataImportConsumerVerticle.class), deploymentOptions, promise);

    return promise.future()
      .onSuccess(ar -> log.info("DataImportConsumerVerticle verticle was successfully started"))
      .onFailure(e -> log.error("DataImportConsumerVerticle verticle was not successfully started", e));
  }

  private void initJavaMoney() {
    try {
      log.info("Available currency rates providers {}", MonetaryConversions.getDefaultConversionProviderChain());
    } catch (Exception e){
      log.error("Java Money API preload failed", e);
    }
  }
}
