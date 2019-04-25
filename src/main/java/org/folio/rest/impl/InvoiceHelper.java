package org.folio.rest.impl;

import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.rest.jaxrs.model.InvoiceCollection;

import io.vertx.core.Context;

public class InvoiceHelper extends AbstractHelper {

	private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";

	InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
		super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
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
