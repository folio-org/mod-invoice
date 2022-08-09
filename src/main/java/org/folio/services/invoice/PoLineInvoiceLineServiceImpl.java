package org.folio.services.invoice;

import java.util.UUID;

public class PoLineInvoiceLineServiceImpl implements PoLineInvoiceLineService {

  @Override
  public void getPoLineInvoiceLineById(String  id) {
    System.out.println("IN getPoLineInvoiceLineById: UUID = "+ id);

  }

  @Override
  public void getPoLineInvoiceLineByQuery(String query) {
    System.out.println("IN getPoLineInvoiceLineByQuery: query = "+ query);
  }
}
