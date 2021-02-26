package org.folio.config;

import org.folio.kafka.KafkaConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan({ "org.folio" })
@Import({ RestClientsConfiguration.class, ServicesConfiguration.class, KafkaConsumersConfiguration.class })
public class ApplicationConfig {
}
