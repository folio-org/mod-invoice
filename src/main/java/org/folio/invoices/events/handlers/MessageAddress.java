package org.folio.invoices.events.handlers;

public enum MessageAddress {

  BATCH_VOUCHER_PERSIST_TOPIC("org.folio.batch.voucher.persist");

  MessageAddress(String address) {
    this.address = address;
  }

  public final String address;
}
