package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class InvoiceHelper extends AbstractHelper {

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";

	InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
		super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
	}

  public CompletableFuture<Invoice> createPurchaseOrder(Invoice invoice) {
    return generateFolioInvoiceNumber()
      .thenCompose(folioInvoiceNumber -> createInvoice(invoice.withFolioInvoiceNo(folioInvoiceNumber)));
  }

  private CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return createRecordInStorage(JsonObject.mapFrom(invoice), resourcesPath(INVOICES))
      .thenApply(invoice::withId);
  }

  private CompletableFuture<String> generateFolioInvoiceNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(FOLIO_INVOICE_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

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

}
