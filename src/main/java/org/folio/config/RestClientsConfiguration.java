package org.folio.config;

import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_EXCHANGE_RATE;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.core.RestClient;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientsConfiguration {

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

  @Bean
  RestClient trFinanceRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(FINANCE_TRANSACTIONS));
  }

  @Bean
  RestClient exchangeRateRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(FINANCE_EXCHANGE_RATE));
  }

  @Bean
  RestClient voucherStorageRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE));
  }

  @Bean
  RestClient voucherNumberStorageRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE));
  }

  @Bean
  RestClient configEntriesRestClient() {
    return new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES));
  }
}
