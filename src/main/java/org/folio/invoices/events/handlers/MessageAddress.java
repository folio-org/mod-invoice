package org.folio.invoices.events.handlers;

public enum MessageAddress {

  INVOICE_TOTALS("org.folio.invoices.invoice.update.totals");

  MessageAddress(String address) {
    this.address = address;
  }

  public final String address;
}
