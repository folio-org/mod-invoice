package org.folio.dataimport.handlers.events;

import static org.folio.DataImportEventTypes.DI_ERROR;

import java.io.IOException;

import io.vertx.kafka.client.producer.KafkaHeader;
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
import org.folio.rest.RestVerticle;
import org.folio.rest.core.RestClient;
import org.folio.rest.jaxrs.model.Event;
import org.folio.utils.UserPermissionsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

@Component
public class DataImportKafkaHandler implements AsyncRecordHandler<String, String> {

  public static final String DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS = "data-import-payload-okapi-permissions";
  public static final String DATA_IMPORT_PAYLOAD_OKAPI_USER_ID = "data-import-payload-okapi-user-id";

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
      populateContextWithOkapiUserAndPerms(kafkaRecord, eventPayload);

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

  private void populateContextWithOkapiUserAndPerms(KafkaConsumerRecord<String, String> kafkaRecord,
                                                    DataImportEventPayload eventPayload) {
    for (KafkaHeader header: kafkaRecord.headers()) {
      if (UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS.equalsIgnoreCase(header.key())) {
        String permissions = header.value().toString();
        eventPayload.getContext().put(DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS, permissions);
      } else if (RestVerticle.OKAPI_USERID_HEADER.equalsIgnoreCase(header.key())) {
        String userId = header.value().toString();
        eventPayload.getContext().put(DATA_IMPORT_PAYLOAD_OKAPI_USER_ID, userId);
      }
    }
  }
}
