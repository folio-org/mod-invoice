package org.folio.invoices.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ResourcePathResolver {

  private ResourcePathResolver() {
  }

  public static final String ACQUISITIONS_UNITS = "acquisitionsUnits";
  public static final String ACQUISITIONS_MEMBERSHIPS = "acquisitionsMemberships";
  public static final String INVOICES = "invoices";
  public static final String INVOICE_LINES = "invoiceLines";
  public static final String ORDER_LINES = "orderLines";
  public static final String VOUCHER_LINES = "voucherLines";
  public static final String VOUCHER_NUMBER_START = "voucherNumberStart";
  public static final String VOUCHER_NUMBER = "voucherNumberGet";
  public static final String FOLIO_INVOICE_NUMBER = "folioInvoiceNo";
  public static final String INVOICE_LINE_NUMBER = "invoiceLineNumber";
  public static final String VOUCHERS = "vouchers";
  public static final String FUNDS = "funds";
  public static final String INVOICE_DOCUMENTS = "invoiceDocuments";
  public static final String BATCH_VOUCHER_EXPORT_CONFIGS = "batchVoucherExportConfigs";
  public static final String INVOICE_TRANSACTION_SUMMARIES = "invoiceSummary";
  public static final String AWAITING_PAYMENTS = "awaitingPayments";
  public static final String BATCH_GROUPS = "batch-groups";

  private static final Map<String, String> SUB_OBJECT_COLLECTION_APIS;
  private static final Map<String, String> SUB_OBJECT_ITEM_APIS;

  static {
    Map<String, String> apis = new HashMap<>();
    apis.put(ACQUISITIONS_UNITS, "/acquisitions-units/units");
    apis.put(ACQUISITIONS_MEMBERSHIPS, "/acquisitions-units/memberships");
    apis.put(INVOICES, "/invoice-storage/invoices");
    apis.put(INVOICE_LINES, "/invoice-storage/invoice-lines");
    apis.put(INVOICE_LINE_NUMBER, "/invoice-storage/invoice-line-number");
    apis.put(ORDER_LINES, "/orders/order-lines");
    apis.put(FOLIO_INVOICE_NUMBER, "/invoice-storage/invoice-number");
    apis.put(VOUCHERS, "/voucher-storage/vouchers");
    apis.put(VOUCHER_LINES, "/voucher-storage/voucher-lines");
    apis.put(VOUCHER_NUMBER_START, "/voucher-storage/voucher-number/start");
    apis.put(VOUCHER_NUMBER, "/voucher-storage/voucher-number");
    apis.put(FUNDS, "/finance/funds");
    apis.put(INVOICE_DOCUMENTS, "/invoice-storage/invoices/%s/documents");
    apis.put(BATCH_VOUCHER_EXPORT_CONFIGS, "/batch-voucher-storage/export-configurations");
    apis.put(INVOICE_TRANSACTION_SUMMARIES, "/finance/invoice-transaction-summaries");
    apis.put(AWAITING_PAYMENTS, "/finance/awaiting-payment");
    apis.put(BATCH_GROUPS, "/batch-group-storage/batch-groups");

    SUB_OBJECT_COLLECTION_APIS = Collections.unmodifiableMap(apis);
    SUB_OBJECT_ITEM_APIS = Collections.unmodifiableMap(
        apis.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue() + "/%s?lang=%s"))
      );
  }

  public static String resourceByIdPath(String field, String id, String lang) {
    return String.format(SUB_OBJECT_ITEM_APIS.get(field), id, lang);
  }

  public static String resourcesPath(String field) {
    return SUB_OBJECT_COLLECTION_APIS.get(field);
  }

  public static String resourceByParentIdAndIdPath(String field, String parentId, String id, String lang) {
    return String.format(SUB_OBJECT_ITEM_APIS.get(field), parentId, id, lang);
  }
}
