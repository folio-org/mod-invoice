package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Invoice;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class InvoiceHelper extends AbstractHelper {

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

}
