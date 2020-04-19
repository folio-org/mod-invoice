package org.folio.services;

import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.rest.impl.ApiTestSuite.mockPort;
import static org.folio.rest.impl.BatchVoucherExportsApiTest.BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH;
import static org.junit.Assert.assertNotNull;


public class BatchVoucherGenerateServiceTest extends ApiTestBase {
  private Context context;
  private Map<String, String> okapiHeaders;
  private static final String VALID_BATCH_VOUCHER_EXPORTS_ID ="566c9156-e52f-4597-9fee-5ddac91d14f2";
  private static final String BATCH_VOUCHER_EXPORT_SAMPLE_PATH = BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH
    + VALID_BATCH_VOUCHER_EXPORTS_ID + ".json";

  @Before
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
  public void positiveGetInvoicesByChunksTest() throws IOException, ExecutionException, InterruptedException {
    BatchVoucherGenerateService service = new BatchVoucherGenerateService(okapiHeaders, context, "en");
    BatchVoucherExport batchVoucherExport = new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_SAMPLE_PATH)).mapTo(BatchVoucherExport.class);

    CompletableFuture<BatchVoucher> future = service.generateBatchVoucher(batchVoucherExport);
    BatchVoucher batchVoucher = future.get();
    assertNotNull(batchVoucher);
  }
}
