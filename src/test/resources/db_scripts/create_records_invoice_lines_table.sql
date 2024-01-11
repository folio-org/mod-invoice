-- Creates table to store records and invoice lines

CREATE TABLE IF NOT EXISTS records_invoices
(
  record_id uuid NOT NULL PRIMARY KEY,
  invoice_line_id uuid NOT NULL
);

