package org.folio.services.invoice;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import one.util.streamex.StreamEx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.order.OrderLineService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;
import static org.folio.rest.acq.model.orders.CompositePoLine.PaymentStatus.ONGOING;
import static org.folio.rest.acq.model.orders.CompositePoLine.PaymentStatus.PAYMENT_NOT_REQUIRED;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.AWAITING_PAYMENT;
import static org.folio.rest.acq.model.orders.PoLine.PaymentStatus.PARTIALLY_PAID;


public class PoLinePaymentStatusUpdateService {

  private static final String PAYMENT_STATUS_PAID_QUERY = "paymentStatus==(\"Fully Paid\" OR \"Partially Paid\")";
  private static final String PO_LINE_WITH_ONE_TIME_OPEN_ORDER_QUERY =
    "purchaseOrder.orderType==\"One-Time\" AND purchaseOrder.workflowStatus==\"Open\"";
  private static final String AND = " AND ";
  private static final Set<CompositePoLine.PaymentStatus> PO_LINE_PAYMENT_IGNORED_STATUSES =
    EnumSet.of(ONGOING, PAYMENT_NOT_REQUIRED);

  private static final Logger logger = LogManager.getLogger();

  private final InvoiceLineService invoiceLineService;
  private final InvoiceService invoiceService;
  private final OrderLineService orderLineService;


  public PoLinePaymentStatusUpdateService(InvoiceLineService invoiceLineService, InvoiceService invoiceService,
      OrderLineService orderLineService) {
    this.invoiceLineService = invoiceLineService;
    this.invoiceService = invoiceService;
    this.orderLineService = orderLineService;
  }

  public Future<Void> updatePoLinePaymentStatusToPayInvoice(List<InvoiceLine> invoiceLines, String poLinePaymentStatus,
      RequestContext requestContext) {
    if (poLinePaymentStatus == null) {
      return payPoLines(invoiceLines, requestContext);
    }
    return updatePoLinePaymentStatusUsingParameter(invoiceLines, poLinePaymentStatus, requestContext);
  }

  public Future<Void> updatePoLinePaymentStatusToCancelInvoice(Invoice invoiceFromStorage, List<InvoiceLine> invoiceLines,
      String poLinePaymentStatus, RequestContext requestContext) {
    if (poLinePaymentStatus != null) {
      return updatePoLinePaymentStatusUsingParameter(invoiceLines, poLinePaymentStatus, requestContext);
    }
    String invoiceId = invoiceFromStorage.getId();
    if (!Invoice.Status.PAID.equals(invoiceFromStorage.getStatus()) || invoiceFromStorage.getFiscalYearId() == null) {
      // in the unlikely case the fiscal year is undefined, the payment status update is skipped
      // (the MODINVOSTO-177 script should fill it up for pre-Poppy invoices)
      logger.info("updatePoLinePaymentStatus:: The invoice was not paid or the fiscal year is unknown; " +
        "skipping po line paymentStatus update. invoiceId={}", invoiceId);
      return succeededFuture();
    }
    List<String> allPoLineIds = invoiceLines.stream()
      .map(InvoiceLine::getPoLineId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (allPoLineIds.isEmpty()) {
      return succeededFuture();
    }
    logger.info("updatePoLinePaymentStatus:: Retrieving linked po lines that might need a paymentStatus update, " +
      "invoiceId={}...", invoiceId);
    return orderLineService.getPoLinesByIdAndQuery(allPoLineIds, this::queryToGetPoLinesWithFullyOrPartiallyPaidPaymentStatusByIds, requestContext)
      .compose(filteredPoLines -> {
        if (filteredPoLines.isEmpty()) {
          logger.info("updatePoLinePaymentStatus:: No matching po line, no paymentStatus update needed, invoiceId={}",
            invoiceId);
          return succeededFuture();
        }
        logger.info("updatePoLinePaymentStatus:: There are po lines linked to the invoice that might need a paymentStatus update;" +
          " retrieving paid invoice lines linked to the po lines...");
        List<String> filteredPoLineIds = filteredPoLines.stream().map(PoLine::getId).toList();
        return getInvoiceLinesByPoLineIdsAndQuery(filteredPoLineIds,
          ids -> queryToGetRelatedPaidInvoiceLinesByPoLineIds(invoiceId, ids), requestContext)
          .compose(relatedInvoiceLines -> {
            if (relatedInvoiceLines.isEmpty()) {
              logger.info("updatePoLinePaymentStatus:: No linked paid invoice line; setting paymentStatus to Awaiting Payment...");
              return updateRelatedPoLines(relatedInvoiceLines, filteredPoLines, emptyList(), invoiceId, requestContext);
            }
            logger.info("updatePoLinePaymentStatus:: There are linked paid invoice lines; " +
              "retrieving the related invoices to select only invoices with the same fiscal year as the cancelled invoice...");
            List<String> relatedInvoiceIds = relatedInvoiceLines.stream().map(InvoiceLine::getInvoiceId).distinct().toList();
            String invoiceQuery = "status==Paid AND fiscalYearId==" + invoiceFromStorage.getFiscalYearId();
            return getInvoicesByIdsAndQuery(relatedInvoiceIds, invoiceQuery, requestContext)
              .compose(relatedInvoices -> updateRelatedPoLines(relatedInvoiceLines, filteredPoLines, relatedInvoices,
                invoiceId, requestContext));
          });
      });
  }

  /**
   * Updates payment status of the associated PO Lines (when poLinePaymentStatus is not used)
   *
   * @param invoiceLines the invoice lines to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private Future<Void> payPoLines(List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    Map<String, List<InvoiceLine>> poLineIdInvoiceLinesMap = groupInvoiceLinesByPoLineId(invoiceLines);
    return fetchPoLines(poLineIdInvoiceLinesMap, requestContext)
      .map(this::updatePoLinesPaymentStatus)
      .compose(poLines -> orderLineService.updateCompositePoLines(poLines, requestContext));
  }

  private Map<String, List<InvoiceLine>>  groupInvoiceLinesByPoLineId(List<InvoiceLine> invoiceLines) {
    if (CollectionUtils.isEmpty(invoiceLines)) {
      return Collections.emptyMap();
    }
    return invoiceLines
      .stream()
      .filter(invoiceLine -> StringUtils.isNotEmpty(invoiceLine.getPoLineId()))
      .collect(Collectors.groupingBy(InvoiceLine::getPoLineId));
  }

  /**
   * Retrieves PO Lines associated with invoice lines and calculates expected PO Line's payment status
   * @param poLineIdsWithInvoiceLines map where key is PO Line id and value is list of associated invoice lines
   * @return map where key is {@link CompositePoLine} and value is expected PO Line's payment status
   */
  private Future<Map<CompositePoLine, CompositePoLine.PaymentStatus>> fetchPoLines(Map<String, List<InvoiceLine>> poLineIdsWithInvoiceLines,
    RequestContext requestContext) {
    List<Future<CompositePoLine>> futures = poLineIdsWithInvoiceLines.keySet()
      .stream()
      .map(poLine -> orderLineService.getPoLine(poLine, requestContext))
      .toList();

    return collectResultsOnSuccess(futures)
      .map(compositePoLines ->  compositePoLines.stream()
        .collect(Collectors.toMap(poLine -> poLine, poLine -> getPoLinePaymentStatus(poLineIdsWithInvoiceLines.get(poLine.getId())))));
  }

  private List<CompositePoLine> updatePoLinesPaymentStatus(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithNewStatuses) {
    return compositePoLinesWithNewStatuses
      .keySet().stream()
      .filter(compositePoLine -> isPaymentStatusUpdateRequired(compositePoLinesWithNewStatuses, compositePoLine))
      .map(compositePoLine -> updatePaymentStatus(compositePoLinesWithNewStatuses, compositePoLine))
      .toList();
  }

  @VisibleForTesting
  boolean isPaymentStatusUpdateRequired(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus,
      CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus = compositePoLinesWithStatus.get(compositePoLine);
    return (!newPaymentStatus.equals(compositePoLine.getPaymentStatus()) &&
      !PO_LINE_PAYMENT_IGNORED_STATUSES.contains(compositePoLine.getPaymentStatus()));
  }

  private CompositePoLine updatePaymentStatus(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus,
      CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus = compositePoLinesWithStatus.get(compositePoLine);
    compositePoLine.setPaymentStatus(newPaymentStatus);
    return compositePoLine;
  }

  private CompositePoLine.PaymentStatus getPoLinePaymentStatus(List<InvoiceLine> invoiceLines) {
    if (isAnyInvoiceLineReleaseEncumbrance(invoiceLines)) {
      return CompositePoLine.PaymentStatus.FULLY_PAID;
    } else {
      return CompositePoLine.PaymentStatus.PARTIALLY_PAID;
    }
  }

  private boolean isAnyInvoiceLineReleaseEncumbrance(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream().anyMatch(InvoiceLine::getReleaseEncumbrance);
  }

  private Future<Void> updatePoLinePaymentStatusUsingParameter(List<InvoiceLine> invoiceLines,
      String poLinePaymentStatus, RequestContext requestContext) {
    logger.info("updatePoLinePaymentStatusUsingParameter:: Update po line paymentStatus using parameter...");
    if ("No Change".equals(poLinePaymentStatus)) {
      logger.info("updatePoLinePaymentStatusUsingParameter:: No change requested");
      return Future.succeededFuture();
    }
    if (invoiceLines.stream().noneMatch(InvoiceLine::getReleaseEncumbrance)) {
      logger.info("updatePoLinePaymentStatusUsingParameter:: No invoice line with releaseEncumbrance=true");
      return Future.succeededFuture();
    }
    List<String> poLineIds = invoiceLines.stream()
      .filter(InvoiceLine::getReleaseEncumbrance)
      .map(InvoiceLine::getPoLineId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (poLineIds.isEmpty()) {
      logger.info("updatePoLinePaymentStatusUsingParameter:: No po line linked to an invoice line with releaseEncumbrance=true");
      return succeededFuture();
    }
    return orderLineService.getPoLinesByIdAndQuery(poLineIds, this::queryPoLinesWithOneTimeOpenOrder, requestContext)
      .map(poLines -> {
        poLines.forEach(poLine -> poLine.setPaymentStatus(PoLine.PaymentStatus.fromValue(poLinePaymentStatus)));
        return poLines;
      }).compose(poLines -> orderLineService.updatePoLines(poLines, requestContext))
      .onSuccess(v -> logger.info("updatePoLinePaymentStatusUsingParameter:: Success updating po lines"))
      .onFailure(t -> logger.error("updatePoLinePaymentStatusUsingParameter:: Error updating po lines", t));
  }

  private String queryToGetPoLinesWithFullyOrPartiallyPaidPaymentStatusByIds(List<String> poLineIds) {
    return PAYMENT_STATUS_PAID_QUERY + AND + convertIdsToCqlQuery(poLineIds);
  }

  private String queryToGetRelatedPaidInvoiceLinesByPoLineIds(String invoiceId, List<String> poLineIds) {
    return "invoiceId<>" + invoiceId + " AND invoiceLineStatus==(\"Paid\") AND " +
      convertIdsToCqlQuery(poLineIds, "poLineId", true);
  }

  private Future<List<InvoiceLine>> getInvoiceLinesByPoLineIdsAndQuery(List<String> poLineIds,
      Function<List<String>, String> queryFunction, RequestContext requestContext) {
    List<Future<List<InvoiceLine>>> futureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(queryFunction)
      .map(query -> invoiceLineService.getInvoiceLinesByQuery(query, requestContext))
      .toList();

    return collectResultsOnSuccess(futureList)
      .map(col -> col.stream().flatMap(List::stream).toList());
  }

  private Future<List<Invoice>> getInvoicesByIdsAndQuery(List<String> invoiceIds, String query,
      RequestContext requestContext) {
    String query2 = "(" + query + ") AND " + convertIdsToCqlQuery(invoiceIds);
    return invoiceService.getInvoices(query2, 0, Integer.MAX_VALUE, requestContext)
      .map(InvoiceCollection::getInvoices);
  }

  private Future<Void> updateRelatedPoLines(List<InvoiceLine> allRelatedInvoiceLines, List<PoLine> filteredPoLines,
      List<Invoice> relatedInvoices, String invoiceId, RequestContext requestContext) {
    List<String> relatedInvoiceIds = relatedInvoices.stream().map(Invoice::getId).toList();
    List<InvoiceLine> filteredInvoiceLines = allRelatedInvoiceLines.stream()
      .filter(invoiceLine -> relatedInvoiceIds.contains(invoiceLine.getInvoiceId()))
      .toList();
    Map<String, List<InvoiceLine>> poLineIdToInvoiceLines = filteredInvoiceLines.stream()
      .collect(groupingBy(InvoiceLine::getPoLineId));
    List<PoLine> modifiedPoLines = new ArrayList<>();
    filteredPoLines.forEach(poLine -> {
      List<InvoiceLine> relatedInvoiceLines = poLineIdToInvoiceLines.get(poLine.getId());
      if (relatedInvoiceLines == null || relatedInvoiceLines.isEmpty()) {
        poLine.setPaymentStatus(AWAITING_PAYMENT);
        modifiedPoLines.add(poLine);
      } else if (relatedInvoiceLines.stream().noneMatch(InvoiceLine::getReleaseEncumbrance) &&
        !PARTIALLY_PAID.equals(poLine.getPaymentStatus())) {
        poLine.setPaymentStatus(PARTIALLY_PAID);
        modifiedPoLines.add(poLine);
      }
    });
    if (modifiedPoLines.isEmpty()) {
      logger.info("updateRelatedPoLines:: No po line paymentStatus update needed, invoiceId={}", invoiceId);
      return succeededFuture();
    }
    logger.info("updateRelatedPoLines:: {} po lines need a paymentStatus update for invoice {}. Updating...",
      modifiedPoLines.size(), invoiceId);
    List<CompositePoLine> compositePoLines = modifiedPoLines.stream()
      .map(poLine -> JsonObject.mapFrom(poLine).mapTo(CompositePoLine.class))
      .toList();
    return orderLineService.updateCompositePoLines(compositePoLines, requestContext);
  }

  private String queryPoLinesWithOneTimeOpenOrder(List<String> poLineIds) {
    return PO_LINE_WITH_ONE_TIME_OPEN_ORDER_QUERY + AND + convertIdsToCqlQuery(poLineIds);
  }

}
