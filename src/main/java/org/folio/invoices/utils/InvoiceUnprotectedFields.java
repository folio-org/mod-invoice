package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InvoiceUnprotectedFields {

  TAGS("tags");

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
