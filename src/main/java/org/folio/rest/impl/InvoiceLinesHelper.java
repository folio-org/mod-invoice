package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getInvoiceLineById;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.rest.jaxrs.model.InvoiceLine;

import io.vertx.core.Context;

public class InvoiceLinesHelper extends AbstractHelper {

  private static final String DELETE_INVOICE_LINE_BY_ID = resourceByIdPath(INVOICE_LINES, "%s") + "?lang=%s";

  InvoiceLinesHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<InvoiceLine> getInvoiceLine(String id) {
    CompletableFuture<InvoiceLine> future = new VertxCompletableFuture<>(ctx);
    try {
      getInvoiceLineById(id, lang, httpClient, ctx, okapiHeaders, logger)
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

  public CompletableFuture<Void> deleteInvoiceLine(String id) {
    return handleDeleteRequest(String.format(DELETE_INVOICE_LINE_BY_ID, id, lang), httpClient, ctx, okapiHeaders, logger);
  }
}
