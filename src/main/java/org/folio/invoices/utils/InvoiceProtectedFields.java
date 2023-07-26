package org.folio.invoices.utils;

import java.util.function.Function;
import org.folio.rest.jaxrs.model.Invoice;

public enum InvoiceProtectedFields implements ProtectedField<Invoice> {

  ADJUSTMENTS("adjustments", Invoice::getAdjustments),
  ADJUSTMENTS_TOTAL("adjustmentsTotal", Invoice::getAdjustmentsTotal),
  APPROVED_BY("approvedBy", Invoice::getApprovedBy),
  APPROVAL_DATE("approvalDate", Invoice::getApprovalDate),
  CHK_SUBSCRIPTION_OVERLAP("chkSubscriptionOverlap", Invoice::getChkSubscriptionOverlap),
  CURRENCY("currency", Invoice::getCurrency),
  EXPORT_TO_ACCOUNTING("exportToAccounting", Invoice::getExportToAccounting),
  FOLIO_INVOICE_NO("folioInvoiceNo", Invoice::getFolioInvoiceNo),
  INVOICE_DATE("invoiceDate", Invoice::getInvoiceDate),
  LOCK_TOTAL("lockTotal", Invoice::getLockTotal),
  PAYMENT_TERMS("paymentTerms", Invoice::getPaymentTerms),
  SOURCE("source", Invoice::getSource),
  VOUCHER_NUMBER("voucherNumber", Invoice::getVoucherNumber),
  PAYMENT_ID("paymentId", Invoice::getPaymentId),
  PO_NUMBERS("poNumbers", Invoice::getPoNumbers),
  VENDOR_ID("vendorId", Invoice::getVendorId);

  InvoiceProtectedFields(String field, Function<Invoice, Object> getter) {
    this.field = field;
    this.getter = getter;
  }

  public String getFieldName() {
    return field;
  }

  public Function<Invoice, Object> getGetter() {
    return getter;
  }

  private String field;
  private Function<Invoice, Object> getter;
}
