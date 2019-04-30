package org.folio.invoices.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ResourcePathResolver {

  private ResourcePathResolver() {
  }

  public static final String INVOICES = "invoices";
  public static final String FOLIO_INVOICE_NUMBER = "folioInvoiceNo";

  private static final Map<String, String> SUB_OBJECT_COLLECTION_APIS;

  static {
    Map<String, String> apis = new HashMap<>();
    apis.put(INVOICES, "/invoice-storage/invoices");
    apis.put(FOLIO_INVOICE_NUMBER, "/invoice-storage/invoice-number");

    SUB_OBJECT_COLLECTION_APIS = Collections.unmodifiableMap(apis);
  }

  public static String resourcesPath(String field) {
    return SUB_OBJECT_COLLECTION_APIS.get(field);
  }
}
