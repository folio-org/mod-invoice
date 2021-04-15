package org.folio.config;

import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.converters.AddressConverter;
import org.folio.rest.core.RestClient;
import org.folio.rest.impl.VoucherService;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.VendorRetrieveService;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.FinanceExchangeRateService;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetExpenseClassService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.InvoiceTransactionSummaryService;
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.FundAvailabilityHolderValidator;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherNumberService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.springframework.context.annotation.Bean;

public class ServicesConfiguration {
  @Bean
  BaseTransactionService transactionService(RestClient restClient) {
    return new BaseTransactionService(restClient);
  }

  @Bean
  EncumbranceService encumbranceService(BaseTransactionService transactionService) {
    return new EncumbranceService(transactionService);
  }

  @Bean
  FinanceExchangeRateService rateOfExchangeService(RestClient restClient) {
    return new FinanceExchangeRateService(restClient);
  }

  @Bean
  VoucherRetrieveService voucherRetrieveService(RestClient restClient) {
    return new VoucherRetrieveService(restClient);
  }

  @Bean
  VoucherValidator voucherValidator() {
    return new VoucherValidator();
  }

  @Bean
  VoucherCommandService voucherCommandService(RestClient restClient,
                                              VoucherNumberService voucherNumberService,
                                              VoucherRetrieveService voucherRetrieveService,
                                              VoucherValidator voucherValidator,
                                              ConfigurationService configurationService, ExchangeRateProviderResolver exchangeRateProviderResolver) {
    return new VoucherCommandService(restClient, voucherNumberService,
      voucherRetrieveService, voucherValidator, configurationService, exchangeRateProviderResolver);
  }

  @Bean
  ConfigurationService tenantConfigurationService(RestClient restClient) {
    return new ConfigurationService(restClient);
  }

  @Bean
  FiscalYearService fiscalYearService(RestClient fiscalYearRestClient){
    return new FiscalYearService(fiscalYearRestClient);
  }

  @Bean
  LedgerService ledgerService(RestClient restClient) {
    return new LedgerService(restClient);
  }

  @Bean
  CurrentFiscalYearService currentFiscalYearService(RestClient restClient, FundService fundService) {
    return new CurrentFiscalYearService(restClient, fundService);
  }

  @Bean
  ExchangeRateProviderResolver exchangeRateProviderResolver() {
    return new ExchangeRateProviderResolver();
  }

  @Bean
  FundService fundService(RestClient restClient) {
    return new FundService(restClient);
  }

  @Bean
  PendingPaymentWorkflowService pendingPaymentService(BaseTransactionService baseTransactionService,
                                                      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                                      FundAvailabilityHolderValidator fundAvailabilityValidator) {
    return new PendingPaymentWorkflowService(baseTransactionService, invoiceTransactionSummaryService, fundAvailabilityValidator);
  }

  @Bean
  PaymentCreditWorkflowService paymentCreditService(BaseTransactionService baseTransactionService) {
    return new PaymentCreditWorkflowService(baseTransactionService);
  }

  @Bean
  InvoiceTransactionSummaryService invoiceTransactionSummaryService(RestClient restClient) {
    return new InvoiceTransactionSummaryService(restClient);
  }

  @Bean
  FundAvailabilityHolderValidator budgetValidationService() {
    return new FundAvailabilityHolderValidator();
  }

  @Bean
  BudgetExpenseClassService budgetExpenseClassService(RestClient restClient) {

    return new BudgetExpenseClassService(restClient);
  }

  @Bean
  OrderService orderService(RestClient restClient, InvoiceLineService invoiceLineService) {
    return new OrderService(restClient, invoiceLineService);
  }

  @Bean
  InvoiceLineService invoiceLineService(RestClient restClient) {
    return new InvoiceLineService(restClient);
  }

  @Bean
  InvoiceService invoiceService(RestClient restClient) {
    return new BaseInvoiceService(restClient);
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
  BudgetService budgetService(RestClient restClient) {
    return new BudgetService(restClient);
  }

  @Bean
  InvoiceRetrieveService invoiceRetrieveService(InvoiceService invoiceService) {
    return new InvoiceRetrieveService(invoiceService);
  }

  @Bean
  VoucherService voucherService(VoucherRetrieveService voucherRetrieveService,
      VoucherCommandService voucherCommandService, VendorRetrieveService vendorRetrieveService,
      AddressConverter addressConverter) {
    return new VoucherService(voucherRetrieveService, voucherCommandService, vendorRetrieveService, addressConverter);
  }

  @Bean
  VoucherNumberService voucherNumberService(RestClient restClient) {
    return new VoucherNumberService(restClient);
  }

  @Bean
  ExpenseClassRetrieveService expenseClassRetrieveService(RestClient restClient) {
    return new ExpenseClassRetrieveService(restClient);
  }

  @Bean
  VendorRetrieveService vendorRetrieveService(RestClient restClient) {
    return new VendorRetrieveService(restClient);
  }

  @Bean
  AddressConverter addressConverter() {
    return AddressConverter.getInstance();
  }
}
