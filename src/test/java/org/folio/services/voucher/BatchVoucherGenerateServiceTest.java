package org.folio.services.voucher;

import static org.folio.ApiTestSuite.mockPort;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.folio.converters.AddressConverter;
import org.folio.rest.RestConstants;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.VoucherService;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.VendorRetrieveService;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceLineService;
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
    RestClient restClient = new RestClient();
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(restClient);
    ConfigurationService configurationService = new ConfigurationService(new RestClient());
    VoucherCommandService voucherCommandService = new VoucherCommandService(restClient,
      new VoucherNumberService(new RestClient()),
      voucherRetrieveService, new VoucherValidator(), configurationService, new ExchangeRateProviderResolver());
    VendorRetrieveService vendorRetrieveService = new VendorRetrieveService(restClient);
    AddressConverter addressConverter = AddressConverter.getInstance();
    VoucherService voucherService = new VoucherService(voucherRetrieveService, voucherCommandService,
      vendorRetrieveService, addressConverter);


    InvoiceService invoiceService = new BaseInvoiceService(new RestClient(), new InvoiceLineService(new RestClient()));
    InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceService);
    BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en", vendorRetrieveService,
              invoiceRetrieveService, voucherService, addressConverter);
    BatchVoucherExport batchVoucherExport = new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_SAMPLE_PATH)).mapTo(BatchVoucherExport.class);

    CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport, new RequestContext(context, okapiHeaders));
    BatchVoucher batchVoucher = future.get();
    assertNotNull(batchVoucher);
  }

  @Test
  public void negativeGetBatchVoucherIfVouchersIsAbsentTest() {
    Assertions.assertThrows(CompletionException.class, () -> {
      RestClient restClient = new RestClient();
      VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(restClient);
      ConfigurationService configurationService = new ConfigurationService(restClient);
      VoucherCommandService voucherCommandService = new VoucherCommandService(restClient,
        new VoucherNumberService(restClient),
        voucherRetrieveService, new VoucherValidator(), configurationService, new ExchangeRateProviderResolver());
      VendorRetrieveService vendorRetrieveService = new VendorRetrieveService(restClient);
      AddressConverter addressConverter = AddressConverter.getInstance();
      VoucherService voucherService = new VoucherService(voucherRetrieveService, voucherCommandService,
        vendorRetrieveService, addressConverter);
      InvoiceService invoiceService = new BaseInvoiceService(restClient, new InvoiceLineService(new RestClient()));
      InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceService);

      BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en", vendorRetrieveService,
              invoiceRetrieveService, voucherService, addressConverter);
      BatchVoucherExport batchVoucherExport = new BatchVoucherExport();
      CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport, new RequestContext(context, okapiHeaders));
      future.join();
    });
  }
}
