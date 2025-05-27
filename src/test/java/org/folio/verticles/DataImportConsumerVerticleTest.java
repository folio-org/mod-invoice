package org.folio.verticles;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ApiTestSuite.KAFKA_ENV_VALUE;
import static org.folio.ApiTestSuite.observeTopic;
import static org.folio.ApiTestSuite.createKafkaProducer;
import static org.folio.DataImportEventTypes.DI_COMPLETED;
import static org.folio.DataImportEventTypes.DI_INCOMING_EDIFACT_RECORD_PARSED;
import static org.folio.DataImportEventTypes.DI_ERROR;
import static org.folio.dataimport.handlers.events.DataImportKafkaHandler.JOB_PROFILE_SNAPSHOT_ID_KEY;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.EntityType.INVOICE;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.junit5.VertxExtension;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.ActionProfile;
import org.folio.ApiTestSuite;
import org.folio.DataImportEventPayload;
import org.folio.JobProfile;
import org.folio.MappingProfile;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.processing.events.EventManager;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.processing.exceptions.EventProcessingException;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class DataImportConsumerVerticleTest extends ApiTestBase {

  private static final String JOB_PROFILE_SNAPSHOTS_MOCK = "jobProfileSnapshots";
  private static final String OKAPI_URL = "http://localhost:" + ApiTestSuite.mockPort;
  private static final String TENANT_ID = "diku";
  private static final String TOKEN = "test-token";
  private static final String RECORD_ID_HEADER = "recordId";

  private JobProfile jobProfile = new JobProfile()
    .withId(UUID.randomUUID().toString())
    .withName("Create invoice")
    .withDataType(JobProfile.DataType.EDIFACT);
  private ActionProfile actionProfile = new ActionProfile()
    .withId(UUID.randomUUID().toString())
    .withAction(CREATE)
    .withFolioRecord(ActionProfile.FolioRecord.INVOICE);
  private MappingProfile mappingProfile = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE);

  private ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper()
    .withId(UUID.randomUUID().toString())
    .withProfileId(jobProfile.getId())
    .withContentType(JOB_PROFILE)
    .withContent(JsonObject.mapFrom(jobProfile).getMap())
    .withChildSnapshotWrappers(Collections.singletonList(
      new ProfileSnapshotWrapper()
        .withId(UUID.randomUUID().toString())
        .withProfileId(actionProfile.getId())
        .withContentType(ACTION_PROFILE)
        .withContent(JsonObject.mapFrom(actionProfile).getMap())
        .withChildSnapshotWrappers(Collections.singletonList(
          new ProfileSnapshotWrapper()
            .withId(UUID.randomUUID().toString())
            .withProfileId(mappingProfile.getId())
            .withContentType(MAPPING_PROFILE)
            .withContent(JsonObject.mapFrom(mappingProfile).getMap())))));

  @BeforeEach
  public void setUp() {
    EventManager.clearEventHandlers();
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, profileSnapshotWrapper);
  }

  @Test
  public void shouldPublishDiCompletedEventWhenProcessingCoreHandlerSucceeded() throws InterruptedException {
    // given
    EventHandler mockedEventHandler = mock(EventHandler.class);
    when(mockedEventHandler.isEligible(any(DataImportEventPayload.class))).thenReturn(true);
    doAnswer(invocationOnMock -> CompletableFuture.completedFuture(invocationOnMock.getArgument(0)))
      .when(mockedEventHandler).handle(any(DataImportEventPayload.class));
    EventManager.registerEventHandler(mockedEventHandler);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(TENANT_ID)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(new HashMap<>(){{
        put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
      }})
      .withProfileSnapshot(profileSnapshotWrapper);

    String recordId = UUID.randomUUID().toString();
    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String>  producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));
    producerRecord.headers().add(RECORD_ID_HEADER, recordId.getBytes(UTF_8));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, DI_COMPLETED.value());
    List<String> observedRecords = observeTopic(topicToObserve, Duration.ofSeconds(40));
    assertFalse(observedRecords.isEmpty());
    assertEquals(1, observedRecords.size());

    Event observedRecord = new JsonObject(observedRecords.getFirst()).mapTo(Event.class);
    DataImportEventPayload consumedEventPayload = new JsonObject(observedRecord.getEventPayload()).mapTo(DataImportEventPayload.class);
    assertEquals(recordId, consumedEventPayload.getContext().get(RECORD_ID_HEADER));
  }

  @Test
  public void shouldPublishDiErrorEventWhenProcessingCoreHandlerFailed() {
    // given
    EventHandler mockedEventHandler = mock(EventHandler.class);
    when(mockedEventHandler.isEligible(any(DataImportEventPayload.class))).thenReturn(true);
    doAnswer(invocationOnMock -> CompletableFuture.failedFuture(new EventProcessingException("error msg")))
      .when(mockedEventHandler).handle(any(DataImportEventPayload.class));
    EventManager.registerEventHandler(mockedEventHandler);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(TENANT_ID)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(new HashMap<>(){{
        put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
      }})
      .withProfileSnapshot(profileSnapshotWrapper);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "test-key", Json.encode(event));

    // when
    try (KafkaProducer<String, String> producer = createKafkaProducer()) {
      producer.send(producerRecord);
    }

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, DI_ERROR.value());
    List<String> observedRecords = observeTopic(topicToObserve, Duration.ofSeconds(30));
    assertFalse(observedRecords.isEmpty());
    assertEquals(1, observedRecords.size());
  }

}
