package org.folio.services;

import static java.util.stream.Collectors.toList;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.invoices.utils.ResourcePathResolver.EXPENSE_CLASSES_URL;
import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.core.RestClient;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.impl.VoucherHelper;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class InvoiceRetrieveServiceTest extends ApiTestBase {
  private static Context context;
  private static Map<String, String> okapiHeaders;
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
  public void positiveGetInvoicesByChunksTest() throws IOException, ExecutionException, InterruptedException {
    ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL)));
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)));
    TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));

    VoucherCommandService voucherCommandService = new VoucherCommandService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)),
              new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
                      voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, context, "en", voucherRetrieveService, voucherCommandService);
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context, "en", expenseClassRetrieveService,
      voucherCommandService, voucherRetrieveService, voucherHelper, new ExchangeRateProviderResolver());
    InvoiceRetrieveService service = new InvoiceRetrieveService(invoiceHelper);
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers").stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    CompletableFuture<List<InvoiceCollection>> future = service.getInvoicesByChunks(vouchers);
    List<InvoiceCollection> lineCollections = future.get();
    assertEquals(3, lineCollections.get(0).getInvoices().size());
  }

  @Test
  public void positiveGetInvoiceMapTest() throws IOException, ExecutionException, InterruptedException {
    ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL)));
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)));
    TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));
    VoucherCommandService voucherCommandService = new VoucherCommandService(new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE)),
      new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
      voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, context, "en", voucherRetrieveService, voucherCommandService);
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context, "en", expenseClassRetrieveService,
      voucherCommandService, voucherRetrieveService, voucherHelper, new ExchangeRateProviderResolver());
    InvoiceRetrieveService service = new InvoiceRetrieveService(invoiceHelper);
    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    List<Voucher> vouchers = vouchersList.getJsonArray("vouchers") .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Voucher.class))
      .collect(toList());

    vouchers.remove(1);
    VoucherCollection voucherCollection = new VoucherCollection();
    voucherCollection.setVouchers(vouchers);

    CompletableFuture<Map<String, Invoice>> future = service.getInvoiceMap(voucherCollection);
    Map<String, Invoice> lineMap = future.get();
    assertEquals(3, lineMap.values().size());
  }
}
