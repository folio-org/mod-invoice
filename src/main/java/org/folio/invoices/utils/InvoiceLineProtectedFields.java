package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InvoiceLineProtectedFields {

  ADJUSTMENTS("adjustments"),
  ADJUSTMENTS_TOTAL("adjustmentsTotal"),
  INVOICE_ID("invoiceId"),
  INVOICE_LINE_NUMBER("invoiceLineNumber"),
  PO_LINE_ID("poLineId"),
  PRODUCT_ID("productId"),
  PRODUCT_ID_TYPE("productIdType"),
  QUANTITY("quantity"),
  SUB_TOTAL("subTotal"),
  TOTAL("total");


  InvoiceLineProtectedFields(String field) {
    this.field = field;
  }

  public String getFieldName() {
    return field;
  }

  private String field;

  public static List<String> getFieldNames() {
    return Arrays.stream(InvoiceLineProtectedFields.values()).map(InvoiceLineProtectedFields::getFieldName).collect(Collectors.toList());
  }
}
