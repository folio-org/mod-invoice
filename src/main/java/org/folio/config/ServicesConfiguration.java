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
import org.folio.services.transaction.CreditCreateUpdateService;
import org.folio.services.transaction.EncumbranceService;
import org.folio.services.transaction.InvoiceTransactionSummaryService;
import org.folio.services.transaction.PaymentCreditWorkflowService;
import org.folio.services.transaction.PaymentCreateUpdateService;
import org.folio.services.transaction.PendingPaymentCreateUpdateService;
import org.folio.services.transaction.PendingPaymentWorkflowService;
import org.folio.services.transaction.TransactionCreateUpdateService;
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
  PendingPaymentWorkflowService pendingPaymentService(BaseTransactionService baseTransactionService,
                                                      CurrentFiscalYearService currentFiscalYearService,
                                                      ExchangeRateProviderResolver exchangeRateProviderResolver,
                                                      FinanceExchangeRateService financeExchangeRateService,
                                                      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                                      BudgetValidationService budgetValidationService) {
    return new PendingPaymentWorkflowService(baseTransactionService, currentFiscalYearService, exchangeRateProviderResolver, financeExchangeRateService, invoiceTransactionSummaryService, budgetValidationService);
  }

  @Bean
  TransactionCreateUpdateService pendingPaymentManagingService(RestClient pendingPaymentRestClient) {
    return new PendingPaymentCreateUpdateService(pendingPaymentRestClient);
  }

  @Bean
  TransactionCreateUpdateService paymentManagingService(RestClient paymentRestClient) {
    return new PaymentCreateUpdateService(paymentRestClient);
  }

  @Bean
  TransactionCreateUpdateService creditManagingService(RestClient creditRestClient) {
    return new CreditCreateUpdateService(creditRestClient);
  }

  @Bean
  TransactionManagingServiceFactory transactionManagingServiceFactory(Set<TransactionCreateUpdateService> transactionCreateUpdateServices) {
    return new TransactionManagingServiceFactory(transactionCreateUpdateServices);
  }

  @Bean
  PaymentCreditWorkflowService paymentCreditService(BaseTransactionService baseTransactionService,
                                                    CurrentFiscalYearService currentFiscalYearService,
                                                    ExchangeRateProviderResolver exchangeRateProviderResolver,
                                                    FinanceExchangeRateService financeExchangeRateService) {
    return new PaymentCreditWorkflowService(baseTransactionService, currentFiscalYearService, exchangeRateProviderResolver, financeExchangeRateService);
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
                                                  RestClient activeBudgetRestClient,
                                                  FinanceExchangeRateService financeExchangeRateService) {
    return new BudgetValidationService(exchangeRateProviderResolver, fiscalYearService, fundService, ledgerService, activeBudgetRestClient, financeExchangeRateService);
  }

  @Bean
  BudgetExpenseClassService budgetExpenseClassService(RestClient budgetExpenseClassRestClient) {
    return new BudgetExpenseClassService(budgetExpenseClassRestClient);
  }
}
