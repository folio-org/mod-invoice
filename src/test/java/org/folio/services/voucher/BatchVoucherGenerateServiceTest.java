package org.folio.services.voucher;

import static org.folio.ApiTestSuite.mockPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.converters.AddressConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.RestConstants;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.VoucherCollection;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
public class BatchVoucherGenerateServiceTest extends ApiTestBase {
  private static final String VALID_BATCH_VOUCHER_EXPORTS_ID = "566c9156-e52f-4597-9fee-5ddac91d14f2";
  public static final String BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExports/";
  private static final String BATCH_VOUCHER_EXPORT_SAMPLE_PATH = BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH
    + VALID_BATCH_VOUCHER_EXPORTS_ID + ".json";

  private Context context;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  public void setUp() {
    context = vertx.getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(RestConstants.OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void positiveGenerateBatchVoucherTest(VertxTestContext vertxTestContext) throws IOException {
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
  void negativeGetBatchVoucherIfVouchersIsAbsentTest(VertxTestContext vertxTestContext) {
    var restClient = new RestClient();
    var invoiceLineService = new InvoiceLineService(restClient);
    var orderService = new OrderService(restClient, invoiceLineService, new OrderLineService(restClient));
    var invoiceService = new BaseInvoiceService(restClient, invoiceLineService, orderService);
    var invoiceRetrieveService = new InvoiceRetrieveService(invoiceService);
    var invoiceLinesRetrieveService = new InvoiceLinesRetrieveService(invoiceLineService);
    var voucherLineService = new VoucherLineService(restClient);
    var vendorRetrieveService = new VendorRetrieveService(restClient);
    var addressConverter = new AddressConverter();
    var batchGroupService = new BatchGroupService(restClient);

    var batchVoucherExport = new BatchVoucherExport();

    VoucherService mockVoucherService = mock(VoucherService.class);

    doReturn(Future.succeededFuture(new VoucherCollection()))
      .when(mockVoucherService).getVouchers(anyString(), any(Integer.class), any(Integer.class), any(RequestContext.class));

    var batchVoucherGenerateService = new BatchVoucherGenerateService(mockVoucherService, invoiceRetrieveService, invoiceLinesRetrieveService,
      voucherLineService, vendorRetrieveService, addressConverter, batchGroupService);

    Future<BatchVoucher> future = batchVoucherGenerateService.buildBatchVoucherObject(batchVoucherExport, new RequestContext(context, okapiHeaders));
    vertxTestContext.assertFailure(future)
      .onComplete(event -> {
        assertThat(event.cause(), instanceOf(HttpException.class));
        var thrown = (HttpException) event.cause();
        assertThat(thrown.getCode(), is(404));
        var error = thrown.getErrors().getErrors().get(0);
        assertThat(error.getMessage(), is("Vouchers for batch voucher export were not found"));
        List<Parameter> parameters = error.getParameters();
        assertThat(parameters.get(0).getKey(), is("voucherCQL"));
        assertThat(parameters.get(0).getValue(), is("batchGroupId==null and voucherDate>=null and voucherDate<=null and exportToAccounting==true"));
        vertxTestContext.completeNow();
      });
  }
}
