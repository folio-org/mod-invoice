package org.folio.config;

import org.folio.kafka.KafkaConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class KafkaConsumersConfiguration {

  @Value("${KAFKA_HOST:kafka}")
  private String kafkaHost;
  @Value("${KAFKA_PORT:9092}")
  private String kafkaPort;
  @Value("${OKAPI_URL:http://okapi:9130}")
  private String okapiUrl;
  @Value("${REPLICATION_FACTOR:1}")
  private int replicationFactor;
  @Value("${ENV:folio}")
  private String envId;

  @Bean
  public KafkaConfig kafkaConfig() {
    return KafkaConfig.builder()
      .envId(envId)
      .kafkaHost(kafkaHost)
      .kafkaPort(kafkaPort)
      .okapiUrl(okapiUrl)
      .replicationFactor(replicationFactor)
      .build();
  }

}
