package org.folio.services;

import static java.util.stream.Collectors.toList;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.rest.RestConstants.OKAPI_URL;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class VoucherLineServiceTest extends ApiTestBase {
  private Context context;
  private Map<String, String> okapiHeaders;
  RequestContext requestContext;
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
    requestContext = new RequestContext(context, okapiHeaders);
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void positiveTest(VertxTestContext vertxTestContext) throws IOException {

    VoucherLineService service = new VoucherLineService(new RestClient());
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers") .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    Future<List<VoucherLineCollection>> future = service.getVoucherLinesByChunks(vouchers, requestContext);
    vertxTestContext.assertComplete(future)
      .onComplete(result -> {
        Assertions.assertEquals(3, result.result().get(0).getVoucherLines().size());

        vertxTestContext.completeNow();
      });
  }

  @Test
  public void positiveGetInvoiceMapTest(VertxTestContext vertxTestContext) throws IOException {
    VoucherLineService service = new VoucherLineService(new RestClient());
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers") .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    VoucherCollection voucherCollection = new VoucherCollection();voucherCollection.setVouchers(vouchers);

    Future<Map<String, List<VoucherLine>>> future = service.getVoucherLinesMap(voucherCollection, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(lineMap -> {
        Assertions.assertEquals(3, lineMap.get("a9b99f8a-7100-47f2-9903-6293d44a9905").size());
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);
  }
}
