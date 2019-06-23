package org.folio.invoices.utils;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {

  GENERIC_ERROR_CODE("genericError", "Generic error"),
  PO_LINE_NOT_FOUND("poLineNotFound", "The purchase order line record is not found"),
  PO_LINE_UPDATE_FAILURE("poLineUpdateFailure", "One or more purchase order line record(s) cannot be updated"),
  VOUCHER_NOT_FOUND("voucherNotFound", "The voucher record is not found"),
  VOUCHER_UPDATE_FAILURE("voucherUpdateFailure", "Voucher record cannot be updated"),
  INVOICE_TOTAL_REQUIRED("invoiceTotalRequired", "The total amount is expected when lockTotal is true"),
  MOD_CONFIG_ERROR("configNotAvailable", "The mod-configuration is not available"),
  PROHIBITED_FIELD_CHANGING("protectedFieldChanging", "Field can't be modified"),
  FUNDS_NOT_FOUND("fundsNotFound", "Fund records are not found");

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
