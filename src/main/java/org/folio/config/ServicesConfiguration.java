package org.folio.config;

import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.converters.AddressConverter;
import org.folio.rest.core.RestClient;
import org.folio.services.AcquisitionsUnitsService;
import org.folio.services.BatchGroupService;
import org.folio.services.InvoiceLinesRetrieveService;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.VendorRetrieveService;
import org.folio.services.VoucherLineService;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
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
import org.folio.services.finance.transaction.OrderTransactionSummaryService;
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceCancelService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoicePaymentService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.FundAvailabilityHolderValidator;
import org.folio.services.validator.InvoiceValidator;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.BatchVoucherExportConfigService;
import org.folio.services.voucher.BatchVoucherExportsService;
import org.folio.services.voucher.BatchVoucherGenerateService;
import org.folio.services.voucher.BatchVoucherService;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherNumberService;
import org.folio.services.voucher.VoucherService;
import org.springframework.context.annotation.Bean;

public class ServicesConfiguration {
  @Bean
  BaseTransactionService transactionService(RestClient restClient) {
    return new BaseTransactionService(restClient);
  }

  @Bean
  EncumbranceService encumbranceService(BaseTransactionService transactionService,
                                        OrderTransactionSummaryService orderTransactionSummaryService) {
    return new EncumbranceService(transactionService, orderTransactionSummaryService);
  }

  @Bean
  VoucherValidator voucherValidator() {
    return new VoucherValidator();
  }

  @Bean VoucherCommandService voucherCommandService() {
    return new VoucherCommandService();
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
                                                      EncumbranceService encumbranceService,
                                                      InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                                      FundAvailabilityHolderValidator fundAvailabilityValidator) {
    return new PendingPaymentWorkflowService(baseTransactionService, encumbranceService, invoiceTransactionSummaryService, fundAvailabilityValidator);
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
  OrderTransactionSummaryService orderTransactionSummaryService(RestClient restClient) {
    return new OrderTransactionSummaryService(restClient);
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
  OrderService orderService(RestClient restClient, InvoiceLineService invoiceLineService,
                            OrderLineService orderLineService) {
    return new OrderService(restClient, invoiceLineService, orderLineService);
  }

  @Bean
  InvoiceLineService invoiceLineService(RestClient restClient) {
    return new InvoiceLineService(restClient);
  }

  @Bean
  InvoiceService invoiceService(RestClient restClient, InvoiceLineService invoiceLineService, OrderService orderService) {
    return new BaseInvoiceService(restClient, invoiceLineService, orderService);
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
  InvoiceLinesRetrieveService invoiceLinesRetrieveService(InvoiceLineService invoiceLineService) {
    return new InvoiceLinesRetrieveService(invoiceLineService);
  }

  @Bean
  VoucherService voucherService(RestClient restClient, VoucherValidator voucherValidator) {
    return new VoucherService(restClient, voucherValidator);
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

  @Bean
  InvoicePaymentService invoicePaymentService() {
    return new InvoicePaymentService();
  }

  @Bean
  OrderLineService orderLineService(RestClient restClient) {
    return new OrderLineService(restClient);
  }

  @Bean
  InvoiceCancelService invoiceCancelService(BaseTransactionService baseTransactionService,
                                            EncumbranceService encumbranceService,
                                            InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                            VoucherService voucherService,
                                            OrderLineService orderLineService,
                                            OrderService orderService) {
    return new InvoiceCancelService(baseTransactionService, encumbranceService, invoiceTransactionSummaryService,
      voucherService, orderLineService, orderService);
  }
  @Bean
  BatchVoucherService batchVoucherService(RestClient restClient) {
    return new BatchVoucherService(restClient);
  }

  @Bean
  BatchVoucherExportConfigService batchVoucherExportConfigService (RestClient restClient) {
    return new BatchVoucherExportConfigService(restClient);
  }

  @Bean
  BatchVoucherGenerateService batchVoucherGenerateService(VoucherService voucherService,
      InvoiceRetrieveService invoiceRetrieveService, InvoiceLinesRetrieveService invoiceLinesRetrieveService,
      VoucherLineService voucherLineService, VendorRetrieveService vendorRetrieveService,
      AddressConverter addressConverter, BatchGroupService batchGroupService) {
    return new BatchVoucherGenerateService(voucherService, invoiceRetrieveService, invoiceLinesRetrieveService,
        voucherLineService, vendorRetrieveService, addressConverter, batchGroupService);
  }

  @Bean
  VoucherLineService voucherLineService(RestClient restClient) {
    return new VoucherLineService(restClient);
  }

  @Bean
  BatchGroupService batchGroupService(RestClient restClient) {
    return new BatchGroupService(restClient);
  }
  @Bean
  BatchVoucherExportsService batchVoucherExportsService(RestClient restClient) {
    return new BatchVoucherExportsService(restClient);
  }
  @Bean
  AdjustmentsService adjustmentsService() {
    return new AdjustmentsService();
  }

  @Bean
  InvoiceValidator invoiceValidator() {
    return new InvoiceValidator();
  }

  @Bean
  AcquisitionsUnitsService acquisitionsUnitsService(RestClient restClient) {
    return new AcquisitionsUnitsService(restClient);
  }
}
