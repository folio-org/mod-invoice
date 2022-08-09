package org.folio.services.invoice;

import java.util.UUID;

public interface PoLineInvoiceLineService {

  void getPoLineInvoiceLineById(String  id);

  void getPoLineInvoiceLineByQuery(String query);

}
