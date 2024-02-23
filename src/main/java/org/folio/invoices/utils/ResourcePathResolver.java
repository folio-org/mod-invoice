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
  public static final String COMPOSITE_ORDER = "compositeOrder";
  public static final String ORDER_LINES = "orderLines";
  public static final String ORDER_INVOICE_RELATIONSHIP = "orderInvoiceRelationship";
  public static final String VOUCHER_LINES = "voucherLines";
  public static final String VOUCHER_NUMBER_START = "voucherNumberStart";
  public static final String VOUCHER_NUMBER_STORAGE = "voucherNumberGet";
  public static final String FOLIO_INVOICE_NUMBER = "folioInvoiceNo";
  public static final String INVOICE_LINE_NUMBER = "invoiceLineNumber";
  public static final String VOUCHERS_STORAGE = "vouchers";
  public static final String FUNDS = "funds";
  public static final String INVOICE_DOCUMENTS = "invoiceDocuments";
  public static final String BATCH_VOUCHER_EXPORT_CONFIGS = "batchVoucherExportConfigs";
  public static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS = "batchVoucherExportConfigsCredentials";
  public static final String BATCH_GROUPS = "batch-groups";
  public static final String BATCH_VOUCHER_STORAGE = "batch-voucher/batch-vouchers";
  public static final String BATCH_VOUCHER_EXPORTS_STORAGE = "batch-voucher/batch-voucher-exports";
  public static final String BUDGETS = "finance.budgets";
  public static final String CURRENT_BUDGET = "finance.current-budgets";
  public static final String LEDGERS = "finance.ledgers";
  public static final String FINANCE_TRANSACTIONS = "finance/transactions";
  public static final String FINANCE_BATCH_TRANSACTIONS = "batchTransactions";
  public static final String EXPENSE_CLASSES_URL = "expenseClassUrl";
  public static final String BUDGET_EXPENSE_CLASSES = "finance-storage.budget-expense-classes";
  public static final String FINANCE_EXCHANGE_RATE = "finance/exchange-rate";
  public static final String TENANT_CONFIGURATION_ENTRIES = "configurations/entries";
  public static final String FISCAL_YEARS = "fiscalYears";

  private static final Map<String, String> SUB_OBJECT_COLLECTION_APIS;
  private static final Map<String, String> SUB_OBJECT_ITEM_APIS;

  static {
    Map<String, String> apis = new HashMap<>();
    apis.put(ACQUISITIONS_UNITS, "/acquisitions-units-storage/units");
    apis.put(ACQUISITIONS_MEMBERSHIPS, "/acquisitions-units-storage/memberships");
    apis.put(INVOICES, "/invoice-storage/invoices");
    apis.put(COMPOSITE_ORDER, "/orders/composite-orders");
    apis.put(INVOICE_LINES, "/invoice-storage/invoice-lines");
    apis.put(INVOICE_LINE_NUMBER, "/invoice-storage/invoice-line-number");
    apis.put(ORDER_LINES, "/orders/order-lines");
    apis.put(ORDER_INVOICE_RELATIONSHIP, "/orders-storage/order-invoice-relns");
    apis.put(FOLIO_INVOICE_NUMBER, "/invoice-storage/invoice-number");
    apis.put(VOUCHERS_STORAGE, "/voucher-storage/vouchers");
    apis.put(VOUCHER_LINES, "/voucher-storage/voucher-lines");
    apis.put(VOUCHER_NUMBER_START, "/voucher-storage/voucher-number/start");
    apis.put(VOUCHER_NUMBER_STORAGE, "/voucher-storage/voucher-number");
    apis.put(FUNDS, "/finance/funds");
    apis.put(INVOICE_DOCUMENTS, "/invoice-storage/invoices/%s/documents");
    apis.put(BATCH_VOUCHER_EXPORT_CONFIGS, "/batch-voucher-storage/export-configurations");
    apis.put(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, "/batch-voucher-storage/export-configurations/%s/credentials");
    apis.put(BATCH_GROUPS, "/batch-group-storage/batch-groups");
    apis.put(BATCH_VOUCHER_STORAGE, "/batch-voucher-storage/batch-vouchers");
    apis.put(BATCH_VOUCHER_EXPORTS_STORAGE, "/batch-voucher-storage/batch-voucher-exports");
    apis.put(FINANCE_TRANSACTIONS, "/finance/transactions");
    apis.put(FINANCE_BATCH_TRANSACTIONS, "/finance/transactions/batch-all-or-nothing");
    apis.put(BUDGETS, "/finance/budgets");
    apis.put(CURRENT_BUDGET, "/finance/funds/%s/budget");
    apis.put(LEDGERS, "/finance/ledgers");
    apis.put(BUDGET_EXPENSE_CLASSES, "/finance-storage/budget-expense-classes");
    apis.put(EXPENSE_CLASSES_URL, "/finance/expense-classes");
    apis.put(FINANCE_EXCHANGE_RATE, "/finance/exchange-rate");
    apis.put(TENANT_CONFIGURATION_ENTRIES, "/configurations/entries");
    apis.put(FISCAL_YEARS, "/finance/fiscal-years");

    SUB_OBJECT_COLLECTION_APIS = Collections.unmodifiableMap(apis);
    SUB_OBJECT_ITEM_APIS = Collections.unmodifiableMap(
        apis.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue() + "/%s"))
      );
  }

  public static String resourceByIdPath(String field, String id) {
    return String.format(SUB_OBJECT_ITEM_APIS.get(field), id);
  }

  public static String resourcesPath(String field) {
    return SUB_OBJECT_COLLECTION_APIS.get(field);
  }

  public static String resourceByParentIdAndIdPath(String field, String parentId, String id) {
    return String.format(SUB_OBJECT_ITEM_APIS.get(field), parentId, id);
  }
}
