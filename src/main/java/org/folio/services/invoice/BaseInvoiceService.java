package org.folio.services.invoice;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;


public class BaseInvoiceService implements InvoiceService {

    private final RestClient invoiceStorageRestClient;

    public BaseInvoiceService(RestClient invoiceStorageRestClient) {
        this.invoiceStorageRestClient = invoiceStorageRestClient;
    }

    @Override
    public CompletableFuture<InvoiceCollection> getInvoices(String query, int offset, int limit, RequestContext requestContext) {
        return invoiceStorageRestClient.get(query, offset, limit, requestContext, InvoiceCollection.class);
    }

    @Override
    public CompletableFuture<Invoice> getInvoiceById(String invoiceId, RequestContext requestContext) {
        return invoiceStorageRestClient.getById(invoiceId, requestContext, Invoice.class);
    }

    @Override
    public CompletableFuture<Invoice> createInvoice(Invoice invoice, RequestContext requestContext) {
        return invoiceStorageRestClient.post(invoice, requestContext, Invoice.class);
    }

    @Override
    public CompletableFuture<Void> updateInvoice(Invoice invoice, RequestContext requestContext) {
        return invoiceStorageRestClient.put(invoice.getId(), invoice, requestContext);
    }

    @Override
    public CompletableFuture<Void> deleteInvoice(String invoiceId, RequestContext requestContext) {
        return invoiceStorageRestClient.delete(invoiceId, requestContext);
    }
}
