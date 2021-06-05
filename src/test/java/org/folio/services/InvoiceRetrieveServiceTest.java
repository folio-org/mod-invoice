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

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class InvoiceRetrieveServiceTest extends ApiTestBase {
  private static Context context;
  private static Map<String, String> okapiHeaders;
  private static final String VOUCHERS_LIST_PATH = BASE_MOCK_DATA_PATH + "vouchers/vouchers.json";

  @Autowired
  InvoiceLineService invoiceLineService;

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
  public void positiveGetInvoicesByChunksTest() throws IOException, ExecutionException, InterruptedException {

    InvoiceService invoiceService = new BaseInvoiceService(new RestClient(), invoiceLineService);
    InvoiceRetrieveService service = new InvoiceRetrieveService(invoiceService);
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers").stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    CompletableFuture<List<InvoiceCollection>> future = service.getInvoicesByChunks(vouchers, new RequestContext(context, okapiHeaders));
    List<InvoiceCollection> lineCollections = future.get();
    Assertions.assertEquals(3, lineCollections.get(0).getInvoices().size());
  }

  @Test
  public void positiveGetInvoiceMapTest() throws IOException, ExecutionException, InterruptedException {
        InvoiceService invoiceService = new BaseInvoiceService(new RestClient(), invoiceLineService);
    InvoiceRetrieveService service = new InvoiceRetrieveService(invoiceService);
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers") .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    VoucherCollection voucherCollection = new VoucherCollection();
    voucherCollection.setVouchers(vouchers);

    CompletableFuture<Map<String, Invoice>> future = service.getInvoiceMap(voucherCollection, new RequestContext(context, okapiHeaders));
    Map<String, Invoice> lineMap = future.get();
    Assertions.assertEquals(3, lineMap.values().size());
  }
}
