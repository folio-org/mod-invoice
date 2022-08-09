package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.resource.PoLineInvoiceLine;
import org.folio.services.invoice.PoLineInvoiceLineService;
import org.folio.services.invoice.PoLineInvoiceLineServiceImpl;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PoLineInvoiceLineImpl implements PoLineInvoiceLine {

  @Override
  public void getPoLineInvoiceLineById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PoLineInvoiceLineService service = new PoLineInvoiceLineServiceImpl();
    service.getPoLineInvoiceLineById(id);

  }

  @Override
  public void getPoLineInvoiceLineByQueryByQuery(String query, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PoLineInvoiceLineService service = new PoLineInvoiceLineServiceImpl();
    service.getPoLineInvoiceLineByQuery(query);
  }


}
