package org.folio.services.voucher;

import static org.folio.ApiTestSuite.mockPort;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
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
import org.folio.rest.RestConstants;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.validator.VoucherValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
    okapiHeaders.put(RestConstants.OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
  }

  @Test
  public void positiveGenerateBatchVoucherTest() throws IOException, ExecutionException, InterruptedException {
    RestClient restClientVoucherStorage = new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE));
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(restClientVoucherStorage);
    TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));
    VoucherCommandService voucherCommandService = new VoucherCommandService(restClientVoucherStorage,
      new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
      voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

    InvoiceService invoiceService = new BaseInvoiceService(new RestClient(ResourcePathResolver.resourcesPath(INVOICES)));
    InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceService);
    BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en",
              invoiceRetrieveService, voucherRetrieveService, voucherCommandService);
    BatchVoucherExport batchVoucherExport = new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_SAMPLE_PATH)).mapTo(BatchVoucherExport.class);

    CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport, new RequestContext(context, okapiHeaders));
    BatchVoucher batchVoucher = future.get();
    assertNotNull(batchVoucher);
  }

  @Test
  public void negativeGetBatchVoucherIfVouchersIsAbsentTest() {
    Assertions.assertThrows(CompletionException.class, () -> {
      VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)));
      TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));
      VoucherCommandService voucherCommandService = new VoucherCommandService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)),
        new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
        voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

      InvoiceService invoiceService = new BaseInvoiceService(new RestClient(ResourcePathResolver.resourcesPath(INVOICES)));
      InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceService);
      BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en",
              invoiceRetrieveService, voucherRetrieveService, voucherCommandService);
      BatchVoucherExport batchVoucherExport = new BatchVoucherExport();
      CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport, new RequestContext(context, okapiHeaders));
      future.join();
    });
  }
}
