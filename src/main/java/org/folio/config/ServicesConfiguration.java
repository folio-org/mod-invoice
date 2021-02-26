package org.folio.config;

import java.util.Set;

import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.rest.core.RestClient;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.FinanceExchangeRateService;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetExpenseClassService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.validator.FundAvailabilityHolderValidator;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.CreditCreateUpdateService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.InvoiceTransactionSummaryService;
import org.folio.services.finance.transaction.PaymentCreateUpdateService;
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.finance.transaction.PendingPaymentCreateUpdateService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.finance.transaction.TransactionCreateUpdateService;
import org.folio.services.finance.transaction.TransactionManagingServiceFactory;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.springframework.context.annotation.Bean;

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
                                                      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                                      FundAvailabilityHolderValidator fundAvailabilityValidator) {
    return new PendingPaymentWorkflowService(baseTransactionService, invoiceTransactionSummaryService, fundAvailabilityValidator);
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
  PaymentCreditWorkflowService paymentCreditService(BaseTransactionService baseTransactionService) {
    return new PaymentCreditWorkflowService(baseTransactionService);
  }

  @Bean
  InvoiceTransactionSummaryService invoiceTransactionSummaryService(RestClient invoiceTransactionSummaryRestClient) {
    return new InvoiceTransactionSummaryService(invoiceTransactionSummaryRestClient);
  }

  @Bean
  FundAvailabilityHolderValidator budgetValidationService() {
    return new FundAvailabilityHolderValidator();
  }

  @Bean
  BudgetExpenseClassService budgetExpenseClassService(RestClient budgetExpenseClassRestClient) {

    return new BudgetExpenseClassService(budgetExpenseClassRestClient);
  }

  @Bean
  OrderService orderService(RestClient orderRestClient, RestClient orderLinesRestClient, RestClient orderInvoiceRelationshipRestClient, InvoiceLineService invoiceLineService) {
    return new OrderService(orderRestClient, orderLinesRestClient, orderInvoiceRelationshipRestClient, invoiceLineService);
  }

  @Bean
  InvoiceLineService invoiceLineService(RestClient invoiceLineRestClient) {
    return new InvoiceLineService(invoiceLineRestClient);
  }

  @Bean
  InvoiceService invoiceService(RestClient invoiceStorageRestClient) {
    return new BaseInvoiceService(invoiceStorageRestClient);
  }

  @Bean
  InvoiceWorkflowDataHolderBuilder holderBuilder(ExchangeRateProviderResolver exchangeRateProviderResolver,
                                                 FiscalYearService fiscalYearService,
                                                 FundService fundService,
                                                 LedgerService ledgerService,
                                                 BaseTransactionService baseTransactionService,
                                                 BudgetService budgetService,
                                                 ExpenseClassRetrieveService expenseClassRetrieveService) {
    return new InvoiceWorkflowDataHolderBuilder(exchangeRateProviderResolver, fiscalYearService, fundService, ledgerService, baseTransactionService, budgetService, expenseClassRetrieveService);
  }

  @Bean
  BudgetService budgetService(RestClient activeBudgetRestClient) {
    return new BudgetService(activeBudgetRestClient);
  }

  @Bean
  InvoiceRetrieveService invoiceRetrieveService(InvoiceService invoiceService) {
    return new InvoiceRetrieveService(invoiceService);
  }

}
