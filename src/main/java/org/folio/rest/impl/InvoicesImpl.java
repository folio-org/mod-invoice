package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.resource.Invoices;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;


public class InvoicesImpl implements Invoices {

  @Override
  public void postInvoices(String lang, Invoice entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PostInvoicesResponse.respond500WithTextPlain("Not supported")));
  }

  @Override
  public void getInvoices(int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(GetInvoicesResponse.respond500WithTextPlain("Not supported")));
  }

  @Override
  public void putInvoicesById(String id, String lang, Invoice entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PutInvoicesByIdResponse.respond500WithTextPlain("Not supported")));
  }

  @Override
  public void getInvoicesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(GetInvoicesByIdResponse.respond500WithTextPlain("Not supported")));
  }

  @Override
  public void deleteInvoicesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PutInvoicesByIdResponse.respond500WithTextPlain("Not supported")));
  }
}
