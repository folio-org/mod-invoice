package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.HelperUtils.validateAcqsUnitAssignment;

public class InvoicesImpl implements org.folio.rest.jaxrs.resource.Invoice {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesImpl.class);
  private static final String NOT_SUPPORTED = "Not supported"; // To overcome sonarcloud warning
  private static final String INVOICE_LOCATION_PREFIX = "/invoice/invoices/%s";
  private static final String INVOICE_LINE_LOCATION_PREFIX = "/invoice/invoice-lines/%s";
  private static final String ACQUISITIONS_UNIT_ASSIGNMENTS_LOCATION_PREFIX = "/invoice/acquisitions-unit-assignments/%s";
  public static final String PROTECTED_AND_MODIFIED_FIELDS = "protectedAndModifiedFields";

  @Validate
  @Override
  public void postInvoiceInvoices(String lang, Invoice invoice, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    if (!helper.validateIncomingInvoice(invoice)) {
      asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(422)));
      return;
    }

    helper.createInvoice(invoice)
      .thenAccept(invoiceWithId -> asyncResultHandler.handle(succeededFuture(
          helper.buildResponseWithLocation(String.format(INVOICE_LOCATION_PREFIX, invoiceWithId.getId()), invoiceWithId))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoices(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper.getInvoices(limit, offset, query)
      .thenAccept(invoices -> {
        logInfo("Successfully retrieved invoices: {}", invoices);
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoices)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper.getInvoice(id)
      .thenAccept(invoice -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoice))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putInvoiceInvoicesById(String id, String lang, Invoice invoice, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    invoice.setId(id);

    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    // Validate incoming invoice first to avoid extra calls to other services if content is invalid
    if (!invoiceHelper.validateIncomingInvoice(invoice)) {
      asyncResultHandler.handle(succeededFuture(invoiceHelper.buildErrorResponse(422)));
      return;
    }

    invoiceHelper.updateInvoice(invoice)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(invoiceHelper.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, invoiceHelper, fail));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper.deleteInvoice(id)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceLines(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);
    helper.getInvoiceLines(limit, offset, query)
      .thenAccept(lines -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(lines))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postInvoiceInvoiceLines(String lang, InvoiceLine invoiceLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);
    logger.info("== Creating InvoiceLine for an existing invoice ==");

    helper.createInvoiceLine(invoiceLine)
      .thenAccept(invoiceLineWithId -> asyncResultHandler.handle(succeededFuture(helper
        .buildResponseWithLocation(String.format(INVOICE_LINE_LOCATION_PREFIX, invoiceLineWithId.getId()), invoiceLineWithId))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);

    invoiceLineHelper.getInvoiceLine(id)
      .thenCompose(invoiceLineHelper::calculateInvoiceLineTotals)
      .thenAccept(invoiceLine -> asyncResultHandler.handle(succeededFuture(invoiceLineHelper.buildOkResponse(invoiceLine))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLineHelper, t));
  }

  @Validate
  @Override
  public void putInvoiceInvoiceLinesById(String invoiceLineId, String lang, InvoiceLine invoiceLine,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLinesHelper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);

    if (StringUtils.isEmpty(invoiceLine.getId())) {
      invoiceLine.setId(invoiceLineId);
    }

    invoiceLinesHelper.updateInvoiceLine(invoiceLine)
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(invoiceLinesHelper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLinesHelper, t));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);

    invoiceLineHelper.deleteInvoiceLine(id)
      .thenAccept(invoiceLine -> asyncResultHandler.handle(succeededFuture(invoiceLineHelper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLineHelper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceNumber(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(GetInvoiceInvoiceNumberResponse.respond500WithTextPlain(NOT_SUPPORTED)));
  }

  @Validate
  @Override
  public void postInvoiceAcquisitionsUnitAssignments(String lang, AcquisitionsUnitAssignment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    AcquisitionsUnitAssignmentsHelper helper = new AcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.createAcquisitionsUnitAssignment(entity)
      .thenAccept(unit -> {
        logInfo("Successfully created new acquisitions unit: {}", unit);
        asyncResultHandler.handle(succeededFuture(
            helper.buildResponseWithLocation(String.format(ACQUISITIONS_UNIT_ASSIGNMENTS_LOCATION_PREFIX, unit.getId()), unit)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceAcquisitionsUnitAssignments(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    AcquisitionsUnitAssignmentsHelper helper = new AcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.getAcquisitionsUnitAssignments(query, offset, limit)
      .thenAccept(units -> {
        logInfo("Successfully retrieved acquisitions unit assignment : {}", units);
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(units)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putInvoiceAcquisitionsUnitAssignmentsById(String id, String lang, AcquisitionsUnitAssignment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    AcquisitionsUnitAssignmentsHelper helper = new AcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    validateAcqsUnitAssignment(helper, id, entity, asyncResultHandler);
    helper.updateAcquisitionsUnitAssignment(entity.withId(id))
      .thenAccept(units -> {
        logInfo("Successfully updated acquisitions unit assignment with id={}", id);
        asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse()));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));

  }

  @Validate
  @Override
  public void getInvoiceAcquisitionsUnitAssignmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    AcquisitionsUnitAssignmentsHelper helper = new AcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.getAcquisitionsUnitAssignment(id)
      .thenAccept(unit -> {
        logInfo("Successfully retrieved acquisitions unit assignment: {}", unit);
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(unit)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private void logInfo(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry)
        .encodePrettily());
    }
  }

  @Validate
  @Override
  public void deleteInvoiceAcquisitionsUnitAssignmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    AcquisitionsUnitAssignmentsHelper helper = new AcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.deleteAcquisitionsUnitAssignment(id)
      .thenAccept(ok -> {
        logInfo("Successfully deleted acquisitions unit assignment with id={}", id);
        asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse()));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private void logInfo(String message, String id) {
    if (logger.isInfoEnabled()) {
      logger.info(message, id);
    }
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

}
