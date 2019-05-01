package org.folio.rest.impl;

import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class InvoiceLinesHelper extends AbstractHelper {

  InvoiceLinesHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Void> updateInvoiceLine(InvoiceLine invoiceLine) {
    return handlePutRequest(resourceByIdPath(INVOICE_LINES, invoiceLine.getId()),
        JsonObject.mapFrom(invoiceLine), httpClient, ctx, okapiHeaders, logger);
  }
}
