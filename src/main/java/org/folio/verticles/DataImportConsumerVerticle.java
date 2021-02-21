package org.folio.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.processing.events.EventManager;
import org.folio.spring.SpringContextUtil;
import org.folio.util.pubsub.PubSubClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.AbstractApplicationContext;

public class DataImportConsumerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(DataImportConsumerVerticle.class);
  private static final GlobalLoadSensor GLOBAL_LOAD_SENSOR = new GlobalLoadSensor();
  public static final String EDIFACT_RECORD_CREATED_EVENT = "DI_EDIFACT_RECORD_CREATED";

  private static AbstractApplicationContext springContext;

  @Value("${mod.invoice.kafka.DataImportConsumer.loadLimit:5}")
  private int loadLimit;
  @Value("${mod.invoice.kafka.DataImportConsumerVerticle.maxDistributionNumber:100}")
  private int maxDistributionNumber;
  @Autowired
  private KafkaConfig kafkaConfig;
  @Autowired
  private AsyncRecordHandler<String, String> dataImportKafkaHandler;
  private KafkaConsumerWrapper<String, String> consumerWrapper;

  @Override
  public void start(Promise<Void> startPromise) {
    context.put("springContext", springContext); // todo: fix it
    SpringContextUtil.autowireDependencies(this, context);

    LOGGER.debug("Kafka config: {}", kafkaConfig);
    EventManager.registerKafkaEventPublisher(kafkaConfig, vertx, maxDistributionNumber);

    SubscriptionDefinition subscriptionDefinition = KafkaTopicNameHelper.createSubscriptionDefinition(kafkaConfig.getEnvId(),
      KafkaTopicNameHelper.getDefaultNameSpace(), EDIFACT_RECORD_CREATED_EVENT);

    consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(GLOBAL_LOAD_SENSOR)
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    consumerWrapper.start(dataImportKafkaHandler, PubSubClientUtils.constructModuleName())
      .onComplete(ar -> startPromise.handle(ar));
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    consumerWrapper.stop().onComplete(stopPromise::handle);
  }

  public static void setSpringGlobalContext(AbstractApplicationContext springContext) {
    DataImportConsumerVerticle.springContext = springContext;
  }
}
