package org.folio.invoices.utils;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {

  GENERIC_ERROR_CODE("genericError", "Generic error"),
  PO_LINE_NOT_FOUND("poLineNotFound", "The purchase order line record is not found"),
  INVOICE_TOTAL_REQUIRED("invoiceTotalRequired", "The total amount is expected when lockTotal is true"),
  PROHIBITED_FIELD_CHANGING("protectedFieldChanging", "Field can't be modified");

  private final String code;
  private final String description;

  ErrorCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code + ": " + description;
  }

  public Error toError() {
    return new Error().withCode(code).withMessage(description);
  }
}
