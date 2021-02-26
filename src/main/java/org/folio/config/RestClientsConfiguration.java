package org.folio.config;

import static org.folio.invoices.utils.ResourcePathResolver.BUDGET_EXPENSE_CLASSES;
import static org.folio.invoices.utils.ResourcePathResolver.COMPOSITE_ORDER;
import static org.folio.invoices.utils.ResourcePathResolver.CURRENT_BUDGET;
import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_CREDITS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_EXCHANGE_RATE;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_PENDING_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.FISCAL_YEARS;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.LEDGERS;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_INVOICE_RELATIONSHIP;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.folio.rest.core.RestClient;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientsConfiguration {

  @Bean
  RestClient invoiceStorageRestClient() {
    return new RestClient(resourcesPath(INVOICES));
  }

  @Bean
  RestClient expenseClassRestTemplate() {
    return new RestClient(resourcesPath(EXPENSE_CLASSES_URL));
  }

  @Bean
  ExpenseClassRetrieveService expenseClassRetrieveService(RestClient expenseClassRestTemplate) {
    return new ExpenseClassRetrieveService(expenseClassRestTemplate);
  }

  @Bean
  RestClient trFinanceRestClient() {
    return new RestClient(resourcesPath(FINANCE_TRANSACTIONS));
  }

  @Bean
  RestClient exchangeRateRestClient() {
    return new RestClient(resourcesPath(FINANCE_EXCHANGE_RATE));
  }

  @Bean
  RestClient voucherStorageRestClient() {
    return new RestClient(resourcesPath(VOUCHERS_STORAGE));
  }

  @Bean
  RestClient voucherNumberStorageRestClient() {
    return new RestClient(resourcesPath(VOUCHER_NUMBER_STORAGE));
  }

  @Bean
  RestClient configEntriesRestClient() {
    return new RestClient(resourcesPath(TENANT_CONFIGURATION_ENTRIES));
  }

  @Bean
  RestClient fiscalYearRestClient() {
    return new RestClient(resourcesPath(FISCAL_YEARS));
  }

  @Bean
  RestClient ledgerRestClient() {
    return new RestClient(resourcesPath(LEDGERS));
  }

  @Bean
  RestClient fundRestClient() {
    return new RestClient(resourcesPath(FUNDS));
  }

  @Bean
  RestClient currentFiscalYearRestClient() {
    return new RestClient(resourcesPath(LEDGERS), "/%s/current-fiscal-year");
  }

  @Bean
  RestClient pendingPaymentRestClient() {
    return new RestClient(resourcesPath(FINANCE_PENDING_PAYMENTS));
  }

  @Bean
  RestClient paymentRestClient() {
    return new RestClient(resourcesPath(FINANCE_PAYMENTS));
  }

  @Bean
  RestClient creditRestClient() {
    return new RestClient(resourcesPath(FINANCE_CREDITS));
  }

  @Bean
  RestClient invoiceTransactionSummaryRestClient() {
    return new RestClient("/finance/invoice-transaction-summaries");
  }

  @Bean
  RestClient budgetExpenseClassRestClient() {
    return new RestClient(resourcesPath(BUDGET_EXPENSE_CLASSES));
  }

  @Bean
  RestClient activeBudgetRestClient() {
    return new RestClient(resourcesPath(CURRENT_BUDGET), "?status=Active");
  }

  @Bean
  RestClient orderRestClient() {
    return new RestClient(resourcesPath(COMPOSITE_ORDER));
  }

  @Bean
  RestClient orderLinesRestClient() {
    return new RestClient(resourcesPath(ORDER_LINES));
  }

  @Bean
  RestClient orderInvoiceRelationshipRestClient() {
    return new RestClient(resourcesPath(ORDER_INVOICE_RELATIONSHIP));
  }
  @Bean
  RestClient invoiceLineRestClient() {
    return new RestClient(resourcesPath(INVOICE_LINES));
  }
}
