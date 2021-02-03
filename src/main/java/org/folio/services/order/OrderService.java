package org.folio.services.order;

import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationshipCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.invoice.InvoiceLineService;

public class OrderService {

  private static final String ORDER_INVOICE_RELATIONSHIP_QUERY = "purchaseOrderId==%s and invoiceId==%s";

  private final RestClient orderRestClient;
  private final RestClient orderLinesRestClient;
  private final RestClient orderInvoiceRelationshipRestClient;

  private final InvoiceLineService invoiceLineService;

  public OrderService(RestClient orderRestClient, RestClient orderLinesRestClient, RestClient orderInvoiceRelationshipRestClient,
    InvoiceLineService invoiceLineService) {
    this.orderRestClient = orderRestClient;
    this.orderLinesRestClient = orderLinesRestClient;
    this.orderInvoiceRelationshipRestClient = orderInvoiceRelationshipRestClient;
    this.invoiceLineService = invoiceLineService;
  }

  public CompletableFuture<List<CompositePoLine>> getOrderPoLines(String orderId, RequestContext requestContext) {
    return getOrder(orderId, requestContext)
      .thenApply(CompositePurchaseOrder::getCompositePoLines);
  }

  private CompletableFuture<CompositePurchaseOrder> getOrder(String orderId, RequestContext requestContext) {
    return orderRestClient.getById(orderId, requestContext, CompositePurchaseOrder.class);
  }

  public CompletableFuture<CompositePoLine> getPoLine(String poLineId, RequestContext requestContext) {
    return orderLinesRestClient.getById(poLineId, requestContext, CompositePoLine.class)
      .exceptionally(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("poLineId").withValue(poLineId));
        throw new HttpException(404, PO_LINE_NOT_FOUND.toError().withParameters(parameters));
      });
  }

  public CompletableFuture<Void> createInvoiceOrderRelation(InvoiceLine invoiceLine, RequestContext requestContext) {
    if (invoiceLine.getPoLineId() == null) return CompletableFuture.completedFuture(null);
    return getPoLine(invoiceLine.getPoLineId(), requestContext)
      .thenCompose(poLine -> getOrderInvoiceRelationship(poLine.getPurchaseOrderId(), invoiceLine.getInvoiceId(), requestContext)
        .thenCompose(relationships -> {
          if (relationships.getTotalRecords() == 0) {
            return createOrderInvoiceRelationship(
              new OrderInvoiceRelationship().withInvoiceId(invoiceLine.getInvoiceId())
                .withPurchaseOrderId(poLine.getPurchaseOrderId()), requestContext)
              .thenCompose(v -> CompletableFuture.completedFuture(null));
          }
          return CompletableFuture.completedFuture(null);
        }));
  }

  public CompletableFuture<OrderInvoiceRelationshipCollection> getOrderInvoiceRelationship(String orderId, String invoiceId, RequestContext requestContext) {
    String query = String.format(ORDER_INVOICE_RELATIONSHIP_QUERY, orderId, invoiceId);
    return orderInvoiceRelationshipRestClient.get(query,0 , 100, requestContext, OrderInvoiceRelationshipCollection.class);
  }

  public CompletableFuture<OrderInvoiceRelationship> createOrderInvoiceRelationship(OrderInvoiceRelationship relationship,
    RequestContext requestContext) {
    return orderInvoiceRelationshipRestClient.post(relationship, requestContext, OrderInvoiceRelationship.class);
  }

  public CompletableFuture<Void> deleteOrderInvoiceRelationship(String id, RequestContext requestContext) {
    return orderInvoiceRelationshipRestClient.delete(id, requestContext);
  }

  public CompletableFuture<Void> deleteOrderInvoiceRelationship(String invoiceId, String poLineId, RequestContext requestContext) {
    return getPoLine(poLineId, requestContext)
      .thenCompose(poLine -> getOrderInvoiceRelationship(poLine.getPurchaseOrderId(), invoiceId, requestContext)
        .thenApply(relation -> relation.getOrderInvoiceRelationships().get(0).getId())
        .thenCompose(id -> deleteOrderInvoiceRelationship(id, requestContext)));
  }

  public CompletableFuture<Boolean> isInvoiceLineLastForOrder(InvoiceLine invoiceLine, RequestContext requestContext) {
    return getPoLine(invoiceLine.getPoLineId(), requestContext)
      .thenApply(CompositePoLine::getPurchaseOrderId)
      .thenCompose(orderId -> getOrderPoLines(orderId, requestContext)
        .thenApply(compositePoLines -> compositePoLines.stream()
          .map(CompositePoLine::getId).collect(Collectors.toList())))
      .thenCompose(poLineIds -> invoiceLineService.getInvoiceLinesRelatedForOrder(poLineIds, invoiceLine.getInvoiceId(), requestContext))
      .thenApply(invoiceLines -> invoiceLines.size() == 1);
  }

  public CompletableFuture<Void> deleteOrderInvoiceRelationIfLastInvoice(String invoiceLineId, RequestContext requestContext) {
    return invoiceLineService.getInvoiceLine(invoiceLineId, requestContext)
      .thenCompose(invoiceLine -> {
        if (invoiceLine.getPoLineId() == null) return CompletableFuture.completedFuture(null);
        return isInvoiceLineLastForOrder(invoiceLine, requestContext)
          .thenCompose(isLastOrder -> Boolean.TRUE.equals(isLastOrder)
            ? deleteOrderInvoiceRelationship(invoiceLine.getInvoiceId(), invoiceLine.getPoLineId(), requestContext)
            : CompletableFuture.completedFuture(null));
      });
  }
}
