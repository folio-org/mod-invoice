package org.folio.models;

import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class InvoiceHolder {

  private InvoiceLine invoiceLine;
  private Invoice invoice;

  public InvoiceLine getInvoiceLine() {
    return invoiceLine;
  }

  public InvoiceHolder setInvoiceLine(InvoiceLine invoiceLine) {
    this.invoiceLine = invoiceLine;
    return this;
  }

  public Invoice getInvoice() {
    return invoice;
  }

  public InvoiceHolder setInvoice(Invoice invoice) {
    this.invoice = invoice;
    return this;
  }
}
