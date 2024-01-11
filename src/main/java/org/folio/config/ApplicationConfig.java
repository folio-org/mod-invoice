package org.folio.config;

import org.folio.rest.core.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan({"org.folio"})
@Import({ ServicesConfiguration.class, KafkaConsumersConfiguration.class })
public class ApplicationConfig {

  @Bean RestClient restClient() {
    return new RestClient();
  }

}
