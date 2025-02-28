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
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceApprovalService;
import org.folio.services.invoice.InvoiceCancelService;
import org.folio.services.invoice.InvoiceFiscalYearsService;
import org.folio.services.invoice.InvoiceFundDistributionService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoicePaymentService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.invoice.PoLinePaymentStatusUpdateService;
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
import org.folio.services.voucher.VoucherCreationService;
import org.folio.services.voucher.VoucherNumberService;
import org.folio.services.voucher.VoucherService;
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
                                                      FundAvailabilityHolderValidator fundAvailabilityValidator) {
    return new PendingPaymentWorkflowService(baseTransactionService, encumbranceService, fundAvailabilityValidator);
  }

  @Bean
  PaymentCreditWorkflowService paymentCreditService(BaseTransactionService baseTransactionService) {
    return new PaymentCreditWorkflowService(baseTransactionService);
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
  VoucherCreationService voucherCreationService(ExpenseClassRetrieveService expenseClassRetrieveService, FundService fundService,
    VoucherLineService voucherLineService, VoucherService voucherService) {
    return new VoucherCreationService(expenseClassRetrieveService, fundService, voucherLineService, voucherService);
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
  InvoiceFundDistributionService invoiceFundDistributionService(AdjustmentsService adjustmentsService,
      ConfigurationService configurationService, ExchangeRateProviderResolver exchangeRateProviderResolver) {
    return new InvoiceFundDistributionService(adjustmentsService, configurationService, exchangeRateProviderResolver);
  }

  @Bean
  InvoiceApprovalService invoiceApprovalService(BudgetExpenseClassService budgetExpenseClassService,
      ConfigurationService configurationService, CurrentFiscalYearService currentFiscalYearService,
      EncumbranceService encumbranceService, InvoiceFundDistributionService invoiceFundDistributionService,
      InvoiceLineService invoiceLineService, InvoiceValidator validator, InvoiceWorkflowDataHolderBuilder holderBuilder,
      PendingPaymentWorkflowService pendingPaymentWorkflowService,
      PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService, VendorRetrieveService vendorService,
      VoucherCommandService voucherCommandService, VoucherCreationService voucherCreationService,
      VoucherService voucherService) {
    return new InvoiceApprovalService(budgetExpenseClassService, configurationService, currentFiscalYearService,
      encumbranceService, invoiceFundDistributionService, invoiceLineService, validator, holderBuilder,
      pendingPaymentWorkflowService, poLinePaymentStatusUpdateService, vendorService, voucherCommandService,
      voucherCreationService, voucherService);
  }

  @Bean
  InvoiceCancelService invoiceCancelService(BaseTransactionService baseTransactionService,
                                            EncumbranceService encumbranceService,
                                            VoucherService voucherService,
                                            OrderLineService orderLineService,
                                            OrderService orderService,
                                            PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService,
                                            InvoiceWorkflowDataHolderBuilder invoiceWorkflowDataHolderBuilder) {
    return new InvoiceCancelService(baseTransactionService, encumbranceService, voucherService, orderLineService,
      orderService, poLinePaymentStatusUpdateService, invoiceWorkflowDataHolderBuilder);
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
  InvoiceValidator invoiceValidator(AdjustmentsService adjustmentsService, OrderLineService orderLineService,
      FiscalYearService fiscalYearService) {
    return new InvoiceValidator(adjustmentsService, orderLineService, fiscalYearService);
  }

  @Bean
  AcquisitionsUnitsService acquisitionsUnitsService(RestClient restClient) {
    return new AcquisitionsUnitsService(restClient);
  }

  @Bean
  InvoiceFiscalYearsService invoiceFiscalYearsService(InvoiceWorkflowDataHolderBuilder holderBuilder,
      BudgetService budgetService, FiscalYearService fiscalYearService) {
    return new InvoiceFiscalYearsService(holderBuilder, budgetService, fiscalYearService);
  }

  @Bean
  PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService(InvoiceLineService invoiceLineService,
      InvoiceService invoiceService, OrderLineService orderLineService) {
    return new PoLinePaymentStatusUpdateService(invoiceLineService, invoiceService, orderLineService);
  }
}
