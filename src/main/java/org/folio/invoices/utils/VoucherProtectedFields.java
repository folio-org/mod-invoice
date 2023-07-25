package org.folio.invoices.utils;

import java.util.function.Function;
import org.folio.rest.jaxrs.model.Voucher;

public enum VoucherProtectedFields implements ProtectedField<Voucher> {
  ID("id", Voucher::getId),
  ACCOUNTING_CODE("accountingCode", Voucher::getAccountingCode),
  AMOUNT("amount", Voucher::getAmount),
  BATCH_GROUP_ID("batchGroupId", Voucher::getBatchGroupId),
  EXCHANGE_RATE("exchangeRate", Voucher::getExchangeRate),
  EXPORT_TO_ACCOUNTING("exportToAccounting", Voucher::getExportToAccounting),
  INVOICE_CURRENCY("invoiceCurrency", Voucher::getInvoiceCurrency),
  INVOICE_ID("invoiceId", Voucher::getInvoiceId),
  STATUS("status", Voucher::getStatus),
  SYSTEM_CURRENCY("systemCurrency", Voucher::getSystemCurrency),
  TYPE("type", Voucher::getType),
  VOUCHER_DATE("voucherDate", Voucher::getVoucherDate),
  ACQ_UNIT_IDS("acqUnitIds", Voucher::getAcqUnitIds);

  private String field;
  private Function<Voucher, Object> getter;

  VoucherProtectedFields(String field, Function<Voucher, Object> getter) {
    this.field = field;
    this.getter = getter;
  }

  public String getFieldName() {
    return field;
  }

  public Function<Voucher, Object> getGetter() {
    return getter;
  }
}
