package org.folio.domain.relationship;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EntityTable {
  INVOICES("records_invoices", "record_id", "invoice_id");

  private final String tableName;
  private final String recordIdFieldName;
  private final String entityIdFieldName;
}
