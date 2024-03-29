package org.folio.verticles;

import static org.folio.DataImportEventTypes.DI_INCOMING_EDIFACT_RECORD_PARSED;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.GlobalLoadSensor;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.KafkaConsumerWrapper;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.kafka.SubscriptionDefinition;
import org.folio.processing.events.EventManager;
import org.folio.processing.events.utils.PomReaderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataImportConsumerVerticle extends AbstractVerticle {

  private static final Logger logger = LogManager.getLogger(DataImportConsumerVerticle.class);
  private static final GlobalLoadSensor GLOBAL_LOAD_SENSOR = new GlobalLoadSensor();

  @Value("${mod.invoice.kafka.DataImportConsumer.loadLimit:5}")
  private int loadLimit;
  @Value("${mod.invoice.kafka.DataImportConsumerVerticle.maxDistributionNumber:100}")
  private int maxDistributionNumber;

  private final AbstractApplicationContext springContext;
  private final KafkaConfig kafkaConfig;
  private final AsyncRecordHandler<String, String> dataImportKafkaHandler;
  private KafkaConsumerWrapper<String, String> consumerWrapper;

  @Autowired
  public DataImportConsumerVerticle(KafkaConfig kafkaConfig, AsyncRecordHandler<String, String> dataImportKafkaHandler,
                                    AbstractApplicationContext springContext) {
    this.springContext = springContext;
    this.kafkaConfig = kafkaConfig;
    this.dataImportKafkaHandler = dataImportKafkaHandler;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    context.put("springContext", springContext);

    logger.info("Kafka config: {}", kafkaConfig);
    EventManager.registerKafkaEventPublisher(kafkaConfig, vertx, maxDistributionNumber);

    SubscriptionDefinition subscriptionDefinition = KafkaTopicNameHelper.createSubscriptionDefinition(kafkaConfig.getEnvId(),
      KafkaTopicNameHelper.getDefaultNameSpace(), DI_INCOMING_EDIFACT_RECORD_PARSED.value());

    consumerWrapper = KafkaConsumerWrapper.<String, String>builder()
      .context(context)
      .vertx(vertx)
      .kafkaConfig(kafkaConfig)
      .loadLimit(loadLimit)
      .globalLoadSensor(GLOBAL_LOAD_SENSOR)
      .subscriptionDefinition(subscriptionDefinition)
      .build();

    consumerWrapper.start(dataImportKafkaHandler, PomReaderUtil.INSTANCE.constructModuleVersionAndVersion(PomReaderUtil.INSTANCE.getModuleName(), PomReaderUtil.INSTANCE.getVersion()))
      .onComplete(ar -> startPromise.handle(ar));
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    consumerWrapper.stop().onComplete(stopPromise::handle);
  }

}
