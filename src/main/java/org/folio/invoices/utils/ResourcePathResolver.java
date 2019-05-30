package org.folio.invoices.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ResourcePathResolver {

  private ResourcePathResolver() {
  }

  public static final String INVOICES = "invoices";
  public static final String INVOICE_LINES = "invoiceLines";
  public static final String VOUCHER_LINES = "voucherLines";
  public static final String VOUCHER_NUMBER_START = "voucherNumberStart";
  public static final String FOLIO_INVOICE_NUMBER = "folioInvoiceNo";
  public static final String INVOICE_LINE_NUMBER = "invoiceLineNumber";
  public static final String VOUCHERS = "vouchers";

  private static final Map<String, String> SUB_OBJECT_COLLECTION_APIS;
  private static final Map<String, String> SUB_OBJECT_ITEM_APIS;

  static {
    Map<String, String> apis = new HashMap<>();
    apis.put(INVOICES, "/invoice-storage/invoices");
    apis.put(INVOICE_LINES, "/invoice-storage/invoice-lines");
    apis.put(INVOICE_LINE_NUMBER, "/invoice-storage/invoice-line-number");
    apis.put(FOLIO_INVOICE_NUMBER, "/invoice-storage/invoice-number");
    apis.put(VOUCHERS, "/voucher-storage/vouchers");
    apis.put(VOUCHER_LINES, "/voucher-storage/voucher-lines");
    apis.put(VOUCHER_NUMBER_START, "/voucher-storage/voucher-number/start");

    SUB_OBJECT_COLLECTION_APIS = Collections.unmodifiableMap(apis);
    SUB_OBJECT_ITEM_APIS = Collections.unmodifiableMap(
        apis.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue() + "/"))
      );
  }

  public static String resourceByIdPath(String field) {
    return SUB_OBJECT_ITEM_APIS.get(field);
  }

  public static String resourceByIdPath(String field, String id) {
    return SUB_OBJECT_ITEM_APIS.get(field) + id;
  }

  public static String resourcesPath(String field) {
    return SUB_OBJECT_COLLECTION_APIS.get(field);
  }
}
