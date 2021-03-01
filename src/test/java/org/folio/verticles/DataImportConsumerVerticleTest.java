package org.folio.verticles;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import net.mguenther.kafka.junit.KeyValue;
import net.mguenther.kafka.junit.ObserveKeyValues;
import net.mguenther.kafka.junit.SendKeyValues;
import org.folio.ActionProfile;
import org.folio.DataImportEventPayload;
import org.folio.JobProfile;
import org.folio.MappingProfile;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.processing.events.EventManager;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.processing.events.utils.ZIPArchiver;
import org.folio.processing.exceptions.EventProcessingException;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ApiTestSuite.KAFKA_ENV_VALUE;
import static org.folio.ApiTestSuite.kafkaCluster;
import static org.folio.DataImportEventTypes.DI_COMPLETED;
import static org.folio.DataImportEventTypes.DI_EDIFACT_RECORD_CREATED;
import static org.folio.DataImportEventTypes.DI_ERROR;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.EntityType.INVOICE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataImportConsumerVerticleTest extends ApiTestBase {

  private static final String OKAPI_URL = "localhost";
  private static final String TENANT_ID = "diku";
  private static final String TOKEN = "test-token";

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
  }

  @Test
  public void shouldPublishDiCompletedEventWhenProcessingCoreHandlerSucceeded() throws IOException, InterruptedException {
    // given
    EventHandler mockedEventHandler = mock(EventHandler.class);
    when(mockedEventHandler.isEligible(any(DataImportEventPayload.class))).thenReturn(true);
    doAnswer(invocationOnMock -> CompletableFuture.completedFuture(invocationOnMock.getArgument(0)))
      .when(mockedEventHandler).handle(any(DataImportEventPayload.class));
    EventManager.registerEventHandler(mockedEventHandler);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_EDIFACT_RECORD_CREATED.value())
      .withTenant(TENANT_ID)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(new HashMap<>())
      .withProfileSnapshot(profileSnapshotWrapper);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(ZIPArchiver.zip(Json.encode(dataImportEventPayload)));
    KeyValue<String, String> record = new KeyValue<>("test-key", Json.encode(event));
    SendKeyValues<String, String> request = SendKeyValues.to(topic, Collections.singletonList(record)).useDefaults();

    // when
    kafkaCluster.send(request);

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, DI_COMPLETED.value());
    kafkaCluster.observeValues(ObserveKeyValues.on(topicToObserve, 1)
      .observeFor(30, TimeUnit.SECONDS)
      .build());
  }

  @Test
  public void shouldPublishDiErrorEventWhenProcessingCoreHandlerFailed() throws IOException, InterruptedException {
    // given
    EventHandler mockedEventHandler = mock(EventHandler.class);
    when(mockedEventHandler.isEligible(any(DataImportEventPayload.class))).thenReturn(true);
    doAnswer(invocationOnMock -> CompletableFuture.failedFuture(new EventProcessingException("error msg")))
      .when(mockedEventHandler).handle(any(DataImportEventPayload.class));
    EventManager.registerEventHandler(mockedEventHandler);

    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_EDIFACT_RECORD_CREATED.value())
      .withTenant(TENANT_ID)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(new HashMap<>())
      .withProfileSnapshot(profileSnapshotWrapper);

    String topic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, dataImportEventPayload.getEventType());
    Event event = new Event().withEventPayload(ZIPArchiver.zip(Json.encode(dataImportEventPayload)));
    KeyValue<String, String> record = new KeyValue<>("test-key", Json.encode(event));
    SendKeyValues<String, String> request = SendKeyValues.to(topic, Collections.singletonList(record)).useDefaults();

    // when
    kafkaCluster.send(request);

    // then
    String topicToObserve = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), TENANT_ID, DI_ERROR.value());
    kafkaCluster.observeValues(ObserveKeyValues.on(topicToObserve, 1)
      .observeFor(30, TimeUnit.SECONDS)
      .build());
  }

}
