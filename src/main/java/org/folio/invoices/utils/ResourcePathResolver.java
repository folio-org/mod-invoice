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

  private static final Map<String, String> SUB_OBJECT_COLLECTION_APIS;
  private static final Map<String, String> SUB_OBJECT_ITEM_APIS;

  static {
    Map<String, String> apis = new HashMap<>();
    apis.put(INVOICES, "/invoice-storage/invoices");
    apis.put(INVOICE_LINES, "/invoice-storage/invoice-lines");

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
