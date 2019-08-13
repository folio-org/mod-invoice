package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InvoiceProtectedFields {

  ADJUSTMENTS("adjustments"),
  ADJUSTMENTS_TOTAL("adjustmentsTotal"),
  APPROVED_BY("approvedBy"),
  APPROVAL_DATE("approvalDate"),
  CHK_SUBSCRIPTION_OVERLAP("chkSubscriptionOverlap"),
  CURRENCY("currency"),
  EXPORT_TO_ACCOUNTING("exportToAccounting"),
  FOLIO_INVOICE_NO("folioInvoiceNo"),
  INVOICE_DATE("invoiceDate"),
  LOCK_TOTAL("lockTotal"),
  PAYMENT_TERMS("paymentTerms"),
  SOURCE("source"),
  VOUCHER_NUMBER("voucherNumber"),
  PAYMENT_ID("paymentId"),
  PO_NUMBERS("poNumbers"),
  SUB_TOTAL("subTotal"),
  TOTAL("total"),
  VENDOR_ID("vendorId");

  InvoiceProtectedFields(String field) {
    this.field = field;
  }

  public String getFieldName() {
    return field;
  }

  private String field;

  public static List<String> getFieldNames() {
    return Arrays.stream(InvoiceProtectedFields.values()).map(InvoiceProtectedFields::getFieldName).collect(Collectors.toList());
  }
}
