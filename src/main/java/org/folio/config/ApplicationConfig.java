package org.folio.config;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.core.RestClient;
import org.folio.services.transaction.BaseTransactionService;
import org.folio.services.transaction.EncumbranceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;

@Configuration
@ComponentScan({"org.folio"})
public class ApplicationConfig {
  @Bean
  RestClient invoiceStorageRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(INVOICES));
  }

  @Bean
  RestClient trFinanceRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(FINANCE_TRANSACTIONS));
  }

  @Bean
  BaseTransactionService transactionService(RestClient trFinanceRestClient) {
    return new BaseTransactionService(trFinanceRestClient);
  }

  @Bean
  EncumbranceService encumbranceService(BaseTransactionService transactionService){
    return new EncumbranceService(transactionService);
  }
}
