package org.folio.invoices.utils;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

import java.util.Collections;

public enum ErrorCodes {

  GENERIC_ERROR_CODE("genericError", "Generic error"),
  INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION("incompatibleInvoiceFields", "Incompatible invoice fields on status transition"),
  PO_LINE_NOT_FOUND("poLineNotFound", "The purchase order line record is not found"),
  CANNOT_DELETE_INVOICE_LINE("cannotDeleteInvoiceLine", "Cannot delete invoice-line because invoice record associated with invoice-line not found"),
  INVALID_INVOICE_TRANSITION_ON_PAID_STATUS("invalidInvoiceStatusTransitionOnPaidStatus", "Cannot transition invoice to any other statuses when it is in Paid status"),
  PO_LINE_UPDATE_FAILURE("poLineUpdateFailure", "One or more purchase order line record(s) cannot be updated"),
  VOUCHER_NOT_FOUND("voucherNotFound", "The voucher record is not found"),
  FUND_DISTRIBUTIONS_NOT_PRESENT("fundDistributionsNotPresent", "At least one fund distribution should present for every associated invoice line"),
  ACCOUNTING_CODE_NOT_PRESENT("accountingCodeNotPresent", "Invoice can not be approved, because it requires an accounting code to be export to accounting"),
  ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT("adjustmentFundDistributionsNotPresent", "At least one fund distribution should present for every non-prorated adjustment"),
  LINE_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH("lineFundDistributionsSummaryMismatch", "Fund distributions summary should be 100 % or equal to subtotal for every associated invoice lines"),
  ADJUSTMENT_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH("adjustmentFundDistributionsSummaryMismatch", "Fund distributions summary should be 100 % or equal to subtotal for every non-prorated adjustment"),
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
  ACQ_UNITS_NOT_FOUND("acqUnitsNotFound", "Acquisitions units assigned to the record not found"),
  PENDING_PAYMENT_ERROR("pendingPaymentError", "Failed to create pending payment"),
  INVOICE_PAYMENT_FAILURE("invoicePaymentFailure", "Invoice payment failure"),
  CURRENT_FISCAL_YEAR_NOT_FOUND("currentFYearNotFound", "Current fiscal year not found for ledger"),
  TRANSACTION_CREATION_FAILURE("transactionCreationFailure", "One or more transactions record(s) failed to be created"),
  DOCUMENT_IS_TOO_LARGE("documentIsTooLarge", "Document size is too large"),
  ADJUSTMENT_IDS_NOT_UNIQUE("adjustmentIdsNotUnique", "Adjustment ids must be unique"),
  CANNOT_DELETE_ADJUSTMENTS("cannotDeleteAdjustment", "Prorated adjustment cannot be deleted because it is present on invoice level"),
  CANNOT_ADD_ADJUSTMENTS("cannotAddAdjustment", "Prorated adjustment cannot be adde because it is not present on invoice level"),
  APPROVED_OR_PAID_INVOICE_DELETE_FORBIDDEN("approvedOrPaidInvoiceDeleteForbiddenError", "Approved or paid invoice can not be deleted"),
  BUDGET_NOT_FOUND("budgetNotFoundByFundId", "Budget not found by fundId"),
  LEDGER_NOT_FOUND("ledgerNotFound", "Ledger not found"),
  BUDGET_IS_INACTIVE("budgetIsInactive", "Budget should be active to approve invoice"),
  FUND_CANNOT_BE_PAID("fundCannotBePaid", "Fund cannot be paid due to restrictions");

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

  public Errors toErrors() {
    return new Errors().withErrors(Collections.singletonList(new Error().withCode(code).withMessage(description))).withTotalRecords(1);
  }
}
