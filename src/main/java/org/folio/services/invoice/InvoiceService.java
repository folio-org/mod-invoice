package org.folio.services.invoice;

import java.util.List;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;

import io.vertx.core.Future;

public interface InvoiceService {
  Future<InvoiceCollection> getInvoices(String query, int offset, int limit, RequestContext requestContext);
  Future<Invoice> getInvoiceById(String invoiceId, RequestContext requestContext);
  Future<Invoice> createInvoice(Invoice invoice, RequestContext requestContext);
  Future<Void> updateInvoice(Invoice invoice, RequestContext requestContext);
  Future<Void> deleteInvoice(String invoiceId, RequestContext requestContext);
  void calculateTotals(Invoice invoice, List<InvoiceLine> lines);
  void calculateTotals(Invoice invoice);
  boolean recalculateTotals(Invoice invoice, List<InvoiceLine> lines);
  Future<Boolean> recalculateTotals(Invoice invoice, RequestContext requestContext);
  Future<Void> updateInvoicesTotals(InvoiceCollection invoiceCollection, RequestContext requestContext);
  Future<String> generateFolioInvoiceNumber(RequestContext requestContext);
}
