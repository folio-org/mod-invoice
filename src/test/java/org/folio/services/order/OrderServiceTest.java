package org.folio.services.order;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationshipCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.services.invoice.InvoiceLineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class OrderServiceTest {
  @InjectMocks
  private OrderService orderService;
  @Mock
  private RestClient restClient;
  @Mock
  private InvoiceLineService invoiceLineService;
  @Mock
  private RequestContext requestContextMock;
  @Mock
  private OrderLineService orderLineService;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }


  @Test
  void shouldDeleteOrderInvoiceRelationshipByInvoiceIdAndLineIdIfRelationExist(VertxTestContext vertxTestContext) {
    String invoiceId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    CompositePoLine poLine = new CompositePoLine().withId(poLineId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationship relationship = new OrderInvoiceRelationship().withInvoiceId(invoiceId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withOrderInvoiceRelationships(List.of(relationship)).withTotalRecords(1);


    doReturn(succeededFuture(poLine)).when(restClient).get(any(RequestEntry.class), eq(CompositePoLine.class), eq(requestContextMock));
    doReturn(succeededFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(OrderInvoiceRelationshipCollection.class), eq(requestContextMock));
    doReturn(succeededFuture(null)).when(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
    doReturn(succeededFuture(poLine)).when(orderLineService).getPoLine(poLineId, requestContextMock);

    var future = orderService.deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceId, poLineId, requestContextMock);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> {
        verify(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);
  }

  @Test
  void shouldSkipDeletionOrderInvoiceRelationshipByInvoiceIdAndLineIdIfRelationNotExist(VertxTestContext vertxTestContext) {
    String invoiceId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    CompositePoLine poLine = new CompositePoLine().withId(poLineId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withOrderInvoiceRelationships(Collections.EMPTY_LIST).withTotalRecords(0);


    doReturn(succeededFuture(poLine)).when(restClient).get(any(RequestEntry.class), eq(CompositePoLine.class), eq(requestContextMock));
    doReturn(succeededFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(OrderInvoiceRelationshipCollection.class), eq(requestContextMock));

    doReturn(succeededFuture(poLine)).when(orderLineService).getPoLine(poLineId, requestContextMock);

    var future = orderService.deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceId, poLineId, requestContextMock);

    vertxTestContext.assertComplete(future)
      .onSuccess(result -> {
        verify(restClient, times(0)).delete(any(RequestEntry.class), eq(requestContextMock));
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);

  }

  @Test
  void shouldDeleteOrderInvoiceRelationshipByInvoiceIdIfRelationExist(VertxTestContext vertxTestContext) {
    String invoiceId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    OrderInvoiceRelationship relationship = new OrderInvoiceRelationship().withInvoiceId(invoiceId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withOrderInvoiceRelationships(List.of(relationship)).withTotalRecords(1);

    doReturn(succeededFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(OrderInvoiceRelationshipCollection.class), eq(requestContextMock));
    doReturn(succeededFuture(null)).when(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
    var future = orderService.deleteOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContextMock);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> {
        verify(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);
  }


  @Test
  void shouldNotDeleteOrderInvoiceRelationshipByInvoiceIdIfRelationNoExist() {
    String invoiceId = UUID.randomUUID().toString();
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withTotalRecords(0);

    doReturn(succeededFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(OrderInvoiceRelationshipCollection.class), eq(requestContextMock));
    doReturn(succeededFuture(null)).when(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
    var future = orderService.deleteOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContextMock);


    verify(restClient, times(0)).delete(any(RequestEntry.class), eq(requestContextMock));
  }
}
