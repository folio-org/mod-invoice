package org.folio.rest.impl;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.SerializationConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.json.jackson.DatabindCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ApplicationConfig;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.spring.SpringContextUtil;
import org.folio.verticles.CancelledJobConsumerVerticle;
import org.folio.verticles.DataImportConsumerVerticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.AbstractApplicationContext;

import javax.money.convert.MonetaryConversions;
import java.util.List;

/**
 * The class initializes vertx context adding spring context
 */
public class InitAPIs implements InitAPI {

  private final Logger log = LogManager.getLogger(InitAPIs.class);

  @Value("${mod.invoice.kafka.DataImportConsumerVerticle.instancesNumber:1}")
  private int dataImportConsumerVerticleNumber;

  @Value("${mod.invoice.kafka.CancelledJobConsumerVerticle.instancesNumber:1}")
  private int cancelledJobConsumerVerticleNumber;

  @Value("${dataimport.consumer.verticle.mandatory:false}")
  private boolean isConsumerVerticleMandatory;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    vertx.executeBlocking(() -> {
      SerializationConfig serializationConfig = ObjectMapperTool.getMapper().getSerializationConfig();
      DeserializationConfig deserializationConfig = ObjectMapperTool.getMapper().getDeserializationConfig();

      DatabindCodec.mapper().setConfig(serializationConfig);
      DatabindCodec.mapper().setConfig(deserializationConfig);

      SpringContextUtil.init(vertx, context, ApplicationConfig.class);
      SpringContextUtil.autowireDependencies(this, context);

      initJavaMoney();

      return deployDataImportConsumerVerticle(vertx).onComplete(result -> {
        if (result.failed() && isConsumerVerticleMandatory) {
          log.error("Failure to init API", result.cause());
          resultHandler.handle(Future.failedFuture(result.cause()));
        } else {
          resultHandler.handle(Future.succeededFuture(true));
        }
      });
    });
  }

  private Future<Void> deployDataImportConsumerVerticle(Vertx vertx) {
    AbstractApplicationContext springContext = vertx.getOrCreateContext().get("springContext");

    List<Future<?>> deploymentFutures = List.of(
      deployVerticle(vertx, springContext, DataImportConsumerVerticle.class, dataImportConsumerVerticleNumber, ThreadingModel.WORKER),
      deployVerticle(vertx, springContext, CancelledJobConsumerVerticle.class, cancelledJobConsumerVerticleNumber, ThreadingModel.EVENT_LOOP)
    );

    return Future.all(deploymentFutures).onComplete(ar -> {
        if (ar.succeeded()) {
          log.info("All consumer verticles deployed successfully.");
        } else {
          log.error("Failed to deploy one or more consumer verticles", ar.cause());
        }
      })
      .map((Void) null);
  }

  /**
   * Deploys a verticle from the Spring context with the given options.
   *
   * @param vertx          The Vert.x instance.
   * @param springContext  The Spring application context.
   * @param verticleClass  The class of the verticle to deploy.
   * @param instances      The number of instances to deploy.
   * @param threadingModel The ThreadingModel value.
   * @return A Future that completes with the deployment ID.
   */
  private Future<String> deployVerticle(Vertx vertx, AbstractApplicationContext springContext,
                                        Class<? extends AbstractVerticle> verticleClass,
                                        int instances, ThreadingModel threadingModel) {
    DeploymentOptions options = new DeploymentOptions()
      .setInstances(instances)
      .setThreadingModel(threadingModel);

    return vertx.deployVerticle(() -> springContext.getBean(verticleClass), options);
  }

  private void initJavaMoney() {
    try {
      log.info("Available currency rates providers {}", MonetaryConversions.getDefaultConversionProviderChain());
    } catch (Exception e){
      log.error("Java Money API preload failed", e);
    }
  }
}
