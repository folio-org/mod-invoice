package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum VoucherProtectedFields {
  ID("id"),
  ACCOUNTING_CODE("accountingCode"),
  AMOUNT("amount"),
  BATCH_GROUP_ID("batchGroupId"),
  EXCHANGE_RATE("exchangeRate"),
  EXPORT_TO_ACCOUNTING("exportToAccounting"),
  INVOICE_CURRENCY("invoiceCurrency"),
  INVOICE_ID("invoiceId"),
  STATUS("status"),
  SYSTEM_CURRENCY("systemCurrency"),
  TYPE("type"),
  VOUCHER_DATE("voucherDate"),
  ACQ_UNIT_IDS("acqUnitIds");

  private String field;

  VoucherProtectedFields(String field){
    this.field = field;
  }

  public String getFieldName() {
    return field;
  }

  public static List<String> getProtectedFields() {
    return Arrays.stream(VoucherProtectedFields.values())
      .map(VoucherProtectedFields::getFieldName)
      .collect(Collectors.toList());
  }
}
