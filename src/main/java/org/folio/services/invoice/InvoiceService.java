package org.folio.services.invoice;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;

public interface InvoiceService {
  CompletableFuture<InvoiceCollection> getInvoices(String query, int offset, int limit, RequestContext requestContext);
  CompletableFuture<Invoice> getInvoiceById(String invoiceId, RequestContext requestContext);
  CompletableFuture<Invoice> createInvoice(Invoice invoice, RequestContext requestContext);
  CompletableFuture<Void> updateInvoice(Invoice invoice, RequestContext requestContext);
  CompletableFuture<Void> deleteInvoice(String invoiceId, RequestContext requestContext);
  void calculateTotals(Invoice invoice, List<InvoiceLine> lines);
  void calculateTotals(Invoice invoice);
  boolean recalculateTotals(Invoice invoice, List<InvoiceLine> lines);
  CompletableFuture<Boolean> recalculateTotals(Invoice invoice, RequestContext requestContext);
  CompletableFuture<Void> updateInvoicesTotals(InvoiceCollection invoiceCollection, RequestContext requestContext);
}
