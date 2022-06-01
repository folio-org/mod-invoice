package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InvoiceFields {
  PAYMENT_DUE("paymentDue"),
  PAYMENT_METHOD("paymentMethod"),
  VENDOR_INVOICE_NO("vendorInvoiceNo"),
  ACQ_UNIT_IDs("acqUnitIds"),
  NOTE("note"),
  BILL_TO("billTo");

  InvoiceFields(String field) {
    this.field = field;
  }

  private String field;

  public String getFieldName() {
    return field;
  }

  public static List<String> getFieldNames() {
    return Arrays.stream(InvoiceFields.values()).map(InvoiceFields::getFieldName).collect(Collectors.toList());
  }
}
