package org.folio.models;

import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class InvoiceHolder {

  private InvoiceLine invoiceLine;
  private Invoice invoice;
  private CompositePurchaseOrder compositePurchaseOrder;

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

  public InvoiceHolder setCompositePurchaseOrder(CompositePurchaseOrder compositePurchaseOrder) {
    this.compositePurchaseOrder = compositePurchaseOrder;
    return this;
  }

  public CompositePurchaseOrder getCompositePurchaseOrder() {
    return compositePurchaseOrder;
  }
}
