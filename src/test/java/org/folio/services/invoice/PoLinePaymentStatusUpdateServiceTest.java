package org.folio.services.invoice;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.order.OrderLineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.AWAITING_PAYMENT;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.CANCELLED;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.FULLY_PAID;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.ONGOING;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.PARTIALLY_PAID;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.PAYMENT_NOT_REQUIRED;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.PENDING;
import static org.folio.rest.impl.ApiTestBase.getMockData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class PoLinePaymentStatusUpdateServiceTest {
  private static final String PO_LINE_MOCK_DATA_PATH = "mockdata/poLines/";
  private static final String EXISTING_PO_LINE_ID = "c2755a78-2f8d-47d0-a218-059a9b7391b4";

  private AutoCloseable mockitoMocks;
  private PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService;

  @Mock
  InvoiceLineService invoiceLineService;
  @Mock
  InvoiceService invoiceService;
  @Mock
  OrderLineService orderLineService;
  @Mock
  private RequestContext requestContext;


  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    poLinePaymentStatusUpdateService = new PoLinePaymentStatusUpdateService(invoiceLineService, invoiceService, orderLineService);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  public void updatePoLinePaymentStatusToPayInvoiceUsingParameterTest() {
    String poLineId1 = UUID.randomUUID().toString();
    String poLineId2 = UUID.randomUUID().toString();
    InvoiceLine invoiceLine1 = new InvoiceLine()
      .withReleaseEncumbrance(true)
      .withPoLineId(poLineId1);
    InvoiceLine invoiceLine2 = new InvoiceLine()
      .withReleaseEncumbrance(false)
      .withPoLineId(poLineId2);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine1, invoiceLine2);
    PoLine poLine1 = new PoLine()
      .withId(poLineId1)
      .withPaymentStatus(AWAITING_PAYMENT);
    List<PoLine> poLines1 = List.of(poLine1);
    PoLine poLine2 = new PoLine()
      .withId(poLineId2)
      .withPaymentStatus(AWAITING_PAYMENT);
    List<PoLine> poLines2 = List.of(poLine2);
    String poLinePaymentStatus = "Fully Paid";

    when(orderLineService.getPoLinesByIdAndQuery(eq(List.of(poLineId1)), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLines1));
    when(orderLineService.getPoLinesByIdAndQuery(eq(List.of(poLineId2)), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLines2));
    when(orderLineService.updatePoLines(anyList(), eq(requestContext)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToPayInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);

    assertTrue(result.succeeded());
    assertEquals(FULLY_PAID, poLine1.getPaymentStatus());
    assertEquals(PARTIALLY_PAID, poLine2.getPaymentStatus());
    verify(orderLineService, times(2)).getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext));
    verify(orderLineService, times(2)).updatePoLines(anyList(), eq(requestContext));
  }

  @Test
  public void updatePoLinePaymentStatusToCancelInvoiceUsingParameterTest() {
    String poLineId = UUID.randomUUID().toString();
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(true)
      .withPoLineId(poLineId);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withPaymentStatus(AWAITING_PAYMENT);
    List<PoLine> poLines = List.of(poLine);
    String poLinePaymentStatus = "Cancelled";

    when(orderLineService.getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLines));
    when(orderLineService.updatePoLines(anyList(), eq(requestContext)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToCancelInvoice(null,
      invoiceLines, poLinePaymentStatus, requestContext);

    assertTrue(result.succeeded());
    assertEquals(CANCELLED, poLine.getPaymentStatus());
    verify(orderLineService, times(1)).getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext));
    verify(orderLineService, times(1)).updatePoLines(anyList(), eq(requestContext));
  }

  @Test
  public void updatePoLinePaymentStatusUsingParameterWithNoChangeTest() {
    String poLineId = UUID.randomUUID().toString();
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(true)
      .withPoLineId(poLineId);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    String poLinePaymentStatus = "No Change";
    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToPayInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);
    assertTrue(result.succeeded());
  }

  @Test
  public void updatePoLinePaymentStatusUsingParameterWithoutReleaseEncumbranceTest() {
    String poLineId = UUID.randomUUID().toString();
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(false)
      .withPoLineId(poLineId);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withPaymentStatus(PAYMENT_NOT_REQUIRED);
    String poLinePaymentStatus = "Cancelled";

    when(orderLineService.getPoLinesByIdAndQuery(eq(List.of(poLineId)), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(List.of()));

    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToPayInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);

    assertTrue(result.succeeded());
    assertEquals(PAYMENT_NOT_REQUIRED, poLine.getPaymentStatus());
  }

  @Test
  public void updatePoLinePaymentStatusUsingParameterWithoutPoLinesTest() {
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(true);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    String poLinePaymentStatus = "Cancelled";
    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToPayInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);
    assertTrue(result.succeeded());
  }

  @Test
  void testOrderLinePaymentStatusAwaitingPayment() throws IOException {
    PoLine poLine = new JsonObject(
      getMockData("mockdata/compositeOrders/e9496a5c-84d1-4f95-89ad-a764be51ca29.json"))
      .mapTo(PoLine.class);
    Map<PoLine, PoLine.PaymentStatus> poLinesWithStatus = Map.ofEntries(Map.entry(poLine, FULLY_PAID));
    boolean paymentRequired = poLinePaymentStatusUpdateService.isPaymentStatusUpdateRequired(poLinesWithStatus, poLine);

    Assertions.assertTrue(paymentRequired);
  }

  @Test
  void testOrderLinePaymentStatusPaymentNotRequiredIgnored() throws IOException {
    PoLine poLine = new JsonObject(
      getMockData("mockdata/compositeOrders/443bcf4c-41e9-4a07-8e70-dcc71ca56069.json"))
      .mapTo(PoLine.class);
    Map<PoLine, PoLine.PaymentStatus> poLinesWithStatus = Map.ofEntries(Map.entry(poLine, FULLY_PAID));
    boolean paymentRequired = poLinePaymentStatusUpdateService.isPaymentStatusUpdateRequired(poLinesWithStatus, poLine);

    Assertions.assertFalse(paymentRequired);
  }

  @Test
  @DisplayName("not decide to update status of POLines with ONGOING status")
  void shouldReturnFalseWhenCheckingForUpdatePoLinePaymentStatusIsOngoing() throws IOException {
    PoLine ongoingpoLine = new JsonObject(getMockData(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID)))
      .mapTo(PoLine.class)
      .withPaymentStatus(ONGOING);

    PoLine fullyPaidpoLine = new JsonObject(getMockData(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID)))
      .mapTo(PoLine.class)
      .withPaymentStatus(FULLY_PAID);

    Map<PoLine, PoLine.PaymentStatus> poLinesWithStatus = new HashMap<>() {{
      put(ongoingpoLine, ONGOING);
      put(fullyPaidpoLine, FULLY_PAID);
    }};

    assertFalse(poLinePaymentStatusUpdateService.isPaymentStatusUpdateRequired(poLinesWithStatus, ongoingpoLine));
  }

  @Test
  @DisplayName("decide to update status of POLines with different statuses")
  void shouldReturnTrueWhenCompositeCheckingForUpdatePoLinePaymentStatusIsDifferentValues() throws IOException {
    PoLine ongoingpoLine = new JsonObject(getMockData(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID)))
      .mapTo(PoLine.class)
      .withPaymentStatus(ONGOING);

    PoLine fullyPaidpoLine = new JsonObject(getMockData(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID)))
      .mapTo(PoLine.class)
      .withPaymentStatus(FULLY_PAID);

    Map<PoLine, PoLine.PaymentStatus> poLinesWithStatus = new HashMap<>() {{
      put(ongoingpoLine, ONGOING);
      put(fullyPaidpoLine, PENDING);
    }};

    assertTrue(poLinePaymentStatusUpdateService.isPaymentStatusUpdateRequired(poLinesWithStatus, fullyPaidpoLine));
  }

}
