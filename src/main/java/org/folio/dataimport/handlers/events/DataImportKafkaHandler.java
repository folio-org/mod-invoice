package org.folio.dataimport.handlers.events;

import static org.folio.DataImportEventTypes.DI_ERROR;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.DataImportEventPayload;
import org.folio.dataimport.InvoiceWriterFactory;
import org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.processing.events.EventManager;
import org.folio.processing.events.utils.ZIPArchiver;
import org.folio.processing.mapping.MappingManager;
import org.folio.processing.mapping.mapper.reader.record.edifact.EdifactReaderFactory;
import org.folio.rest.core.RestClient;
import org.folio.rest.jaxrs.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

@Component
public class DataImportKafkaHandler implements AsyncRecordHandler<String, String> {

  private final Logger logger = LogManager.getLogger(DataImportKafkaHandler.class);

  @Autowired
  public DataImportKafkaHandler(RestClient restClient) {
    MappingManager.registerReaderFactory(new EdifactReaderFactory());
    MappingManager.registerWriterFactory(new InvoiceWriterFactory());
    EventManager.registerEventHandler(new CreateInvoiceEventHandler(restClient));
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      Promise<String> promise = Promise.promise();
      Event event = DatabindCodec.mapper().readValue(kafkaRecord.value(), Event.class);
      DataImportEventPayload eventPayload = new JsonObject(ZIPArchiver.unzip(event.getEventPayload())).mapTo(DataImportEventPayload.class);
      logger.info("Data import event payload has been received with event type: {}", eventPayload.getEventType());

      EventManager.handleEvent(eventPayload).whenComplete((processedPayload, throwable) -> {
        if (throwable != null) {
          promise.fail(throwable);
        } else if (DI_ERROR.value().equals(processedPayload.getEventType())) {
          promise.fail("Failed to process data import event payload");
        } else {
          promise.complete(kafkaRecord.key());
        }
      });
      return promise.future();
    } catch (IOException e) {
      logger.error("Failed to process data import kafka record from topic {}", kafkaRecord.topic(), e);
      return Future.failedFuture(e);
    }
  }
}
