package org.folio.services.order;

import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.ResourcePathResolver.COMPOSITE_ORDER;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_INVOICE_RELATIONSHIP;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationshipCollection;
import org.folio.rest.acq.model.orders.PurchaseOrder;
import org.folio.rest.acq.model.orders.PurchaseOrderCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.invoice.InvoiceLineService;

public class OrderService {
  private static final Logger LOG = LogManager.getLogger(OrderService.class);

  private static final String ORDER_INVOICE_RELATIONSHIP_QUERY = "purchaseOrderId==%s and invoiceId==%s";
  private static final String ORDER_INVOICE_RELATIONSHIP_BY_INVOICE_ID_QUERY = "invoiceId==%s";
  private static final String ORDERS_ENDPOINT = resourcesPath(COMPOSITE_ORDER);
  private static final String ORDERS_BY_ID_ENDPOINT = ORDERS_ENDPOINT + "/{id}";
  private static final String ORDER_INVOICE_RELATIONSHIPS_ENDPOINT = resourcesPath(ORDER_INVOICE_RELATIONSHIP);
  private static final String ORDER_INVOICE_RELATIONSHIPS_BY_ID_ENDPOINT = ORDER_INVOICE_RELATIONSHIPS_ENDPOINT + "/{id}";

  private final RestClient restClient;

  private final InvoiceLineService invoiceLineService;

  private final OrderLineService orderLineService;

  public OrderService(RestClient restClient, InvoiceLineService invoiceLineService,
                      OrderLineService orderLineService) {
    this.restClient = restClient;
    this.invoiceLineService = invoiceLineService;
    this.orderLineService = orderLineService;
  }

  public CompletableFuture<List<CompositePoLine>> getOrderPoLines(String orderId, RequestContext requestContext) {
    return getOrder(orderId, requestContext)
      .thenApply(CompositePurchaseOrder::getCompositePoLines);
  }

  public CompletableFuture<List<PurchaseOrder>> getOrders(String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDERS_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, requestContext, PurchaseOrderCollection.class)
      .thenApply(PurchaseOrderCollection::getPurchaseOrders);
  }

  public CompletableFuture<CompositePurchaseOrder> getOrder(String orderId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDERS_BY_ID_ENDPOINT).withId(orderId);
    return restClient.get(requestEntry, requestContext, CompositePurchaseOrder.class);
  }


  public CompletableFuture<Void> createInvoiceOrderRelation(InvoiceLine invoiceLine, RequestContext requestContext) {
    if (invoiceLine.getPoLineId() == null) return CompletableFuture.completedFuture(null);
    return orderLineService.getPoLine(invoiceLine.getPoLineId(), requestContext)
      .thenCompose(poLine -> getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceLine.getInvoiceId(), requestContext)
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

  public CompletableFuture<OrderInvoiceRelationshipCollection> getOrderInvoiceRelationshipByOrderIdAndInvoiceId(String orderId, String invoiceId, RequestContext requestContext) {
    String query = String.format(ORDER_INVOICE_RELATIONSHIP_QUERY, orderId, invoiceId);
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(100);
    return restClient.get(requestEntry, requestContext, OrderInvoiceRelationshipCollection.class);
  }

  public CompletableFuture<OrderInvoiceRelationshipCollection> getOrderInvoiceRelationshipByInvoiceId(String invoiceId, RequestContext requestContext) {
    String query = String.format(ORDER_INVOICE_RELATIONSHIP_BY_INVOICE_ID_QUERY, invoiceId);
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(100);
    return restClient.get(requestEntry, requestContext, OrderInvoiceRelationshipCollection.class);
  }

  public CompletableFuture<OrderInvoiceRelationship> createOrderInvoiceRelationship(OrderInvoiceRelationship relationship,
    RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_ENDPOINT);
    return restClient.post(requestEntry, relationship, requestContext, OrderInvoiceRelationship.class);
  }

  public CompletableFuture<Void> deleteOrderInvoiceRelationshipById(String id, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_BY_ID_ENDPOINT).withId(id);
    return restClient.delete(requestEntry, requestContext);
  }

  public CompletableFuture<Void> deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(String invoiceId, String poLineId, RequestContext requestContext) {
    return orderLineService.getPoLine(poLineId, requestContext)
      .thenCompose(poLine -> getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceId, requestContext))
      .thenCompose(relation -> {
          if (relation.getTotalRecords() > 0) {
            return deleteOrderInvoiceRelationshipById(relation.getOrderInvoiceRelationships().get(0).getId(), requestContext);
          }
          return CompletableFuture.completedFuture(null);
      });
  }

  public CompletableFuture<Void> deleteOrderInvoiceRelationshipByInvoiceId(String invoiceId, RequestContext requestContext) {
    return getOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContext)
      .thenCompose(relation -> {
        if (relation.getTotalRecords() > 0) {
          List<String> ids = relation.getOrderInvoiceRelationships().stream().map(OrderInvoiceRelationship::getId).collect(Collectors.toList());
          return deleteOrderInvoiceRelations(ids, requestContext);
        }
        return CompletableFuture.completedFuture(null);
      });
  }

  public CompletableFuture<Boolean> isInvoiceLineLastForOrder(InvoiceLine invoiceLine, RequestContext requestContext) {
    return orderLineService.getPoLine(invoiceLine.getPoLineId(), requestContext)
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
            ? deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceLine.getInvoiceId(), invoiceLine.getPoLineId(), requestContext)
            : CompletableFuture.completedFuture(null));
      });
  }


  private CompletableFuture<Void> deleteOrderInvoiceRelations(List<String> relationIds, RequestContext requestContext) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    relationIds.forEach(id ->
      futures.add(deleteOrderInvoiceRelationshipById(id, requestContext))
    );
    return collectResultsOnSuccess(futures).thenAccept(result -> LOG.debug("Number of deleted relations between order and invoices: " + result.size()));
  }
}
