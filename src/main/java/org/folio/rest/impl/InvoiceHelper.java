package org.folio.rest.impl;

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

import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryOperators;

import io.vertx.core.Context;

public class InvoiceHelper extends AbstractHelper {

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";
  private static final String DELETE_INVOICE_BY_ID = resourceByIdPath(INVOICES, "%s") + "?lang=%s";

  InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return generateFolioInvoiceNumber()
      .thenCompose(folioInvoiceNumber -> {
        JsonObject jsonInvoice = JsonObject.mapFrom (invoice.withFolioInvoiceNo(folioInvoiceNumber));
        return createRecordInStorage(jsonInvoice, resourcesPath(INVOICES));
      })
      .thenApply(id -> {
        calculateTotals(invoice);
        return invoice.withId(id);
      });
  }

  private CompletableFuture<String> generateFolioInvoiceNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(FOLIO_INVOICE_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

  private void calculateTotals(Invoice invoice) {
    calculateTotals(invoice, Collections.emptyList());
  }

  private void calculateTotals(Invoice invoice, List<InvoiceLine> lines) {
    CurrencyUnit currency = Monetary.getCurrency(invoice.getCurrency());
    Money zero = Money.of(0, currency);

    // 1. Sub-total
    MonetaryAmount subTotal;
    if (CollectionUtils.isNotEmpty(lines)) {
      subTotal = lines.stream()
        .map(InvoiceLine::getSubTotal)
        .map(amount -> Money.of(amount, currency))
        .reduce(zero, Money::add);
    } else {
      subTotal = zero;
    }
    invoice.setSubTotal(convertToDouble(subTotal));

    // 2. Adjustments
    MonetaryAmount adjustmentsTotal = invoice.getAdjustments()
      .stream()
      .filter(adj -> !adj.getProRateAdjustment())
      .map(adj -> calculateTotal(adj, subTotal))
      .reduce(zero, MonetaryAmount::add);
    invoice.setAdjustmentsTotal(convertToDouble(adjustmentsTotal));

    // 3. Total
    if (!invoice.getLockTotal()) {
      invoice.setTotal(convertToDouble(subTotal.add(adjustmentsTotal)));
    }
  }

  private double convertToDouble(MonetaryAmount amount) {
    return amount.getNumber().doubleValue();
  }

  private MonetaryAmount calculateTotal(Adjustment adjustment, MonetaryAmount subTotal) {
    Double adjustmentValue = adjustment.getAdjustmentValue();
    if (adjustment.getAdjustmentType() == Adjustment.AdjustmentType.PERCENTAGE) {
      return MonetaryOperators.percent(adjustmentValue)
        .apply(subTotal);
    } else {
      return Money.of(adjustmentValue, subTotal.getCurrency());
    }
  }

  /**
   * Gets invoice by id
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public CompletableFuture<Invoice> getInvoice(String id) {
    CompletableFuture<Invoice> future = new VertxCompletableFuture<>(ctx);
    getInvoiceById(id, lang, httpClient, ctx, okapiHeaders, logger).thenApply(jsonInvoice -> {
      if (logger.isInfoEnabled()) {
        logger.info("Successfully retrieved invoice by id: " + jsonInvoice.encodePrettily());
      }
      return jsonInvoice.mapTo(Invoice.class);
    })
      .thenCompose(invoice -> getInvoiceLines(invoice.getId()).thenApply(lines -> {
        calculateTotals(invoice, lines);
        return invoice;
      }))
      .thenAccept(future::complete)
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

    return setSystemGeneratedData(invoice)
      .thenCompose(updatedInvoice -> {
        JsonObject jsonInvoice = JsonObject.mapFrom(updatedInvoice);
        return handlePutRequest(resourceByIdPath(INVOICES, invoice.getId()), jsonInvoice, httpClient, ctx, okapiHeaders, logger);
      });
  }

  private CompletableFuture<Invoice> setSystemGeneratedData(Invoice invoice) {
    return getInvoice(invoice.getId())
      .thenApply(invoiceFromStorage -> invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo()));
  }

  private CompletableFuture<List<InvoiceLine>> getInvoiceLines(String invoiceId) {
    InvoiceLineHelper helper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
    return helper.getInvoiceLines(999, 0, "invoiceId==" + invoiceId)
      .thenApply(InvoiceLineCollection::getInvoiceLines);
  }
}
