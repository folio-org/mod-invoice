package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.CompositePoLine;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;


public class InvoiceHelper extends AbstractHelper {

  private final InvoiceLineHelper invoiceLineHelper;

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";
  private static final String DELETE_INVOICE_BY_ID = resourceByIdPath(INVOICES, "%s") + "?lang=%s";
  private static final String PO_LINE_BY_ID_ENDPOINT = "/orders/order-lines/%s?lang=%s";

  InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return generateFolioInvoiceNumber()
      .thenCompose(folioInvoiceNumber -> {
        JsonObject jsonInvoice = JsonObject.mapFrom (invoice.withFolioInvoiceNo(folioInvoiceNumber));
        return createRecordInStorage(jsonInvoice, resourcesPath(INVOICES));
      })
      .thenApply(invoice::withId);
  }

  private CompletableFuture<String> generateFolioInvoiceNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(FOLIO_INVOICE_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

  /**
   * Gets invoice by id
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public CompletableFuture<Invoice> getInvoice(String id) {
    CompletableFuture<Invoice> future = new VertxCompletableFuture<>(ctx);
    getInvoiceById(id, lang, httpClient, ctx, okapiHeaders, logger)
    .thenAccept(jsonInvoice -> {
      logger.info("Successfully retrieved invoice by id: " + jsonInvoice.encodePrettily());
      future.complete(jsonInvoice.mapTo(Invoice.class));
    })
    .exceptionally(t -> {
      logger.error("Failed to build an Invoice", t.getCause());
      future.completeExceptionally(t);
      return null;
    });
    return future;
  }
  
  /**
   * Gets list of invoice
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link InvoiceCollection} on success or an exception if processing fails
   */
  public CompletableFuture<InvoiceCollection> getInvoices(int limit, int offset, String query) {
    CompletableFuture<InvoiceCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String queryParam = getEndpointWithQuery(query, logger);
      String endpoint = String.format(GET_INVOICES_BY_QUERY, limit, offset, queryParam, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenAccept(jsonInvoices -> {
        logger.info("Successfully retrieved invoices: " + jsonInvoices.encodePrettily());
        future.complete(jsonInvoices.mapTo(InvoiceCollection.class));
      })
      .exceptionally(t -> {
        logger.error("Error getting invoices", t);
        future.completeExceptionally(t);
        return null;
      });
    } catch (Exception e) {
        future.completeExceptionally(e);
    }
    return future;
  }

  public CompletableFuture<Void> deleteInvoice(String id) {
    return handleDeleteRequest(String.format(DELETE_INVOICE_BY_ID, id, lang), httpClient, ctx, okapiHeaders, logger);
  }

  public CompletableFuture<Void> updateInvoice(Invoice invoice) {
    logger.debug("Updating invoice...");

    return getInvoice(invoice.getId())
      .thenApply(invoiceFromStorage -> {
        setSystemGeneratedData(invoiceFromStorage, invoice);
        return invoiceFromStorage;
      })
      .thenCompose(invoiceFromStorage -> {
        if (isTransitionToPaid(invoiceFromStorage, invoice)) {
          return payInvoice(invoice);
        } else {
          return updateInvoiceRecord(invoice);
        }
      });
  }


  private CompletableFuture<Void> updateInvoiceRecord(Invoice updatedInvoice) {
    JsonObject jsonInvoice = JsonObject.mapFrom(updatedInvoice);
    return handlePutRequest(resourceByIdPath(INVOICES, updatedInvoice.getId()), jsonInvoice, httpClient, ctx, okapiHeaders, logger);
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
  }

  private boolean isTransitionToPaid(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.PAID;
  }

  /**
   * Handles transition of given invoice to PAID status.
   *
   * @param invoice Invoice to paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payInvoice(Invoice invoice) {
    return fetchInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(this::groupInvoiceLinesByPoLineId)
      .thenCompose(this::fetchPoLines)
      .thenApply(this::updatePoLinesPaymentStatus)
      .thenCompose(this::updateCompositePoLines)
      .thenCompose(aVoid -> updateInvoiceRecord(invoice));
  }


  private CompletableFuture<List<InvoiceLine>> fetchInvoiceLinesByInvoiceId(String invoiceId) {
    String query = "invoiceId==" + invoiceId;
    // Assuming that the invoice will never contain more than Integer.MAX_VALUE invoiceLines.
    return invoiceLineHelper.getInvoiceLines(Integer.MAX_VALUE, 0, query)
      .thenApply(InvoiceLineCollection::getInvoiceLines);
  }

  private Map<String, List<InvoiceLine>>  groupInvoiceLinesByPoLineId(List<InvoiceLine> invoiceLines) {
    if (CollectionUtils.isEmpty(invoiceLines)) {
      return Collections.emptyMap();
    }
    return invoiceLines
      .stream()
      .collect(Collectors.groupingBy(InvoiceLine::getPoLineId));
  }

  private CompletableFuture<Map<CompositePoLine, List<InvoiceLine>>> fetchPoLines(Map<String, List<InvoiceLine>> poLineIdsWithInvoiceLines) {
    List<CompletableFuture<CompositePoLine>> futures = poLineIdsWithInvoiceLines.keySet()
      .stream()
      .map(this::getPoLineById)
      .collect(toList());

    return VertxCompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toMap(poLine -> poLine, poLine -> poLineIdsWithInvoiceLines.get(poLine.getId()))));
  }

  private CompletableFuture<CompositePoLine> getPoLineById(String poLineId) {
    return handleGetRequest(String.format(PO_LINE_BY_ID_ENDPOINT, poLineId, lang), httpClient, ctx, okapiHeaders, logger)
      .thenApply(jsonObject -> jsonObject.mapTo(CompositePoLine.class))
      .exceptionally(throwable -> {
        logger.error("Exception retrieving poLine with id={}:", throwable, poLineId);
        throw new HttpException(500, String.format("PoLine with id=%s not found", poLineId));
      });
  }

  private List<CompositePoLine> updatePoLinesPaymentStatus(Map<CompositePoLine, List<InvoiceLine>> compositePoLinesWithInvoiceLines) {
    return compositePoLinesWithInvoiceLines.keySet().stream()
      .peek(compositePoLine -> updatePoLinePaymentStatus(compositePoLine, compositePoLinesWithInvoiceLines.get(compositePoLine)))
      .collect(Collectors.toList());
  }

  private void updatePoLinePaymentStatus(CompositePoLine compositePoLine, List<InvoiceLine> invoiceLines) {
    if (isAnyInvoiceLineReleaseEncumbrance(invoiceLines)) {
      compositePoLine.setPaymentStatus(CompositePoLine.PaymentStatus.FULLY_PAID);
    } else {
      compositePoLine.setPaymentStatus(CompositePoLine.PaymentStatus.PARTIALLY_PAID);
    }
  }

  private boolean isAnyInvoiceLineReleaseEncumbrance(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream().anyMatch(InvoiceLine::getReleaseEncumbrance);
  }

  private CompletionStage<Void> updateCompositePoLines(List<CompositePoLine> poLines) {
    return VertxCompletableFuture.allOf(poLines.stream()
      .map(JsonObject::mapFrom)
      .map(poLine -> handlePutRequest(String.format(PO_LINE_BY_ID_ENDPOINT, poLine.getString(ID), lang), poLine, httpClient, ctx, okapiHeaders, logger))
      .toArray(CompletableFuture[]::new));
  }

}
