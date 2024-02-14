package org.folio.services.order;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_INVOICE_LINE;
import static org.folio.invoices.utils.ErrorCodes.USER_NOT_A_MEMBER_OF_THE_ACQ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationshipCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Error;
import org.folio.services.invoice.InvoiceLineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
  @InjectMocks
  private OrderLineService orderLineServiceInject;

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

  @Test
  void shouldRethrowUserNotAMemberOfTheAcq(VertxTestContext vertxTestContext) {
    // given
    doReturn(failedFuture(new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_NOT_A_MEMBER_OF_THE_ACQ.toError())))
      .when(restClient).put(any(RequestEntry.class), any(CompositePoLine.class), eq(requestContextMock));

    // when
    Future<Void> future = orderLineServiceInject.updateCompositePoLines(List.of(new CompositePoLine()), requestContextMock);

    // then
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        HttpException httpException = (HttpException) result.cause();
        assertEquals(HttpStatus.HTTP_FORBIDDEN.toInt(), httpException.getCode());
        Error error = httpException.getErrors().getErrors().get(0);
        assertEquals(USER_NOT_A_MEMBER_OF_THE_ACQ.getCode(), error.getCode());
        vertxTestContext.completeNow();
      });
  }

  @Test
  void shouldThrowNotFoundErrorWhenDeletingOrderInvoiceRelationIfLastInvoice(VertxTestContext vertxTestContext) {
    String invoiceLineId = UUID.randomUUID().toString();
    String errorMsg = "Not Found";
    doReturn(failedFuture(new HttpException(404, errorMsg)))
      .when(invoiceLineService).getInvoiceLine(invoiceLineId, requestContextMock);

    // when
    Future<Void> future = orderService.deleteOrderInvoiceRelationIfLastInvoice(invoiceLineId, requestContextMock);

    // then
    vertxTestContext.assertFailure(future)
      .onComplete(event -> {
        HttpException httpException = (HttpException) event.cause();
        assertEquals(404, httpException.getCode());
        Error error = httpException.getErrors().getErrors().get(0);
        assertEquals(CANNOT_DELETE_INVOICE_LINE.getCode(), error.getCode());
        assertEquals(CANNOT_DELETE_INVOICE_LINE.getDescription() + " : " + errorMsg, error.getMessage());
        assertEquals("lineId", error.getParameters().get(0).getKey());
        assertEquals(invoiceLineId, error.getParameters().get(0).getValue());
        vertxTestContext.completeNow();
      });
  }
}
