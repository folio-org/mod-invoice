package org.folio.invoices.events.handlers;

public enum MessageAddress {

  INVOICE_TOTALS("org.folio.invoices.invoice.update.totals"),
  BATCH_VOUCHER_PERSIST_TOPIC("org.folio.batch.voucher.persist");

  MessageAddress(String address) {
    this.address = address;
  }

  public final String address;
}
