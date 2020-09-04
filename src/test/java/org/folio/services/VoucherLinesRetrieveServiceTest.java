package org.folio.services;

import static java.util.stream.Collectors.toList;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class VoucherLinesRetrieveServiceTest extends ApiTestBase {
  private Context context;
  private Map<String, String> okapiHeaders;
  private static final String VOUCHERS_LIST_PATH = BASE_MOCK_DATA_PATH + "vouchers/vouchers.json";

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
  public void positiveTest() throws IOException, ExecutionException, InterruptedException {

    VoucherLinesRetrieveService service = new VoucherLinesRetrieveService(okapiHeaders, context, "en");
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers") .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    CompletableFuture<List<VoucherLineCollection>> future = service.getVoucherLinesByChunks(vouchers);
    List<VoucherLineCollection> lineCollections = future.get();
    assertEquals(3, lineCollections.get(0).getVoucherLines().size());
  }

  @Test
  public void positiveGetInvoiceMapTest() throws IOException, ExecutionException, InterruptedException {

    VoucherLinesRetrieveService service = new VoucherLinesRetrieveService(okapiHeaders, context, "en");
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers") .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    VoucherCollection voucherCollection = new VoucherCollection();voucherCollection.setVouchers(vouchers);

    CompletableFuture<Map<String, List<VoucherLine>>> future = service.getVoucherLinesMap(voucherCollection);
    Map<String, List<VoucherLine>> lineMap = future.get();
    assertEquals(3, lineMap.get("a9b99f8a-7100-47f2-9903-6293d44a9905").size());
  }
}
