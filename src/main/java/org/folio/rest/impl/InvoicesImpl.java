package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.io.FileUtils.ONE_MB;
import static org.folio.invoices.utils.ErrorCodes.DOCUMENT_IS_TOO_LARGE;
import static org.folio.invoices.utils.ErrorCodes.MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY;
import static org.folio.rest.RestVerticle.STREAM_ABORT;
import static org.folio.rest.RestVerticle.STREAM_COMPLETE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.annotations.Stream;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceDocument;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.ValidateFundDistributionsRequest;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class InvoicesImpl extends BaseApi implements org.folio.rest.jaxrs.resource.Invoice {

  private static final Logger logger = LogManager.getLogger(InvoicesImpl.class);
  private static final String NOT_SUPPORTED = "Not supported"; // To overcome sonarcloud warning
  private static final String INVOICE_LOCATION_PREFIX = "/invoice/invoices/%s";
  private static final String INVOICE_LINE_LOCATION_PREFIX = "/invoice/invoice-lines/%s";
  public static final String PROTECTED_AND_MODIFIED_FIELDS = "protectedAndModifiedFields";
  private static final String DOCUMENTS_LOCATION_PREFIX = "/invoice/invoices/%s/documents/%s";
  private byte[] requestBytesArray = new byte[0];
  private static final long MAX_DOCUMENT_SIZE = 7 * ONE_MB;

  @Validate
  @Override
  public void postInvoiceInvoices(Invoice invoice, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext);
    RequestContext requestContext = new RequestContext(vertxContext, okapiHeaders);
    helper.createInvoice(invoice, requestContext)
      .onSuccess(invoiceWithId -> asyncResultHandler.handle(succeededFuture(helper.buildResponseWithLocation(String.format(INVOICE_LOCATION_PREFIX, invoiceWithId.getId()), invoiceWithId))))
      .onFailure(t -> {
        logger.error("Failed to create invoice ", t);
        handleErrorResponse(asyncResultHandler, helper, t);
      });

  }

  @Validate
  @Override
  public void getInvoiceInvoices(String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext);

    helper.getInvoices(limit, offset, query)
      .onSuccess(invoices -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoices))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoicesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext);

    helper.getInvoice(id)
      .onSuccess(invoice -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoice))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putInvoiceInvoicesById(String id, Invoice invoice, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    invoice.setId(id);

    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, vertxContext);
    invoiceHelper.updateInvoice(invoice)
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(invoiceHelper.buildNoContentResponse())))
      .onFailure(t -> {
        logger.error("Failed to update invoice with id={}", invoice.getId(), t);
        handleErrorResponse(asyncResultHandler, invoiceHelper, t);
      });

  }

  @Validate
  @Override
  public void deleteInvoiceInvoicesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext);

    helper.deleteInvoice(id, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceLines(String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, vertxContext);
    helper.getInvoiceLines(limit, offset, query)
      .onSuccess(lines -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(lines))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postInvoiceInvoiceLines(InvoiceLine invoiceLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, vertxContext);
    logger.info("== Creating InvoiceLine for an existing invoice ==");

    helper.createInvoiceLine(invoiceLine)
      .onSuccess(invoiceLineWithId -> asyncResultHandler.handle(succeededFuture(helper
        .buildResponseWithLocation(String.format(INVOICE_LINE_LOCATION_PREFIX, invoiceLineWithId.getId()), invoiceLineWithId))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceLinesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, vertxContext);

    invoiceLineHelper.getInvoiceLinePersistTotal(id)
      .onSuccess(invoiceLine -> asyncResultHandler.handle(succeededFuture(invoiceLineHelper.buildOkResponse(invoiceLine))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, invoiceLineHelper, t));
  }

  @Validate
  @Override
  public void putInvoiceInvoiceLinesById(String invoiceLineId, InvoiceLine invoiceLine,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLinesHelper = new InvoiceLineHelper(okapiHeaders, vertxContext);

    if (StringUtils.isEmpty(invoiceLine.getId())) {
      invoiceLine.setId(invoiceLineId);
    }

    invoiceLinesHelper.updateInvoiceLine(invoiceLine, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(invoiceLinesHelper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, invoiceLinesHelper, t));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoiceLinesById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, vertxContext);

    invoiceLineHelper.deleteInvoiceLine(id)
      .onSuccess(invoiceLine -> asyncResultHandler.handle(succeededFuture(invoiceLineHelper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, invoiceLineHelper, t));
  }

  @Override
  @Validate
  public void putInvoiceInvoiceLinesFundDistributionsValidate(ValidateFundDistributionsRequest request,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    FundDistributionsValidationHelper helper = new FundDistributionsValidationHelper(okapiHeaders, vertxContext);
    helper.validateFundDistributions(request)
      .onSuccess(invoiceLine -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceNumber(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(GetInvoiceInvoiceNumberResponse.respond500WithTextPlain(NOT_SUPPORTED)));
  }

  @Validate
  @Override
  public void getInvoiceInvoicesDocumentsById(String id, String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext);
    documentHelper.getDocumentsByInvoiceId(id, limit, offset, query)
      .onSuccess(documents -> asyncResultHandler.handle(succeededFuture(documentHelper.buildOkResponse(documents))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
  }

  @Validate
  @Stream
  @Override
  public void postInvoiceInvoicesDocumentsById(String id, InputStream is, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try (InputStream bis = new BufferedInputStream(is)) {
      if (Objects.isNull(okapiHeaders.get(STREAM_COMPLETE))) {
        // This code will be executed for each stream's chunk
        processBytesArrayFromStream(bis);
      } else if (Objects.nonNull(okapiHeaders.get(STREAM_ABORT))) {
        asyncResultHandler.handle(succeededFuture(PostInvoiceInvoicesDocumentsByIdResponse.respond400WithTextPlain("Stream aborted")));
      } else {
        // This code will be executed one time after stream completing
        if (Objects.nonNull(requestBytesArray)) {
          processDocumentCreation(id, okapiHeaders, asyncResultHandler, vertxContext);
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
  public void getInvoiceInvoicesDocumentsByIdAndDocumentId(String id, String documentId,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext);
    documentHelper.getDocumentByInvoiceIdAndDocumentId(id, documentId)
      .onSuccess(document -> asyncResultHandler.handle(succeededFuture(documentHelper.buildOkResponse(document))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoicesDocumentsByIdAndDocumentId(String invoiceId, String documentId,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext);

    documentHelper.deleteDocument(invoiceId, documentId)
      .onSuccess(invoiceLine -> asyncResultHandler.handle(succeededFuture(documentHelper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoicesFiscalYearsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext);
    helper.getFiscalYearsByInvoiceId(id)
      .onSuccess(fiscalYearCollection -> asyncResultHandler.handle(
        succeededFuture(helper.buildOkResponse(fiscalYearCollection))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
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
  }

  private void processDocumentCreation(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DocumentHelper documentHelper = new DocumentHelper(okapiHeaders, vertxContext);
    InvoiceDocument entity = new JsonObject(new String(requestBytesArray, StandardCharsets.UTF_8)).mapTo(InvoiceDocument.class);
    if (!entity.getDocumentMetadata().getInvoiceId().equals(id)) {
      asyncResultHandler.handle(succeededFuture(documentHelper.buildErrorResponse(new HttpException(422, MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY))));
    } else {
      documentHelper.createDocument(id, entity)
        .onSuccess(document -> asyncResultHandler.handle(succeededFuture(documentHelper
          .buildResponseWithLocation(String.format(DOCUMENTS_LOCATION_PREFIX, id, document.getDocumentMetadata().getId()), document))))
        .onFailure(t -> handleErrorResponse(asyncResultHandler, documentHelper, t));
    }
  }
}
