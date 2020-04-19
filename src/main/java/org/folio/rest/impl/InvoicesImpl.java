package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Stream;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceDocument;
import org.folio.rest.jaxrs.model.InvoiceLine;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.io.FileUtils.ONE_MB;
import static org.folio.invoices.utils.ErrorCodes.DOCUMENT_IS_TOO_LARGE;
import static org.folio.invoices.utils.ErrorCodes.MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY;
import static org.folio.rest.RestVerticle.STREAM_ABORT;
import static org.folio.rest.RestVerticle.STREAM_COMPLETE;

public class InvoicesImpl implements org.folio.rest.jaxrs.resource.Invoice {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesImpl.class);
  private static final String NOT_SUPPORTED = "Not supported"; // To overcome sonarcloud warning
  private static final String INVOICE_LOCATION_PREFIX = "/invoice/invoices/%s";
  private static final String INVOICE_LINE_LOCATION_PREFIX = "/invoice/invoice-lines/%s";
  public static final String PROTECTED_AND_MODIFIED_FIELDS = "protectedAndModifiedFields";
  private static final String DOCUMENTS_LOCATION_PREFIX = "/invoice/invoices/%s/documents/%s";
  private byte[] requestBytesArray = new byte[0];
  private static final long MAX_DOCUMENT_SIZE = 25 * ONE_MB;

  @Validate
  @Override
  public void postInvoiceInvoices(String lang, Invoice invoice, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper.validateIncomingInvoice(invoice)
      .thenAccept(isValid -> {
        if (Boolean.TRUE.equals(isValid)) {
          helper.createInvoice(invoice)
            .thenAccept(invoiceWithId -> asyncResultHandler.handle(succeededFuture(
                helper.buildResponseWithLocation(String.format(INVOICE_LOCATION_PREFIX, invoiceWithId.getId()), invoiceWithId))))
            .exceptionally(t -> {
              logger.error("Failed to create invoice ", t);
              return handleErrorResponse(asyncResultHandler, helper, t);
            });
        } else {
          asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(422)));
        }
      })
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
    invoiceHelper.validateIncomingInvoice(invoice)
      .thenAccept(isValid -> {
        if (Boolean.TRUE.equals(isValid)) {
          invoiceHelper.updateInvoice(invoice)
            .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(invoiceHelper.buildNoContentResponse())))
            .exceptionally(t -> {
              logger.error("Failed to update invoice with id={}", t, invoice.getId());
              return handleErrorResponse(asyncResultHandler, invoiceHelper, t);
            });
        } else {
          asyncResultHandler.handle(succeededFuture(invoiceHelper.buildErrorResponse(422)));
        }
      })
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

    invoiceLineHelper.getInvoiceLinePersistTotal(id)
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
  public void getInvoiceInvoicesDocumentsById(String id, int offset, int limit, String query, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext, lang);
    documentHelper.getDocumentsByInvoiceId(id, limit, offset, query)
      .thenAccept(documents -> asyncResultHandler.handle(succeededFuture(documentHelper.buildOkResponse(documents))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
  }

  @Validate
  @Stream
  @Override
  public void postInvoiceInvoicesDocumentsById(String id, String lang, InputStream is, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      if (Objects.isNull(okapiHeaders.get(STREAM_COMPLETE))) {
        // This code will be executed for each stream's chunk
        processBytesArrayFromStream(is);
      } else if (Objects.nonNull(okapiHeaders.get(STREAM_ABORT))) {
        asyncResultHandler.handle(succeededFuture(PostInvoiceInvoicesDocumentsByIdResponse.respond400WithTextPlain("Stream aborted")));
      } else {
        // This code will be executed one time after stream completing
        if (Objects.nonNull(requestBytesArray)) {
          processDocumentCreation(id, lang, okapiHeaders, asyncResultHandler, vertxContext);
        } else {
          Errors errors = new Errors().withErrors(Collections.singletonList(DOCUMENT_IS_TOO_LARGE.toError())).withTotalRecords(1);
          asyncResultHandler.handle(succeededFuture(PostInvoiceInvoicesDocumentsByIdResponse.respond413WithApplicationJson(errors)));
        }
      }
    } catch (Exception e) {
      asyncResultHandler.handle(succeededFuture(PostInvoiceInvoicesDocumentsByIdResponse.respond500WithTextPlain("Internal Server Error")));
    }
  }

  @Validate
  @Override
  public void getInvoiceInvoicesDocumentsByIdAndDocumentId(String id, String documentId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext, lang);
    documentHelper.getDocumentByInvoiceIdAndDocumentId(id, documentId)
      .thenAccept(document -> {
        logInfo("Successfully retrieved document: {}", document);
        asyncResultHandler.handle(succeededFuture(documentHelper.buildOkResponse(document)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoicesDocumentsByIdAndDocumentId(String invoiceId, String documentId, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext, lang);

    documentHelper.deleteDocument(invoiceId, documentId)
      .thenAccept(invoiceLine -> asyncResultHandler.handle(succeededFuture(documentHelper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
  }

  private void logInfo(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry).encodePrettily());
    }
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

  private void processBytesArrayFromStream(InputStream is) throws IOException {
    if (Objects.nonNull(requestBytesArray) && requestBytesArray.length < MAX_DOCUMENT_SIZE && is.available() < MAX_DOCUMENT_SIZE) {
      requestBytesArray = ArrayUtils.addAll(requestBytesArray, IOUtils.toByteArray(is));
    } else {
      requestBytesArray = null;
    }
    is.close();
  }

  private void processDocumentCreation(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws IOException {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext, lang);
    InvoiceDocument entity = new JsonObject(new String(requestBytesArray, StandardCharsets.UTF_8)).mapTo(InvoiceDocument.class);
    if (!entity.getDocumentMetadata().getInvoiceId().equals(id)) {
      documentHelper.addProcessingError(MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY.toError());
      asyncResultHandler.handle(succeededFuture(documentHelper.buildErrorResponse(422)));
    } else {
      documentHelper.createDocument(id, entity)
        .thenAccept(document -> {
          logInfo("Successfully created document with id={}", document);
          asyncResultHandler.handle(succeededFuture(documentHelper.buildResponseWithLocation(String.format(DOCUMENTS_LOCATION_PREFIX, id, document.getDocumentMetadata().getId()), document)));
        })
        .exceptionally(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
    }
  }
}
