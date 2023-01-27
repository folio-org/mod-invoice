package org.folio.services.voucher;

import static org.folio.ApiTestSuite.mockPort;
import static org.folio.ApiTestSuite.vertx;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.folio.converters.AddressConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.RestConstants;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.BatchGroupService;
import org.folio.services.InvoiceLinesRetrieveService;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.VendorRetrieveService;
import org.folio.services.VoucherLineService;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.VoucherValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class BatchVoucherGenerateServiceTest extends ApiTestBase {
  private static final String VALID_BATCH_VOUCHER_EXPORTS_ID ="566c9156-e52f-4597-9fee-5ddac91d14f2";
  public static final String BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExports/";
  private static final String BATCH_VOUCHER_EXPORT_SAMPLE_PATH = BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH
    + VALID_BATCH_VOUCHER_EXPORTS_ID + ".json";

  private Context context;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  public void setUp()  {
    context = vertx.getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(RestConstants.OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void positiveGenerateBatchVoucherTest(VertxTestContext vertxTestContext) throws IOException {
    var restClient = new RestClient();
    VoucherService voucherService = new VoucherService(restClient, new VoucherValidator());
    InvoiceLineService invoiceLineService = new InvoiceLineService(restClient);
    OrderService orderService = new OrderService(restClient, invoiceLineService, new OrderLineService(restClient));
    InvoiceService invoiceService = new BaseInvoiceService(restClient, invoiceLineService, orderService);
    InvoiceRetrieveService invoiceRetrieveService = new InvoiceRetrieveService(invoiceService);
    InvoiceLinesRetrieveService invoiceLinesRetrieveService = new InvoiceLinesRetrieveService(invoiceLineService);
    VoucherLineService voucherLineService = new VoucherLineService(restClient);
    VendorRetrieveService vendorRetrieveService = new VendorRetrieveService(restClient);
    AddressConverter addressConverter = new AddressConverter();
    BatchGroupService batchGroupService = new BatchGroupService(restClient);
    BatchVoucherExport batchVoucherExport = new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_SAMPLE_PATH))
      .mapTo(BatchVoucherExport.class);

    BatchVoucherGenerateService batchVoucherGenerateService = new BatchVoucherGenerateService(voucherService, invoiceRetrieveService, invoiceLinesRetrieveService,
      voucherLineService, vendorRetrieveService, addressConverter, batchGroupService);

    Future<BatchVoucher> future = batchVoucherGenerateService.buildBatchVoucherObject(batchVoucherExport, new RequestContext(context, okapiHeaders));
    vertxTestContext.assertComplete(future)
      .onComplete(result -> {
        Assertions.assertNotNull(result.result());
        vertxTestContext.completeNow();
      });
  }

  @Test
  @Disabled
  public void negativeGetBatchVoucherIfVouchersIsAbsentTest(VertxTestContext vertxTestContext) {
    BatchVoucherExport batchVoucherExport = new BatchVoucherExport();
    var restClient = new RestClient();
    VoucherService voucherService = new VoucherService(restClient, new VoucherValidator());

    BatchVoucherGenerateService batchVoucherGenerateService = new BatchVoucherGenerateService(voucherService,null , null,
      null, null, null, null);
    Future<BatchVoucher> future = batchVoucherGenerateService.buildBatchVoucherObject(batchVoucherExport, new RequestContext(context, okapiHeaders));
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        assertThat(result.cause(), instanceOf(HttpException.class));

        HttpException exception = (HttpException) result.cause();
        Assertions.assertEquals(404, exception.getCode());
        vertxTestContext.completeNow();
      });
  }
}
