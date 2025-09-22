package org.folio.verticles;

import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.folio.dataimport.cache.CancelledJobsIdsCache;
import org.folio.kafka.KafkaConfig;
import org.folio.rest.jaxrs.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CancelledJobConsumerVerticleTest {

  @Mock
  private CancelledJobsIdsCache cancelledJobsIdsCache;

  @Test
  public void shouldCallCachePutWhenEventReceived() {
    // Arrange
    CancelledJobConsumerVerticle consumerVerticle =
      new CancelledJobConsumerVerticle(cancelledJobsIdsCache, mock(KafkaConfig.class), 1000);

    String jobId = UUID.randomUUID().toString();
    Event event = new Event().withEventPayload(jobId);

    KafkaConsumerRecord<String, String> kafkaRecord = mock(KafkaConsumerRecord.class);
    when(kafkaRecord.value()).thenReturn(Json.encode(event));

    // Act
    consumerVerticle.handle(kafkaRecord);

    // Assert
    verify(cancelledJobsIdsCache, timeout(1000).times(1)).put(jobId);
  }
}
