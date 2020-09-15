package org.folio.config;

import org.folio.rest.core.RestClient;
import org.folio.services.finance.BudgetExpenseClassService;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.FinanceExchangeRateService;
import org.folio.services.finance.BudgetValidationService;
import org.folio.services.finance.CurrentFiscalYearService;
import org.folio.services.finance.FiscalYearService;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.transaction.BaseTransactionService;
import org.folio.services.transaction.CreditManagingService;
import org.folio.services.transaction.EncumbranceService;
import org.folio.services.transaction.InvoiceTransactionSummaryService;
import org.folio.services.transaction.PaymentCreditService;
import org.folio.services.transaction.PaymentManagingService;
import org.folio.services.transaction.PendingPaymentManagingService;
import org.folio.services.transaction.PendingPaymentService;
import org.folio.services.transaction.TransactionManagingService;
import org.folio.services.transaction.TransactionManagingServiceFactory;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.springframework.context.annotation.Bean;

import java.util.Set;

public class ServicesConfiguration {
  @Bean
  BaseTransactionService transactionService(RestClient trFinanceRestClient, TransactionManagingServiceFactory transactionManagingServiceFactory) {
    return new BaseTransactionService(trFinanceRestClient, transactionManagingServiceFactory);
  }

  @Bean
  EncumbranceService encumbranceService(BaseTransactionService transactionService) {
    return new EncumbranceService(transactionService);
  }

  @Bean
  FinanceExchangeRateService rateOfExchangeService(RestClient exchangeRateRestClient) {
    return new FinanceExchangeRateService(exchangeRateRestClient);
  }

  @Bean
  VoucherRetrieveService voucherRetrieveService(RestClient voucherStorageRestClient) {
    return new VoucherRetrieveService(voucherStorageRestClient);
  }

  @Bean
  VoucherValidator voucherValidator() {
    return new VoucherValidator();
  }

  @Bean
  VoucherCommandService voucherCommandService(RestClient voucherStorageRestClient, RestClient voucherNumberStorageRestClient,
                                              VoucherRetrieveService voucherRetrieveService,
                                              VoucherValidator voucherValidator,
                                              TenantConfigurationService tenantConfigurationService) {
    return new VoucherCommandService(voucherStorageRestClient, voucherNumberStorageRestClient,
      voucherRetrieveService, voucherValidator, tenantConfigurationService);
  }

  @Bean
  TenantConfigurationService tenantConfigurationService(RestClient configEntriesRestClient) {
    return new TenantConfigurationService(configEntriesRestClient);
  }

  @Bean
  FiscalYearService fiscalYearService(RestClient fiscalYearRestClient){
    return new FiscalYearService(fiscalYearRestClient);
  }

  @Bean
  LedgerService ledgerService(RestClient ledgerRestClient) {
    return new LedgerService(ledgerRestClient);
  }

  @Bean
  CurrentFiscalYearService currentFiscalYearService(RestClient currentFiscalYearRestClient, FundService fundService) {
    return new CurrentFiscalYearService(currentFiscalYearRestClient, fundService);
  }

  @Bean
  ExchangeRateProviderResolver exchangeRateProviderResolver() {
    return new ExchangeRateProviderResolver();
  }

  @Bean
  FundService fundService(RestClient fundRestClient) {
    return new FundService(fundRestClient);
  }

  @Bean
  PendingPaymentService pendingPaymentService(BaseTransactionService baseTransactionService,
                                              CurrentFiscalYearService currentFiscalYearService,
                                              ExchangeRateProviderResolver exchangeRateProviderResolver,
                                              FinanceExchangeRateService financeExchangeRateService,
                                              InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                              BudgetValidationService budgetValidationService) {
    return new PendingPaymentService(baseTransactionService, currentFiscalYearService, exchangeRateProviderResolver, financeExchangeRateService, invoiceTransactionSummaryService, budgetValidationService);
  }

  @Bean
  TransactionManagingService pendingPaymentManagingService(RestClient pendingPaymentRestClient) {
    return new PendingPaymentManagingService(pendingPaymentRestClient);
  }

  @Bean
  TransactionManagingService paymentManagingService(RestClient paymentRestClient) {
    return new PaymentManagingService(paymentRestClient);
  }

  @Bean
  TransactionManagingService creditManagingService(RestClient creditRestClient) {
    return new CreditManagingService(creditRestClient);
  }

  @Bean
  TransactionManagingServiceFactory transactionManagingServiceFactory(Set<TransactionManagingService> transactionManagingServices) {
    return new TransactionManagingServiceFactory(transactionManagingServices);
  }

  @Bean
  PaymentCreditService paymentCreditService(BaseTransactionService baseTransactionService,
                                            CurrentFiscalYearService currentFiscalYearService,
                                            ExchangeRateProviderResolver exchangeRateProviderResolver,
                                            FinanceExchangeRateService financeExchangeRateService) {
    return new PaymentCreditService(baseTransactionService, currentFiscalYearService, exchangeRateProviderResolver, financeExchangeRateService);
  }

  @Bean
  InvoiceTransactionSummaryService invoiceTransactionSummaryService(RestClient invoiceTransactionSummaryRestClient) {
    return new InvoiceTransactionSummaryService(invoiceTransactionSummaryRestClient);
  }

  @Bean
  BudgetValidationService budgetValidationService(ExchangeRateProviderResolver exchangeRateProviderResolver,
                                                  FiscalYearService fiscalYearService,
                                                  FundService fundService,
                                                  LedgerService ledgerService,
                                                  RestClient activeBudgetRestClient) {
    return new BudgetValidationService(exchangeRateProviderResolver, fiscalYearService, fundService, ledgerService, activeBudgetRestClient);
  }

  @Bean
  BudgetExpenseClassService budgetExpenseClassService(RestClient budgetExpenseClassRestClient) {
    return new BudgetExpenseClassService(budgetExpenseClassRestClient);
  }
}
