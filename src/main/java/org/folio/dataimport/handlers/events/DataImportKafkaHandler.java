package org.folio.dataimport.handlers.events;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.DataImportEventPayload;
import org.folio.dataimport.InvoiceWriterFactory;
import org.folio.dataimport.cache.JobProfileSnapshotCache;
import org.folio.dataimport.handlers.actions.CreateInvoiceEventHandler;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.processing.events.EventManager;
import org.folio.processing.exceptions.EventProcessingException;
import org.folio.processing.mapping.MappingManager;
import org.folio.processing.mapping.mapper.reader.record.edifact.EdifactReaderFactory;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.RestClient;
import org.folio.rest.jaxrs.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static org.folio.DataImportEventTypes.DI_ERROR;

@Component
public class DataImportKafkaHandler implements AsyncRecordHandler<String, String> {

  public static final String JOB_PROFILE_SNAPSHOT_ID_KEY = "JOB_PROFILE_SNAPSHOT_ID";
  private static final String PROFILE_SNAPSHOT_NOT_FOUND_MSG = "JobProfileSnapshot was not found by id '%s'";

  private final Logger logger = LogManager.getLogger(DataImportKafkaHandler.class);

  private final JobProfileSnapshotCache profileSnapshotCache;

  @Autowired
  public DataImportKafkaHandler(RestClient restClient, JobProfileSnapshotCache profileSnapshotCache) {
    this.profileSnapshotCache = profileSnapshotCache;
    MappingManager.registerReaderFactory(new EdifactReaderFactory());
    MappingManager.registerWriterFactory(new InvoiceWriterFactory());
    EventManager.registerEventHandler(new CreateInvoiceEventHandler(restClient));
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
    try {
      Promise<String> promise = Promise.promise();
      Event event = DatabindCodec.mapper().readValue(kafkaRecord.value(), Event.class);
      DataImportEventPayload eventPayload = Json.decodeValue(event.getEventPayload(), DataImportEventPayload.class);
      logger.info("Data import event payload has been received with event type: {}", eventPayload.getEventType());

      String profileSnapshotId = eventPayload.getContext().get(JOB_PROFILE_SNAPSHOT_ID_KEY);
      Map<String, String> okapiHeaders = retrieveOkapiHeaders(eventPayload);

      profileSnapshotCache.get(profileSnapshotId, okapiHeaders)
        .thenCompose(snapshotOptional -> snapshotOptional
          .map(profileSnapshot -> EventManager.handleEvent(eventPayload, profileSnapshot))
          .orElse(CompletableFuture.failedFuture(new EventProcessingException(format(PROFILE_SNAPSHOT_NOT_FOUND_MSG, profileSnapshotId)))))
        .whenComplete((processedPayload, throwable) -> {
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

  private Map<String, String> retrieveOkapiHeaders(DataImportEventPayload eventPayload) {
    return Map.of(RestConstants.OKAPI_URL, eventPayload.getOkapiUrl(),
      RestVerticle.OKAPI_HEADER_TENANT, eventPayload.getTenant(),
      RestVerticle.OKAPI_HEADER_TOKEN, eventPayload.getToken());
  }
}
