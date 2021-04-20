package org.folio.services.invoice;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;

import java.util.concurrent.CompletableFuture;

public interface InvoiceService {
    CompletableFuture<InvoiceCollection> getInvoices(String query, int offset, int limit, RequestContext requestContext);
    CompletableFuture<Invoice> getInvoiceById(String invoiceId, RequestContext requestContext);
    CompletableFuture<Invoice> createInvoice(Invoice invoice, RequestContext requestContext);
    CompletableFuture<Void> updateInvoice(Invoice invoice, RequestContext requestContext);
    CompletableFuture<Void> deleteInvoice(String invoiceId, RequestContext requestContext);
}
