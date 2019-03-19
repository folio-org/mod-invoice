package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;


public class InvoicesImpl implements org.folio.rest.jaxrs.resource.Invoice {

  private static final String NOT_SUPPORTED = "Not supported";	// To overcome sonarcloud warning
  
  @Validate
	@Override
	public void postInvoiceInvoices(String lang, Invoice entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PostInvoiceInvoicesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
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

  @Validate
	@Override
	public void getInvoiceInvoiceNumber(String lang, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(GetInvoiceInvoiceNumberResponse.respond500WithTextPlain(NOT_SUPPORTED)));
	}

  @Validate
	@Override
	public void postInvoiceInvoiceNumberValidate(String lang, Object entity, Map<String, String> okapiHeaders,
	    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
		asyncResultHandler.handle(succeededFuture(PostInvoiceInvoiceNumberValidateResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
	}
}
