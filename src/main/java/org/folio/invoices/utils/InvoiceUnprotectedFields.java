package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InvoiceUnprotectedFields {
  PAYMENT_DUE("paymentDue"),
  PAYMENT_METHOD("paymentMethod"),
  VENDOR_INVOICE_NO("vendorInvoiceNo"),
  ACQ_UNIT_IDs("acqUnitIds"),
  NOTE("note"),
  BILL_TO("billTo");

  InvoiceUnprotectedFields(String field) {
    this.field = field;
  }

  public String getFieldName() {
    return field;
  }

  private String field;

  public static List<String> getFieldNames() {
    return Arrays.stream(InvoiceUnprotectedFields.values()).map(InvoiceUnprotectedFields::getFieldName).collect(Collectors.toList());
  }
}
