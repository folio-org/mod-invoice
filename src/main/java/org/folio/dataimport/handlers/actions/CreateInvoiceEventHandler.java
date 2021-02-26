package org.folio.dataimport.handlers.actions;

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
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.impl.InvoiceLineHelper;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.ActionProfile.Action.CREATE;
import static org.folio.ActionProfile.FolioRecord.INVOICE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;

public class CreateInvoiceEventHandler implements EventHandler {

  private static final Logger LOGGER = LogManager.getLogger(CreateInvoiceEventHandler.class);
  private static final String PAYLOAD_HAS_NO_DATA_MSG = "Failed to handle event payload, cause event payload context does not contain EDIFACT data";

  public static final String INVOICE_LINES_KEY = "INVOICE_LINES";
  public static final String DI_INVOICE_CREATED_EVENT = "DI_INVOICE_CREATED";
  private static final String INVOICE_FIELD = "invoice";
  private static final String INVOICE_LINES_FIELD = "invoiceLines";

  @Override
  public CompletableFuture<DataImportEventPayload> handle(DataImportEventPayload dataImportEventPayload) {
    CompletableFuture<DataImportEventPayload> future = new CompletableFuture<>();
    dataImportEventPayload.setEventType(DI_INVOICE_CREATED_EVENT);
    try {
      HashMap<String, String> payloadContext = dataImportEventPayload.getContext();
      if (payloadContext == null || isBlank(payloadContext.get(EntityType.EDIFACT_INVOICE.value()))) {
        LOGGER.error(PAYLOAD_HAS_NO_DATA_MSG);
        return CompletableFuture.failedFuture(new EventProcessingException(PAYLOAD_HAS_NO_DATA_MSG));
      }

      prepareEventPayloadForMapping(dataImportEventPayload);
      MappingManager.map(dataImportEventPayload);
      prepareMappingResult(dataImportEventPayload);

      Map<String, String> okapiHeaders = getOkapiHeaders(dataImportEventPayload);
      saveInvoice(dataImportEventPayload, okapiHeaders)
        .thenCompose(savedInvoice -> saveInvoiceLines(savedInvoice.getId(), dataImportEventPayload, okapiHeaders))
        .whenComplete((savedInvoiceLines, throwable) -> {
          if (throwable == null) {
            InvoiceLineCollection invoiceLines = new InvoiceLineCollection().withInvoiceLines(savedInvoiceLines).withTotalRecords(savedInvoiceLines.size());
            dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, Json.encode(invoiceLines));
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

    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, Vertx.currentContext(), null);
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

  private void prepareMappingResult(DataImportEventPayload dataImportEventPayload) {
    JsonObject mappingResult = new JsonObject(dataImportEventPayload.getContext().get(INVOICE.value()));
    JsonObject invoiceJson = mappingResult.getJsonObject(INVOICE_FIELD);
    dataImportEventPayload.getContext().put(INVOICE_LINES_KEY, invoiceJson.remove(INVOICE_LINES_FIELD).toString());
    dataImportEventPayload.getContext().put(INVOICE.value(), invoiceJson.encode());
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
