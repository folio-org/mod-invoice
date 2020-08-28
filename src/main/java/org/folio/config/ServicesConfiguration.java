package org.folio.config;

import org.folio.rest.core.RestClient;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.FinanceExchangeRateService;
import org.folio.services.finance.CurrentFiscalYearService;
import org.folio.services.finance.FiscalYearService;
import org.folio.services.finance.LedgerService;
import org.folio.services.transaction.BaseTransactionService;
import org.folio.services.transaction.EncumbranceService;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.springframework.context.annotation.Bean;

public class ServicesConfiguration {
  @Bean
  BaseTransactionService transactionService(RestClient trFinanceRestClient) {
    return new BaseTransactionService(trFinanceRestClient);
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
  CurrentFiscalYearService currentFiscalYearService(FiscalYearService fiscalYearService, LedgerService ledgerService) {
    return new CurrentFiscalYearService(fiscalYearService, ledgerService);
  }

  @Bean
  ExchangeRateProviderResolver exchangeRateProviderResolver() {
    return new ExchangeRateProviderResolver();
  }
}
