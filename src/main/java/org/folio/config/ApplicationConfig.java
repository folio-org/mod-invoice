package org.folio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

@Configuration
@ComponentScan({ "org.folio.rest.impl", "org.folio.invoices" })
@Import({JAXBConfig.class, ConvertersConfig.class})
public class ApplicationConfig {
  @Bean
  PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    ClassPathResource location = new ClassPathResource("application.properties");
    configurer.setLocations(location);
    return configurer;
  }

}
