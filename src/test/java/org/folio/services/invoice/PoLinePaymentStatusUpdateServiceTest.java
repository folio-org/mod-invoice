package org.folio.services.invoice;

import io.vertx.core.Future;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.order.OrderLineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class PoLinePaymentStatusUpdateServiceTest {

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
  public void updatePoLinePaymentStatusToApproveInvoiceUsingParameterTest() {
    String poLineId = UUID.randomUUID().toString();
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(true)
      .withPoLineId(poLineId);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withPaymentStatus(PoLine.PaymentStatus.AWAITING_PAYMENT);
    List<PoLine> poLines = List.of(poLine);
    String poLinePaymentStatus = "Fully Paid";

    when(orderLineService.getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLines));
    when(orderLineService.updatePoLines(anyList(), eq(requestContext)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToApproveInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);

    assertTrue(result.succeeded());
    assertEquals(PoLine.PaymentStatus.FULLY_PAID, poLine.getPaymentStatus());
    verify(orderLineService, times(1)).getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext));
    verify(orderLineService, times(1)).updatePoLines(anyList(), eq(requestContext));
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
      .withPaymentStatus(PoLine.PaymentStatus.AWAITING_PAYMENT);
    List<PoLine> poLines = List.of(poLine);
    String poLinePaymentStatus = "Cancelled";

    when(orderLineService.getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLines));
    when(orderLineService.updatePoLines(anyList(), eq(requestContext)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToCancelInvoice(null,
      invoiceLines, poLinePaymentStatus, requestContext);

    assertTrue(result.succeeded());
    assertEquals(PoLine.PaymentStatus.CANCELLED, poLine.getPaymentStatus());
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
    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToApproveInvoice(invoiceLines,
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
    String poLinePaymentStatus = "Cancelled";
    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToApproveInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);
    assertTrue(result.succeeded());
  }

  @Test
  public void updatePoLinePaymentStatusUsingParameterWithoutPoLinesTest() {
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(true);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    String poLinePaymentStatus = "Cancelled";
    Future<Void> result = poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToApproveInvoice(invoiceLines,
      poLinePaymentStatus, requestContext);
    assertTrue(result.succeeded());
  }

}
