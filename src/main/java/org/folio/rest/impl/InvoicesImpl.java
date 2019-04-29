package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;


public class InvoicesImpl implements org.folio.rest.jaxrs.resource.Invoice {

  private static final String NOT_SUPPORTED = "Not supported";	// To overcome sonarcloud warning
  
  @Validate
	@Override
	public void postInvoiceInvoices(String lang, Invoice invoice, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

		InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

		helper.createPurchaseOrder(invoice)
      .thenAccept(invoiceWithId -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoiceWithId))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
	}

  @Validate
  @Override
  public void getInvoiceInvoices(int offset, int limit, String lang, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(GetInvoiceInvoicesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
  }

  @Validate
	@Override
	public void getInvoiceInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoiceInvoicesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void putInvoiceInvoicesById(String id, String lang, Invoice entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PutInvoiceInvoicesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void deleteInvoiceInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(DeleteInvoiceInvoicesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void getInvoiceInvoiceLines(int offset, int limit, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoiceInvoiceLinesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void postInvoiceInvoiceLines(String lang, InvoiceLine entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PostInvoiceInvoiceLinesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void getInvoiceInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoiceInvoiceLinesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void putInvoiceInvoiceLinesById(String id, String lang, InvoiceLine entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PutInvoiceInvoiceLinesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void deleteInvoiceInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(DeleteInvoiceInvoiceLinesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper,
                                   Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
