package org.folio.services.voucher;

import static org.folio.ApiTestSuite.mockPort;
import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.core.RestClient;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.impl.VoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.folio.services.validator.VoucherValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class BatchVoucherGenerateServiceTest extends ApiTestBase {
  private static final String VALID_BATCH_VOUCHER_EXPORTS_ID ="566c9156-e52f-4597-9fee-5ddac91d14f2";
  public static final String BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExports/";
  private static final String BATCH_VOUCHER_EXPORT_SAMPLE_PATH = BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH
    + VALID_BATCH_VOUCHER_EXPORTS_ID + ".json";

  private Context context;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  public void setUp()  {
    super.setUp();
    context = Vertx.vertx().getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
  }

  @Test
  @Disabled
  public void positiveGenerateBatchVoucherTest() throws IOException, ExecutionException, InterruptedException {
    ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL)));
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)));
    TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));
    VoucherCommandService voucherCommandService = new VoucherCommandService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)),
      new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
      voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, context, "en", voucherRetrieveService, voucherCommandService);
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context, "en", expenseClassRetrieveService,
      voucherCommandService, voucherRetrieveService, voucherHelper, new ExchangeRateProviderResolver());
    InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceHelper);
    BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en",
              invoiceRetrieveService, voucherRetrieveService, voucherCommandService);
    BatchVoucherExport batchVoucherExport = new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_SAMPLE_PATH)).mapTo(BatchVoucherExport.class);

    CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport);
    BatchVoucher batchVoucher = future.get();
    assertNotNull(batchVoucher);
  }

  @Test
  public void negativeGetBatchVoucherIfVouchersIsAbsentTest() {
    Assertions.assertThrows(CompletionException.class, () -> {
      ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL)));
      VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)));
      TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));
      VoucherCommandService voucherCommandService = new VoucherCommandService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)),
        new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
        voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

      VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, context, "en", voucherRetrieveService, voucherCommandService);
      InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context, "en", expenseClassRetrieveService,
                         voucherCommandService, voucherRetrieveService, voucherHelper, new ExchangeRateProviderResolver());
      InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceHelper);
      BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en",
              invoiceRetrieveService, voucherRetrieveService, voucherCommandService);
      BatchVoucherExport batchVoucherExport = new BatchVoucherExport();
      CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport);
      future.join();
    });
  }
}
