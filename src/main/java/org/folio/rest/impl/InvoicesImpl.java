package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class InvoicesImpl implements org.folio.rest.jaxrs.resource.Invoice {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesImpl.class);
  private static final String NOT_SUPPORTED = "Not supported";	// To overcome sonarcloud warning

  @Validate
  @Override
  public void postInvoiceInvoices(String lang, Invoice entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PostInvoiceInvoicesResponse.respond500WithTextPlain(NOT_SUPPORTED)));
  }

  @Validate
  @Override
  public void getInvoiceInvoices(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
  InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);
  helper
    .getInvoices(limit, offset, query)
      .thenAccept(invoices -> {
        if (logger.isInfoEnabled()) {
          logger.info("Successfully retrieved invoices: " + JsonObject.mapFrom(invoices).encodePrettily());
        }
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoices)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
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
  public void putInvoiceInvoiceLinesById(String invoiceLineId, String lang, InvoiceLine invoiceLine,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (StringUtils.isEmpty(invoiceLine.getId())) {
      invoiceLine.setId(invoiceLineId);
    }
    InvoiceLinesHelper invoiceLinesHelper = new InvoiceLinesHelper(okapiHeaders, vertxContext, lang);
    invoiceLinesHelper.updateInvoiceLine(invoiceLine)
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(invoiceLinesHelper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLinesHelper, t));
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
