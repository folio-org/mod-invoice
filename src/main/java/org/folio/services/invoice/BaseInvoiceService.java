package org.folio.services.invoice;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;

import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class BaseInvoiceService implements InvoiceService {

    private static final String INVOICE_ENDPOINT = resourcesPath(INVOICES);
    private static final String INVOICE_BY_ID_ENDPOINT = INVOICE_ENDPOINT + "/{id}";

    private final RestClient restClient;

    public BaseInvoiceService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CompletableFuture<InvoiceCollection> getInvoices(String query, int offset, int limit, RequestContext requestContext) {
        RequestEntry requestEntry = new RequestEntry(INVOICE_ENDPOINT)
            .withQuery(query)
            .withOffset(offset)
            .withLimit(limit);
        return restClient.get(requestEntry, requestContext, InvoiceCollection.class);
    }

    @Override
    public CompletableFuture<Invoice> getInvoiceById(String invoiceId, RequestContext requestContext) {
        RequestEntry requestEntry = new RequestEntry(INVOICE_BY_ID_ENDPOINT).withId(invoiceId);
        return restClient.get(requestEntry, requestContext, Invoice.class);
    }

    @Override
    public CompletableFuture<Invoice> createInvoice(Invoice invoice, RequestContext requestContext) {
        RequestEntry requestEntry = new RequestEntry(INVOICE_ENDPOINT);
        return restClient.post(requestEntry, invoice, requestContext, Invoice.class);
    }

    @Override
    public CompletableFuture<Void> updateInvoice(Invoice invoice, RequestContext requestContext) {
        RequestEntry requestEntry = new RequestEntry(INVOICE_BY_ID_ENDPOINT).withId(invoice.getId());
        return restClient.put(requestEntry, invoice, requestContext);
    }

    @Override
    public CompletableFuture<Void> deleteInvoice(String invoiceId, RequestContext requestContext) {
        RequestEntry requestEntry = new RequestEntry(INVOICE_BY_ID_ENDPOINT).withId(invoiceId);
        return restClient.delete(requestEntry, requestContext);
    }
}
