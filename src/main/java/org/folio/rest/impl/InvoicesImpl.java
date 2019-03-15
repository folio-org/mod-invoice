package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.resource.Invoicing;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;


public class InvoicesImpl implements Invoicing {

  private static final String NOT_SUPPORTED = "Not supported";	// To overcome sonarcloud warning
  
	@Override
	public void postInvoicingInvoices(String lang, Invoice entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PostInvoicingInvoicesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void getInvoicingInvoices(int offset, int limit, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoicingInvoicesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void getInvoicingInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoicingInvoicesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void putInvoicingInvoicesById(String id, String lang, Invoice entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PutInvoicingInvoicesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void deleteInvoicingInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(DeleteInvoicingInvoicesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void getInvoicingInvoiceLines(String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoicingInvoiceLinesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void postInvoicingInvoiceLines(String lang, InvoiceLine entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PostInvoicingInvoiceLinesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void getInvoicingInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoicingInvoiceLinesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void putInvoicingInvoiceLinesById(String id, String lang, InvoiceLine entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PutInvoicingInvoiceLinesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void deleteInvoicingInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(DeleteInvoicingInvoiceLinesByIdResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void getInvoicingInvoiceNumber(String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoicingInvoiceNumberResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

	@Override
	public void postInvoicingInvoiceNumberValidate(String lang, Object entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PostInvoicingInvoiceNumberValidateResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
	}
}
