package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.rest.jaxrs.model.InvoiceLine;

import io.vertx.core.Context;

public class InvoiceLinesHelper extends AbstractHelper {

  InvoiceLinesHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<InvoiceLine> getInvoiceLines(String id) {
    CompletableFuture<InvoiceLine> future = new VertxCompletableFuture<>(ctx);
    try {
      String endpoint = String.format(resourceByIdPath("invoiceLines") + id, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonInvoiceLines -> {
          logger.info("Successfully retrieved invoice lines: " + jsonInvoiceLines.encodePrettily());
          future.complete(jsonInvoiceLines.mapTo(InvoiceLine.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting invoice lines", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }
}
