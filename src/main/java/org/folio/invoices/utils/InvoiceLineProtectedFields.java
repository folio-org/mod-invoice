package org.folio.invoices.utils;

import java.util.function.Function;
import org.folio.rest.jaxrs.model.InvoiceLine;

public enum InvoiceLineProtectedFields implements ProtectedField<InvoiceLine> {

  ADJUSTMENTS("adjustments", InvoiceLine::getAdjustments),
  ADJUSTMENTS_TOTAL("adjustmentsTotal", InvoiceLine::getAdjustmentsTotal),
  INVOICE_ID("invoiceId", InvoiceLine::getInvoiceId),
  INVOICE_LINE_NUMBER("invoiceLineNumber", InvoiceLine::getInvoiceLineNumber),
  PO_LINE_ID("poLineId", InvoiceLine::getPoLineId),
  PRODUCT_ID("productId", InvoiceLine::getProductId),
  PRODUCT_ID_TYPE("productIdType", InvoiceLine::getProductIdType),
  QUANTITY("quantity", InvoiceLine::getQuantity),
  SUB_TOTAL("subTotal", InvoiceLine::getSubTotal),
  TOTAL("total", InvoiceLine::getTotal);

  InvoiceLineProtectedFields(String field, Function<InvoiceLine, Object> getter) {
    this.field = field;
    this.getter = getter;
  }

  public String getFieldName() {
    return field;
  }

  public Function<InvoiceLine, Object> getGetter() {
    return getter;
  }

  private String field;
  private Function<InvoiceLine, Object> getter;
}
