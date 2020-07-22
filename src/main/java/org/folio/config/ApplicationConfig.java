package org.folio.config;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.core.RestClient;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;

@Configuration
@ComponentScan({"org.folio"})
public class ApplicationConfig {
  @Bean
  RestClient invoiceStorageRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(INVOICES));
  }

  @Bean
  RestClient expenseClassRestTemplate() {
    return new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL));
  }

  @Bean
  ExpenseClassRetrieveService expenseClassRetrieveService(RestClient expenseClassRestTemplate) {
    return new ExpenseClassRetrieveService(expenseClassRestTemplate);
  }
}
