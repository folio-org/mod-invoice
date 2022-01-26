package org.folio;

public final class TestMockDataConstants {
  private TestMockDataConstants() {
  }
  public static final String BASE_MOCK_DATA_PATH = "mockdata/";
  public static final String BASE_MOCK_TRANSACTIONS_BASE_PATH = BASE_MOCK_DATA_PATH + "transactions/";
  public static final String MOCK_TRANSACTIONS_LIST = BASE_MOCK_TRANSACTIONS_BASE_PATH + "transactions.json";
  public static final String MOCK_CREDITS_LIST = BASE_MOCK_TRANSACTIONS_BASE_PATH + "credits.json";
  public static final String MOCK_ENCUMBRANCES_LIST = BASE_MOCK_TRANSACTIONS_BASE_PATH + "encumbrances.json";
  public static final String MOCK_PAYMENTS_LIST = BASE_MOCK_TRANSACTIONS_BASE_PATH + "payments.json";
  public static final String MOCK_PENDING_PAYMENTS_LIST = BASE_MOCK_TRANSACTIONS_BASE_PATH + "pending-payments.json";
  public static final String INVOICE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoices/";
  public static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoiceLines/";
  public static final String INVOICE_LINES_LIST_PATH = INVOICE_LINES_MOCK_DATA_PATH + "invoice_lines.json";
  public static final String VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "vouchers/";
}
