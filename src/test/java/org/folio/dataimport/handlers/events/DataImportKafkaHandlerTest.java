package org.folio.dataimport.handlers.events;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import org.folio.DataImportEventPayload;
import org.folio.dataimport.cache.CancelledJobsIdsCache;
import org.folio.dataimport.cache.JobProfileSnapshotCache;
import org.folio.processing.events.EventManager;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class DataImportKafkaHandlerTest {

  @Mock
  private CancelledJobsIdsCache cancelledJobsIdsCache;
  @Mock
  private JobProfileSnapshotCache profileSnapshotCache;

  @InjectMocks
  private DataImportKafkaHandler kafkaHandler;

  private MockedStatic<EventManager> eventManagerMock;

  @BeforeEach
  public void setUp() {
    eventManagerMock = Mockito.mockStatic(EventManager.class);
  }

  @AfterEach
  public void tearDown() {
    eventManagerMock.close();
  }

  @Test
  public void shouldSkipProcessing_whenJobIsCancelled() {
    // Arrange
    String jobExecutionId = UUID.randomUUID().toString();
    KafkaConsumerRecord<String, String> kafkaRecord = mock(KafkaConsumerRecord.class);
    KafkaHeader header = mock(KafkaHeader.class);

    when(header.key()).thenReturn("jobExecutionId");
    when(header.value()).thenReturn(Buffer.buffer(jobExecutionId));
    when(kafkaRecord.headers()).thenReturn(List.of(header));
    when(cancelledJobsIdsCache.contains(ArgumentMatchers.anyString())).thenReturn(true);

    // Act
    kafkaHandler.handle(kafkaRecord);

    // Assert
    eventManagerMock.verify(() -> EventManager.handleEvent(ArgumentMatchers.any(), ArgumentMatchers.any()), never());
  }

  @Test
  public void shouldProcessEvent_whenJobIsNotCancelled() {

    // Arrange
    String jobExecutionId = UUID.randomUUID().toString();
    String profileSnapshotId = UUID.randomUUID().toString();

    DataImportEventPayload payload = new DataImportEventPayload()
      .withContext(new java.util.HashMap<>() {{
        put("JOB_PROFILE_SNAPSHOT_ID", profileSnapshotId);
      }});
    Event event = new Event().withEventPayload(Json.encode(payload));

    KafkaConsumerRecord<String, String> kafkaRecord = mock(KafkaConsumerRecord.class);
    KafkaHeader header = mock(KafkaHeader.class);

    when(header.key()).thenReturn("jobExecutionId");
    when(header.value()).thenReturn(Buffer.buffer(jobExecutionId));
    when(kafkaRecord.headers()).thenReturn(List.of(header));
    when(kafkaRecord.value()).thenReturn(Json.encode(event));

    // The cache does NOT contain our jobExecutionId.
    when(cancelledJobsIdsCache.contains(jobExecutionId)).thenReturn(false);

    // We mock dependencies that are called AFTER cache validation.
    when(profileSnapshotCache.get(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
      .thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(new ProfileSnapshotWrapper())));

    eventManagerMock.when(() -> EventManager.handleEvent(ArgumentMatchers.any(), ArgumentMatchers.any()))
      .thenReturn(CompletableFuture.completedFuture(new DataImportEventPayload()));

    // Act
    kafkaHandler.handle(kafkaRecord);

    // Assert
    eventManagerMock.verify(() -> EventManager.handleEvent(ArgumentMatchers.any(), ArgumentMatchers.any()), times(1));
  }
}
