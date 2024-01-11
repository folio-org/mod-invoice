-- Creates table to store records and invoice lines
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.records_invoice_lines
(
  record_id uuid NOT NULL PRIMARY KEY,
  invoice_line_id NOT NULL uuid
);
