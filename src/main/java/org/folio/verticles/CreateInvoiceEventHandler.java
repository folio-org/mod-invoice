package org.folio.verticles;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.ActionProfile;
import org.folio.DataImportEventPayload;
import org.folio.processing.events.services.handler.EventHandler;
import org.folio.processing.exceptions.EventProcessingException;
import org.folio.processing.mapping.MappingManager;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.impl.InvoiceLineHelper;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ActionProfile.FolioRecord.INVOICE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;

public class CreateInvoiceEventHandler implements EventHandler {

  private static final Logger LOGGER = LogManager.getLogger(CreateInvoiceEventHandler.class);

  private static final String PAYLOAD_HAS_NO_DATA_MSG = "Failed to handle event payload, cause event payload context does not contain EDIFACT data";
  private static final String PO_LINES_BY_POL_NUMBER_CQL = "poLineNumber==(%s)";
  private static final String PO_LINES_BY_REF_NUMBER_CQL = "vendorDetail.referenceNumbers=(%s)";
  private static final String REF_NUMBER_CRITERIA_PATTERN = "\"\\\"refNumber\\\":\\\"%s\\\"\"";

  public static final String INVOICE_FIELD = "invoice";
  public static final String INVOICE_LINES_FIELD = "invoiceLines";
  public static final String DEFAULT_LANG = "en";
  public static final String INVOICE_LINES_KEY = "INVOICE_LINES";
  public static final String POL_TITLE_KEY = "POL_TITLE_%s";
  public static final String POL_NUMBER_KEY = "POL_NUMBER_%s";
  public static final String POL_FUND_DISTRIBUTIONS_KEY = "POL_FUND_DISTRIBUTIONS_%s";
  public static final String DI_INVOICE_CREATED_EVENT = "DI_INVOICE_CREATED";

  private RestClient orderLinesRestClient;

  public CreateInvoiceEventHandler(RestClient orderLinesRestClient) {
    this.orderLinesRestClient = orderLinesRestClient;
  }

  @Override
  public CompletableFuture<DataImportEventPayload> handle(DataImportEventPayload dataImportEventPayload) {
    CompletableFuture<DataImportEventPayload> future = new CompletableFuture<>();
    try {
      HashMap<String, String> payloadContext = dataImportEventPayload.getContext();
      if (payloadContext == null || isBlank(payloadContext.get(EntityType.EDIFACT_INVOICE.value()))) {
        LOGGER.error(PAYLOAD_HAS_NO_DATA_MSG);
        return CompletableFuture.failedFuture(new EventProcessingException(PAYLOAD_HAS_NO_DATA_MSG));
      }

      Map<String, String> okapiHeaders = getOkapiHeaders(dataImportEventPayload);

      Map<Integer, String> invoiceLineNoToPoLineNo = new HashMap<>();
      Map<Integer, String> invoiceLineNoToRefNo = new HashMap<>();

      getAssociatedPoLinesByPoLineNumber(invoiceLineNoToPoLineNo, okapiHeaders)
        .thenCompose(associatedPoLineMap -> {
          if (associatedPoLineMap.size() < invoiceLineNoToPoLineNo.size()) {
            return getAssociatedPoLinesByRefNumber(invoiceLineNoToRefNo, okapiHeaders)
              .thenApply(poLinesMap -> {
                associatedPoLineMap.putAll(poLinesMap);
                return associatedPoLineMap;
              });
          }
          return CompletableFuture.completedFuture(associatedPoLineMap);
        })
        .thenAccept(invLineNoToPoLine -> ensureAdditionalData(dataImportEventPayload, invLineNoToPoLine))
        .thenAccept(v -> prepareEventPayloadForMapping(dataImportEventPayload))
        .thenAccept(v -> MappingManager.map(dataImportEventPayload))
        .thenAccept(v -> prepareMappingResult(dataImportEventPayload))
        .thenCompose(v -> saveInvoice(dataImportEventPayload, okapiHeaders))
        .thenCompose(savedInvoice -> saveInvoiceLines(savedInvoice.getId(), dataImportEventPayload, okapiHeaders))
        .whenComplete((savedInvoiceLines, throwable) -> {
          dataImportEventPayload.setEventType(DI_INVOICE_CREATED_EVENT);
          if (throwable == null) {
            dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, Json.encode(savedInvoiceLines));
            future.complete(dataImportEventPayload);
          } else {
            LOGGER.error("Error during creation invoice and invoice lines", throwable);
            future.completeExceptionally(throwable);
          }
        });
    } catch (Exception e) {
      LOGGER.error("Error during creation invoice and invoice lines", e);
      future.completeExceptionally(e);
    }
    return future;
  }

  private void prepareMappingResult(DataImportEventPayload dataImportEventPayload) {
    JsonObject mappingResult = new JsonObject(dataImportEventPayload.getContext().get(INVOICE.value()));
    JsonObject invoiceJson = mappingResult.getJsonObject(INVOICE_FIELD);
    dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, invoiceJson.remove(INVOICE_LINES_FIELD).toString());
    dataImportEventPayload.getContext().put(INVOICE.value(), invoiceJson.encode());
  }

  private CompletableFuture<Map<Integer, PoLine>> getAssociatedPoLinesByPoLineNumber(Map<Integer, String> invoiceLineNoToPoLineNo, Map<String, String> okapiHeaders) {
    Map<Integer, PoLine> invoiceLineNoToPoLine = new HashMap<>();
    String preparedCql = prepareQueryGetPoLinesByNumber(List.copyOf(invoiceLineNoToPoLineNo.values()));

    return orderLinesRestClient.get(preparedCql, 0, Integer.MAX_VALUE, buildRequestContext(Vertx.currentContext() , okapiHeaders), PoLineCollection.class)
      .thenApply(poLineCollection -> poLineCollection.getPoLines().stream().collect(Collectors.toMap(PoLine::getPoLineNumber, poLine -> poLine)))
      .thenAccept(poLineNumberToPoLine -> invoiceLineNoToPoLineNo
        .forEach((key, value) -> poLineNumberToPoLine.computeIfPresent(value, (polNo, poLine) -> invoiceLineNoToPoLine.put(key, poLine))))
      .thenApply(v -> invoiceLineNoToPoLine);
  }

  private CompletableFuture<Map<Integer, PoLine>> getAssociatedPoLinesByRefNumber(Map<Integer, String> invoiceLineNoToRefNo, Map<String, String> okapiHeaders) {
    Map<Integer, PoLine> invoiceLineNoToPoLine = new HashMap<>();
    String cqlGetPoLinesByRefNo = prepareQueryGetPoLinesByRefNumber(List.copyOf(invoiceLineNoToRefNo.values()));

    return orderLinesRestClient.get(cqlGetPoLinesByRefNo, 0, Integer.MAX_VALUE, buildRequestContext(Vertx.currentContext(), okapiHeaders), PoLineCollection.class)
      .thenAccept(poLineCollection -> invoiceLineNoToRefNo.forEach((key, value) -> poLineCollection.getPoLines().stream()
        .filter(poLine -> poLine.getVendorDetail().getReferenceNumbers().stream()
          .anyMatch(refNumberItem -> value.equals(refNumberItem.getRefNumber())))
        .findFirst()
        .ifPresent(poLine -> invoiceLineNoToPoLine.put(key, poLine))))
      .thenApply(v -> invoiceLineNoToPoLine);
  }

  private String prepareQueryGetPoLinesByNumber(List<String> poLineNumbers) {
    String valueString = poLineNumbers.stream()
      .map(number -> format("\"%s\"", number))
      .collect(Collectors.joining(" OR "));

    return format(PO_LINES_BY_POL_NUMBER_CQL, valueString);
  }

  private String prepareQueryGetPoLinesByRefNumber(List<String> referenceNumbers) {
    String valueString = referenceNumbers.stream()
      .map(refNumber -> format(REF_NUMBER_CRITERIA_PATTERN, refNumber))
      .collect(Collectors.joining(" OR "));

    return format(PO_LINES_BY_REF_NUMBER_CQL, valueString);
  }

  private CompletableFuture<Invoice> saveInvoice(DataImportEventPayload dataImportEventPayload, Map<String, String> okapiHeaders) {
    JsonObject invoiceJson = new JsonObject(dataImportEventPayload.getContext().get(INVOICE.value()));
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, Vertx.currentContext(), null);

    return invoiceHelper.createInvoice(invoiceJson.mapTo(Invoice.class)).thenApply(invoice -> {
      dataImportEventPayload.getContext().put(INVOICE.value(), Json.encode(invoice));
      return invoice;
    });
  }

  private CompletableFuture<List<InvoiceLine>> saveInvoiceLines(String invoiceId, DataImportEventPayload dataImportEventPayload, Map<String, String> okapiHeaders) {
    ArrayList<CompletableFuture<InvoiceLine>> futures = new ArrayList<>();

    JsonArray invoiceLinesJson = new JsonArray(dataImportEventPayload.getContext().get(INVOICE_LINES_KEY));
    List<InvoiceLine> invoiceLines = invoiceLinesJson.stream()
      .map(JsonObject.class::cast)
      .map(json -> json.mapTo(InvoiceLine.class))
      .peek(invoiceLine -> invoiceLine.setInvoiceId(invoiceId))
      .collect(Collectors.toList());

    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, Vertx.currentContext(), DEFAULT_LANG);
    invoiceLines.forEach(invoiceLine -> futures.add(helper.createInvoiceLine(invoiceLine)));

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream()
        .filter(future -> !future.isCompletedExceptionally())
        .map(future -> future.join())
        .collect(Collectors.toList()));
  }

  private Map<String, String> getOkapiHeaders(DataImportEventPayload dataImportEventPayload) {
    return Map.of(RestVerticle.OKAPI_HEADER_TENANT, dataImportEventPayload.getTenant(),
      RestVerticle.OKAPI_HEADER_TOKEN, dataImportEventPayload.getToken(),
      RestConstants.OKAPI_URL, dataImportEventPayload.getOkapiUrl());
  }

  private void prepareEventPayloadForMapping(DataImportEventPayload dataImportEventPayload) {
    dataImportEventPayload.getEventsChain().add(dataImportEventPayload.getEventType());
    dataImportEventPayload.setCurrentNode(dataImportEventPayload.getCurrentNode().getChildSnapshotWrappers().get(0));
    dataImportEventPayload.getContext().put(INVOICE.value(), new JsonObject().encode());
  }

  private void ensureAdditionalData(DataImportEventPayload dataImportEventPayload, Map<Integer, PoLine> invoiceLineNoToPoLine) {
    for (Entry<Integer, PoLine> pair : invoiceLineNoToPoLine.entrySet()) {
      dataImportEventPayload.getContext().put(format(POL_TITLE_KEY, pair.getKey()), pair.getValue().getTitleOrPackage());
      dataImportEventPayload.getContext().put(format(POL_NUMBER_KEY, pair.getKey()), pair.getValue().getPoLineNumber());
      dataImportEventPayload.getContext().put(format(POL_FUND_DISTRIBUTIONS_KEY, pair.getKey()), Json.encode(pair.getValue().getFundDistribution()));
    }
  }

  private RequestContext buildRequestContext(Context context, Map<String, String> okapiHeaders) {
    return new RequestContext(context, okapiHeaders);
  }

  @Override
  public boolean isEligible(DataImportEventPayload dataImportEventPayload) {
    if (dataImportEventPayload.getCurrentNode() != null && ACTION_PROFILE == dataImportEventPayload.getCurrentNode().getContentType()) {
      ActionProfile actionProfile = JsonObject.mapFrom(dataImportEventPayload.getCurrentNode().getContent()).mapTo(ActionProfile.class);
      return actionProfile.getAction() == CREATE && actionProfile.getFolioRecord() == INVOICE;
    }
    return false;
  }
}
