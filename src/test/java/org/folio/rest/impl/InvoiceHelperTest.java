package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.RestConstants;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.RestClient;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.folio.ApiTestSuite.mockPort;
import static org.folio.invoices.utils.ResourcePathResolver.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InvoiceHelper should :")
class InvoiceHelperTest extends ApiTestBase {

  private static final String PO_LINE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  private static final String EXISTING_PO_LINE_ID = "c2755a78-2f8d-47d0-a218-059a9b7391b4";

  private Context context;
  private Map<String, String> okapiHeaders;

  ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(new RestClient(ResourcePathResolver.resourcesPath(EXPENSE_CLASSES_URL)));
  RestClient restClientVoucherStorage = new RestClient(ResourcePathResolver.resourcesPath(VOUCHERS_STORAGE));
  VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(restClientVoucherStorage);
  TenantConfigurationService tenantConfigurationService = new TenantConfigurationService(new RestClient(ResourcePathResolver.resourcesPath(TENANT_CONFIGURATION_ENTRIES)));

  VoucherCommandService voucherCommandService = new VoucherCommandService(restClientVoucherStorage,
    new RestClient(ResourcePathResolver.resourcesPath(VOUCHER_NUMBER_STORAGE)),
    voucherRetrieveService, new VoucherValidator(), tenantConfigurationService);

  @BeforeEach
  public void setUp() {
    super.setUp();
    context = Vertx.vertx().getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(RestConstants.OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
  }

  @Test
  @DisplayName("not decide to update status of POLines with ONGOING status")
  void shouldReturnFalseWhenCompositeCheckingForUpdatePoLinePaymentStatusIsOngoing() {
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context, "en", expenseClassRetrieveService,
      voucherCommandService, voucherRetrieveService, new ExchangeRateProviderResolver());

    CompositePoLine ongoingCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.ONGOING);

    CompositePoLine fullyPaidCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.FULLY_PAID);

    Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus = new HashMap<>() {{
      put(ongoingCompositePoLine, CompositePoLine.PaymentStatus.ONGOING);
      put(fullyPaidCompositePoLine, CompositePoLine.PaymentStatus.FULLY_PAID);
    }};

    assertFalse(invoiceHelper.isPaymentStatusUpdateRequired(compositePoLinesWithStatus, ongoingCompositePoLine));
  }

  @Test
  @DisplayName("decide to update status of POLines with different statuses")
  void shouldReturnTrueWhenCompositeCheckingForUpdatePoLinePaymentStatusIsDifferentValues() {
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context, "en", expenseClassRetrieveService,
      voucherCommandService, voucherRetrieveService, new ExchangeRateProviderResolver());

    CompositePoLine ongoingCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.ONGOING);

    CompositePoLine fullyPaidCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.FULLY_PAID);

    Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus = new HashMap<>() {{
      put(ongoingCompositePoLine, CompositePoLine.PaymentStatus.ONGOING);
      put(fullyPaidCompositePoLine, CompositePoLine.PaymentStatus.PENDING);
    }};

    assertTrue(invoiceHelper.isPaymentStatusUpdateRequired(compositePoLinesWithStatus, fullyPaidCompositePoLine));
  }

}
