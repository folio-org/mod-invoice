package org.folio.invoices.utils;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {

  GENERIC_ERROR_CODE("genericError", "Generic error"),
  INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION("incompatibleInvoiceFields", "Incompatible invoice fields on status transition"),
  PO_LINE_NOT_FOUND("poLineNotFound", "The purchase order line record is not found"),
  CANNOT_DELETE_INVOICE_LINE("cannotDeleteInvoiceLine", "Cannot delete invoice-line because invoice record associated with invoice-line not found"),
  PO_LINE_UPDATE_FAILURE("poLineUpdateFailure", "One or more purchase order line record(s) cannot be updated"),
  VOUCHER_NOT_FOUND("voucherNotFound", "The voucher record is not found"),
  FUND_DISTRIBUTIONS_NOT_PRESENT("FundDistributionsNotPresent", "At least one fund distribution should present for every associated invoice line"),
  FUND_DISTRIBUTIONS_PERCENTAGE_SUMMARY_MISMATCH("FundDistributionsPercentageSummaryMismatch", "Fund distributions percentage summary should be 100 for every associated invoice lines"),
  VOUCHER_UPDATE_FAILURE("voucherUpdateFailure", "Voucher record cannot be updated"),
  INVOICE_TOTAL_REQUIRED("invoiceTotalRequired", "The total amount is expected when lockTotal is true"),
  MOD_CONFIG_ERROR("configNotAvailable", "The mod-configuration is not available"),
  PROHIBITED_FIELD_CHANGING("protectedFieldChanging", "Field can't be modified"),
  FUNDS_NOT_FOUND("fundsNotFound", "Fund records are not found"),
  EXTERNAL_ACCOUNT_NUMBER_IS_MISSING("externalAccountNoIsMissing", "Fund record does not contain an externalAccountNo."),
  VOUCHER_NUMBER_PREFIX_NOT_ALPHA("voucherNumberPrefixNotAlpha", "Voucher number prefix must contains only Unicode letters"),
  PROHIBITED_INVOICE_LINE_CREATION("prohibitedInvoiceLineCreation","It is not allowed to add invoice line to the invoice that has been approved"),
  MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY("idMismatch", "Mismatch between id in path and request body"),
  USER_HAS_NO_PERMISSIONS("userHasNoPermission", "User does not have permissions - operation is restricted"),
  USER_HAS_NO_ACQ_PERMISSIONS("userHasNoAcqUnitsPermission", "User does not have permissions to manage acquisition units assignments - operation is restricted"),
  ACQ_UNITS_NOT_FOUND("acqUnitsNotFound", "Acquisitions units assigned to invoice not found");

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
