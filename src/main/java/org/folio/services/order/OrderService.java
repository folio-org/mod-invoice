package org.folio.services.order;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_INVOICE_LINE;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.ResourcePathResolver.COMPOSITE_ORDER;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_INVOICE_RELATIONSHIP;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationshipCollection;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PurchaseOrder;
import org.folio.rest.acq.model.orders.PurchaseOrderCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.invoice.InvoiceLineService;

public class OrderService {
  private static final Logger logger = LogManager.getLogger();

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

  public Future<List<PoLine>> getOrderPoLines(String orderId, RequestContext requestContext) {
    return getOrder(orderId, requestContext)
      .map(CompositePurchaseOrder::getPoLines);
  }

  public Future<List<PurchaseOrder>> getOrders(String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDERS_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry,  PurchaseOrderCollection.class, requestContext)
      .map(PurchaseOrderCollection::getPurchaseOrders);
  }

  public Future<CompositePurchaseOrder> getOrder(String orderId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDERS_BY_ID_ENDPOINT).withId(orderId);
    return restClient.get(requestEntry, CompositePurchaseOrder.class, requestContext);
  }


  public Future<Void> createInvoiceOrderRelation(InvoiceLine invoiceLine, RequestContext requestContext) {
    if (invoiceLine.getPoLineId() == null) {
      return succeededFuture(null);
    }
    return orderLineService.getPoLineById(invoiceLine.getPoLineId(), requestContext)
      .compose(poLine -> getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceLine.getInvoiceId(), requestContext)
        .compose(relationships -> {
          if (relationships.getTotalRecords() == 0) {
            return createOrderInvoiceRelationship(new OrderInvoiceRelationship()
                .withInvoiceId(invoiceLine.getInvoiceId())
                .withPurchaseOrderId(poLine.getPurchaseOrderId()), requestContext)
              .compose(v -> succeededFuture());
          }
          return succeededFuture();
        }));
  }

  public Future<OrderInvoiceRelationshipCollection> getOrderInvoiceRelationshipByOrderIdAndInvoiceId(String orderId, String invoiceId, RequestContext requestContext) {
    String query = String.format(ORDER_INVOICE_RELATIONSHIP_QUERY, orderId, invoiceId);
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(100);
    return restClient.get(requestEntry, OrderInvoiceRelationshipCollection.class, requestContext);
  }

  public Future<OrderInvoiceRelationshipCollection> getOrderInvoiceRelationshipByInvoiceId(String invoiceId, RequestContext requestContext) {
    String query = String.format(ORDER_INVOICE_RELATIONSHIP_BY_INVOICE_ID_QUERY, invoiceId);
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(100);
    return restClient.get(requestEntry, OrderInvoiceRelationshipCollection.class, requestContext);
  }

  public Future<OrderInvoiceRelationship> createOrderInvoiceRelationship(OrderInvoiceRelationship relationship,
    RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_ENDPOINT);
    return restClient.post(requestEntry, relationship, OrderInvoiceRelationship.class, requestContext);
  }

  public Future<Void> deleteOrderInvoiceRelationshipById(String id, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_INVOICE_RELATIONSHIPS_BY_ID_ENDPOINT).withId(id);
    return restClient.delete(requestEntry, requestContext);
  }

  public Future<Void> deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(String invoiceId, String poLineId, RequestContext requestContext) {
    return orderLineService.getPoLineById(poLineId, requestContext)
      .compose(poLine -> getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceId, requestContext))
      .compose(relation -> {
          if (relation.getTotalRecords() > 0) {
            return deleteOrderInvoiceRelationshipById(relation.getOrderInvoiceRelationships().get(0).getId(), requestContext);
          }
          return succeededFuture(null);
      });
  }

  public Future<Void> deleteOrderInvoiceRelationshipByInvoiceId(String invoiceId, RequestContext requestContext) {
    return getOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContext)
      .compose(relation -> {
        if (relation.getTotalRecords() > 0) {
          List<String> ids = relation.getOrderInvoiceRelationships().stream().map(OrderInvoiceRelationship::getId).toList();
          return deleteOrderInvoiceRelations(ids, requestContext);
        }
        return succeededFuture(null);
      });
  }

  public Future<Boolean> isInvoiceLineLastForOrder(InvoiceLine invoiceLine, RequestContext requestContext) {
    return orderLineService.getPoLineById(invoiceLine.getPoLineId(), requestContext)
      .map(PoLine::getPurchaseOrderId)
      .compose(orderId -> getOrderPoLines(orderId, requestContext)
        .map(poLines -> poLines.stream()
          .map(PoLine::getId).toList()))
      .compose(poLineIds -> invoiceLineService.getInvoiceLinesRelatedForOrder(poLineIds, invoiceLine.getInvoiceId(), requestContext))
      .map(invoiceLines -> invoiceLines.size() == 1);
  }

  public Future<Void> deleteOrderInvoiceRelationIfLastInvoice(String invoiceLineId, RequestContext requestContext) {
    return invoiceLineService.getInvoiceLine(invoiceLineId, requestContext)
      .compose(invoiceLine -> {
        if (invoiceLine.getPoLineId() == null) return succeededFuture(null);
        return isInvoiceLineLastForOrder(invoiceLine, requestContext)
          .compose(isLastOrder -> Boolean.TRUE.equals(isLastOrder)
            ? deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceLine.getInvoiceId(), invoiceLine.getPoLineId(), requestContext)
            : succeededFuture(null));
      })
      .recover(throwable -> {
        logger.error("Can't delete Order Invoice relation for invoice line: {}", invoiceLineId, throwable);
        var param = new Parameter().withKey("lineId").withValue(invoiceLineId);
        var causeParam = new Parameter().withKey("cause").withValue(throwable.getMessage());
        throw new HttpException(404, CANNOT_DELETE_INVOICE_LINE, List.of(param, causeParam));
      });
  }

  private Future<Void> deleteOrderInvoiceRelations(List<String> relationIds, RequestContext requestContext) {
    List<Future<Void>> futures = new ArrayList<>();
    relationIds.forEach(id ->
      futures.add(deleteOrderInvoiceRelationshipById(id, requestContext))
    );
    return collectResultsOnSuccess(futures)
      .onSuccess(v -> logger.info("Number of deleted relations between order and invoices: {}", relationIds.size()))
      .mapEmpty();
  }
}
