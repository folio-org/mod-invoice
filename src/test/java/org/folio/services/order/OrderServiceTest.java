package org.folio.services.order;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
  void shouldDeleteOrderInvoiceRelationshipByInvoiceIdAndLineIdIfRelationExist() {
    String invoiceId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    CompositePoLine poLine = new CompositePoLine().withId(poLineId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationship relationship = new OrderInvoiceRelationship().withInvoiceId(invoiceId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withOrderInvoiceRelationships(List.of(relationship)).withTotalRecords(1);


    doReturn(completedFuture(poLine)).when(restClient).get(any(RequestEntry.class), eq(requestContextMock), eq(CompositePoLine.class));
    doReturn(completedFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(requestContextMock), eq(OrderInvoiceRelationshipCollection.class));
    doReturn(completedFuture(null)).when(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
    doReturn(completedFuture(poLine)).when(orderLineService).getPoLine(poLineId, requestContextMock);
    orderService.deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceId, poLineId, requestContextMock).join();

    verify(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
  }

  @Test
  void shouldSkipDeletionOrderInvoiceRelationshipByInvoiceIdAndLineIdIfRelationNotExist() {
    String invoiceId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    CompositePoLine poLine = new CompositePoLine().withId(poLineId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withOrderInvoiceRelationships(Collections.EMPTY_LIST).withTotalRecords(0);


    doReturn(completedFuture(poLine)).when(restClient).get(any(RequestEntry.class), eq(requestContextMock), eq(CompositePoLine.class));
    doReturn(completedFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(requestContextMock), eq(OrderInvoiceRelationshipCollection.class));
    doReturn(completedFuture(poLine)).when(orderLineService).getPoLine(poLineId, requestContextMock);
    orderService.deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceId, poLineId, requestContextMock).join();

    verify(restClient, times(0)).delete(any(RequestEntry.class), eq(requestContextMock));
  }

  @Test
  void shouldDeleteOrderInvoiceRelationshipByInvoiceIdIfRelationExist() {
    String invoiceId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    OrderInvoiceRelationship relationship = new OrderInvoiceRelationship().withInvoiceId(invoiceId).withPurchaseOrderId(orderId);
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withOrderInvoiceRelationships(List.of(relationship)).withTotalRecords(1);

    doReturn(completedFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(requestContextMock), eq(OrderInvoiceRelationshipCollection.class));
    doReturn(completedFuture(null)).when(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
    orderService.deleteOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContextMock).join();

    verify(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
  }


  @Test
  void shouldNotDeleteOrderInvoiceRelationshipByInvoiceIdIfRelationNoExist() {
    String invoiceId = UUID.randomUUID().toString();
    OrderInvoiceRelationshipCollection relationships = new OrderInvoiceRelationshipCollection().withTotalRecords(0);

    doReturn(completedFuture(relationships)).when(restClient).get(any(RequestEntry.class), eq(requestContextMock), eq(OrderInvoiceRelationshipCollection.class));
    doReturn(completedFuture(null)).when(restClient).delete(any(RequestEntry.class), eq(requestContextMock));
    orderService.deleteOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContextMock).join();

    verify(restClient, times(0)).delete(any(RequestEntry.class), eq(requestContextMock));
  }
}
