package org.folio.rest.impl;

import static org.folio.ApiTestSuite.mockPort;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.HashMap;
import java.util.Map;
import org.folio.rest.RestConstants;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@DisplayName("InvoiceHelper should : ")
class InvoiceHelperTest extends ApiTestBase {

  private static final String PO_LINE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  private static final String EXISTING_PO_LINE_ID = "c2755a78-2f8d-47d0-a218-059a9b7391b4";

  private Context context;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  public void setUp(final VertxTestContext testContext) {
    super.setUp(testContext);
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
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context);

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
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context);

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
