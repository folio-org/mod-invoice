package org.folio.dataimport.handlers.actions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.folio.ApiTestSuite.KAFKA_ENV_VALUE;
import static org.folio.ApiTestSuite.observeTopic;
import static org.folio.ApiTestSuite.sendToTopic;
import static org.folio.DataImportEventTypes.DI_COMPLETED;
import static org.folio.DataImportEventTypes.DI_ERROR;
import static org.folio.DataImportEventTypes.DI_INCOMING_EDIFACT_RECORD_PARSED;
import static org.folio.DataImportEventTypes.DI_JOB_CANCELLED;
import static org.folio.dataimport.handlers.events.DataImportKafkaHandler.JOB_PROFILE_SNAPSHOT_ID_KEY;
import static org.folio.kafka.KafkaTopicNameHelper.getDefaultNameSpace;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.jaxrs.model.EntityType.EDIFACT_INVOICE;
import static org.folio.rest.jaxrs.model.EntityType.INVOICE;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.vertx.junit5.VertxTestContext;
import lombok.SneakyThrows;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.ActionProfile;
import org.folio.ApiTestSuite;
import org.folio.DataImportEventPayload;
import org.folio.JobProfile;
import org.folio.MappingProfile;
import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.processing.events.EventManager;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class CancelledJobEventHandlerTest extends ApiTestBase {

  private static final String JOB_PROFILE_SNAPSHOTS_MOCK_PATH = "/job-execution-service/jobProfileSnapshots";
  private static final String OKAPI_URL = "http://localhost:" + ApiTestSuite.mockPort;

  private final JobProfile jobProfile = new JobProfile()
    .withId(UUID.randomUUID().toString())
    .withName("Create invoice from EDIFACT")
    .withDataType(JobProfile.DataType.EDIFACT);

  private final ActionProfile actionProfile = new ActionProfile()
    .withId(UUID.randomUUID().toString())
    .withAction(ActionProfile.Action.CREATE)
    .withFolioRecord(ActionProfile.FolioRecord.INVOICE);

  private final MappingProfile mappingProfile = new MappingProfile()
    .withId(UUID.randomUUID().toString())
    .withIncomingRecordType(EDIFACT_INVOICE)
    .withExistingRecordType(INVOICE);

  private final ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper()
    .withId(UUID.randomUUID().toString())
    .withProfileId(jobProfile.getId())
    .withContentType(JOB_PROFILE)
    .withContent(JsonObject.mapFrom(jobProfile).getMap())
    .withChildSnapshotWrappers(Collections.singletonList(
      new ProfileSnapshotWrapper()
        .withProfileId(actionProfile.getId())
        .withContentType(ACTION_PROFILE)
        .withContent(JsonObject.mapFrom(actionProfile).getMap())
        .withChildSnapshotWrappers(Collections.singletonList(
          new ProfileSnapshotWrapper()
            .withProfileId(mappingProfile.getId())
            .withContentType(MAPPING_PROFILE)
            .withContent(JsonObject.mapFrom(mappingProfile).getMap())))));

  @SneakyThrows
  @BeforeEach
  public void setUp(final VertxTestContext context) {
    super.setUp(context);
    EventManager.clearEventHandlers();
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK_PATH, profileSnapshotWrapper);
  }

  @Test
  public void shouldSkipEventProcessingWhenJobWasCancelled() throws InterruptedException {

    // Arrange
    // 1. Mock a handler to ensure it's never called for a cancelled job
    EventHandler mockedEventHandler = mock(EventHandler.class);
    when(mockedEventHandler.isEligible(any(DataImportEventPayload.class))).thenReturn(true);
    EventManager.registerEventHandler(mockedEventHandler);

    String tenantId = TENANT_ID;
    String jobExecutionId = UUID.randomUUID().toString();

    // 2. Send a cancellation event to the specific topic
    Event cancellationEvent = new Event().withEventPayload(jobExecutionId);
    String cancellationTopic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), tenantId, DI_JOB_CANCELLED.value());
    ProducerRecord<String, String> cancellationRecord = new ProducerRecord<>(cancellationTopic, Json.encode(cancellationEvent));
    cancellationRecord.headers().add(TENANT_ID, tenantId.getBytes(UTF_8));

    sendToTopic(cancellationRecord);

    // 3. Wait for a moment to ensure the consumer processes the cancellation and updates the cache
    TimeUnit.SECONDS.sleep(1);

    // 4. Prepare a regular data import event for the SAME jobExecutionId
    DataImportEventPayload dataImportEventPayload = new DataImportEventPayload()
      .withEventType(DI_INCOMING_EDIFACT_RECORD_PARSED.value())
      .withTenant(tenantId)
      .withOkapiUrl(OKAPI_URL)
      .withToken(TOKEN)
      .withContext(new HashMap<>() {{
        put(JOB_PROFILE_SNAPSHOT_ID_KEY, profileSnapshotWrapper.getId());
      }})
      .withProfileSnapshot(profileSnapshotWrapper);

    String dataImportTopic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), tenantId, dataImportEventPayload.getEventType());
    Event dataImportEvent = new Event().withEventPayload(Json.encode(dataImportEventPayload));
    ProducerRecord<String, String> dataImportRecord = new ProducerRecord<>(dataImportTopic, "test-key", Json.encode(dataImportEvent));
    // This header is crucial for the handler to identify the job
    dataImportRecord.headers().add("jobExecutionId", jobExecutionId.getBytes(UTF_8));

    // Act
    // 5. Send the data import event, which should be ignored
    sendToTopic(dataImportRecord);

    // Assert
    String completedTopic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), tenantId, DI_COMPLETED.value());
    String errorTopic = KafkaTopicNameHelper.formatTopicName(KAFKA_ENV_VALUE, getDefaultNameSpace(), tenantId, DI_ERROR.value());

    // 6. Observe the output topics for a short duration
    List<String> completedRecords = observeTopic(completedTopic, Duration.ofSeconds(5));
    List<String> errorRecords = observeTopic(errorTopic, Duration.ofSeconds(5));

    // 7. Verify that no events were published, proving the handler skipped the event
    assertTrue(completedRecords.isEmpty(), "DI_COMPLETED topic should be empty because the job was cancelled");
    assertTrue(errorRecords.isEmpty(), "DI_ERROR topic should be empty because the job was cancelled");

    // 8. As an extra check, verify that the core logic handler was never invoked
    verify(mockedEventHandler, never()).handle(any(DataImportEventPayload.class));
  }
}
