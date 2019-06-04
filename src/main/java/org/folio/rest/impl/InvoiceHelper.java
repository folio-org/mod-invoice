package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.calculateAdjustment;
import static org.folio.invoices.utils.HelperUtils.convertToDouble;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.javamoney.moneta.Money;

import io.vertx.core.Context;

public class InvoiceHelper extends AbstractHelper {

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";
  private static final String DELETE_INVOICE_BY_ID = resourceByIdPath(INVOICES, "%s") + "?lang=%s";

  InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return generateFolioInvoiceNumber()
      .thenApply(invoice::withFolioInvoiceNo)
      .thenApply(JsonObject::mapFrom)
      .thenCompose(jsonInvoice -> createRecordInStorage(jsonInvoice, resourcesPath(INVOICES)))
      .thenApply(invoice::withId)
      .thenApply(this::withCalculatedTotals);
  }

  /**
   * Gets invoice by id and calculates totals
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public CompletableFuture<Invoice> getInvoice(String id) {
    CompletableFuture<Invoice> future = new VertxCompletableFuture<>(ctx);
    getInvoiceRecord(id)
      // To calculate totals, related invoice lines have to be retrieved
      .thenCompose(invoice -> getInvoiceLines(invoice).thenApply(lines -> withCalculatedTotals(invoice, lines)))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        logger.error("Failed to get an Invoice by id={}", t.getCause(), id);
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  /**
   * Gets invoice by id without calculating totals
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public CompletableFuture<Invoice> getInvoiceRecord(String id) {
    return getInvoiceById(id, lang, httpClient, ctx, okapiHeaders, logger);
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

  public CompletableFuture<Void> updateInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    logger.debug("Updating invoice...");

    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
    JsonObject jsonInvoice = JsonObject.mapFrom(invoice);
    return handlePutRequest(resourceByIdPath(INVOICES, invoice.getId()), jsonInvoice, httpClient, ctx, okapiHeaders, logger);
  }

  private CompletableFuture<String> generateFolioInvoiceNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(FOLIO_INVOICE_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

  private Invoice withCalculatedTotals(Invoice invoice) {
    return withCalculatedTotals(invoice, Collections.emptyList());
  }

  private Invoice withCalculatedTotals(Invoice invoice, List<InvoiceLine> lines) {
    CurrencyUnit currency = Monetary.getCurrency(invoice.getCurrency());
    Money zero = Money.of(0, currency);

    if (lines == null) {
      lines = Collections.emptyList();
    }

    // 1. Sub-total
    MonetaryAmount subTotal = lines.stream()
      .map(InvoiceLine::getSubTotal)
      .map(amount -> Money.of(amount, currency))
      .reduce(zero, Money::add);

    // 2. Adjustments (sum of not prorated and "In addition to" invoice level and all invoice line level adjustments)
    MonetaryAmount invoiceLinesAdjTotal = lines.stream()
      .map(invoiceLine -> Money.of(invoiceLine.getAdjustmentsTotal(), currency))
      .reduce(zero, Money::add);

    MonetaryAmount adjustmentsTotal = invoice.getAdjustments()
      .stream()
      .filter(adj -> adj.getProrate() != Adjustment.Prorate.NOT_PRORATED)
      .filter(adj -> adj.getRelationToTotal() == Adjustment.RelationToTotal.IN_ADDITION_TO)
      .map(adj -> calculateAdjustment(adj, subTotal))
      .reduce(zero, MonetaryAmount::add)
      .add(invoiceLinesAdjTotal);

    // 3. Total
    if (!invoice.getLockTotal()) {
      invoice.setTotal(convertToDouble(subTotal.add(adjustmentsTotal)));
    }
    invoice.setAdjustmentsTotal(convertToDouble(adjustmentsTotal));
    invoice.setSubTotal(convertToDouble(subTotal));

    return invoice;
  }

  private CompletableFuture<List<InvoiceLine>> getInvoiceLines(Invoice invoice) {
    InvoiceLineHelper helper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
    return helper.getInvoiceLines(999, 0, "invoiceId==" + invoice.getId())
      .thenApply(InvoiceLineCollection::getInvoiceLines)
      .thenApply(lines -> {
        lines.forEach(line -> helper.calculateInvoiceLineTotals(line, invoice));
        return lines;
      });
  }
}
