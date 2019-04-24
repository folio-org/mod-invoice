package org.folio.rest.impl;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.rest.jaxrs.model.InvoiceCollection;

import io.vertx.core.Context;
import io.vertx.core.logging.Logger;

public class InvoiceHelper extends AbstractHelper {

	private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s&lang=%s";

	InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
		super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
	}

	/**
	 * @param query  string representing CQL query
	 * @param logger {@link Logger} to log error if any
	 * @return URL encoded string
	 */
	public static String encodeQuery(String query, Logger logger) {
		try {
			return URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			logger.error("Error happened while attempting to encode '{}'", e, query);
			throw new CompletionException(e);
		}
	}

	public static String getEndpointWithQuery(String query, Logger logger) {
		return isEmpty(query) ? EMPTY : "&query=" + encodeQuery(query, logger);
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
