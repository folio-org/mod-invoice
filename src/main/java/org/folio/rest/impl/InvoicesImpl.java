package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.folio.HttpStatus;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rulez.Rules;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieSession;

import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.vertx.core.Future.succeededFuture;

public class InvoicesImpl implements org.folio.rest.jaxrs.resource.Invoice {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesImpl.class);
  private static final String NOT_SUPPORTED = "Not supported";  // To overcome sonarcloud warning
  private static final String INVOICE_LOCATION_PREFIX = "/invoice/invoices/%s";
  private static final String INVOICE_LINE_LOCATION_PREFIX = "/invoice/invoice-lines/%s";
  static final String PROTECTED_AND_MODIFIED_FIELDS = "protectedAndModifiedFields";
  private static final String RULES_FILE_PATH = "src/main/resources/rules/not_autostarted";
  private static final String $_PROTECTED_AND_MODIFIED_FIELDS = "$protectedAndModifiedFields";
  private static final String $_EXISTED_INVOICE = "$existedInvoice";
  private static final String $_EXISTED_INVOICE_LINE = "$existedInvoiceLine";

  private KieSession session;

  public InvoicesImpl() throws Exception {
      session = new Rules(RULES_FILE_PATH).buildSession();
      session.addEventListener(new RuleRuntimeEventListener() {
        @Override
        public void objectInserted(ObjectInsertedEvent event) {
          session.fireAllRules();
        }

        @Override
        public void objectUpdated(ObjectUpdatedEvent event) {}

        @Override
        public void objectDeleted(ObjectDeletedEvent event) {}
      });
  }

  @Validate
  @Override
  public void postInvoiceInvoices(String lang, Invoice invoice, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper.createInvoice(invoice)
      .thenAccept(invoiceWithId -> {
        Response response = PostInvoiceInvoicesResponse.respond201WithApplicationJson(invoiceWithId,
        PostInvoiceInvoicesResponse.headersFor201()
          .withLocation(String.format(INVOICE_LOCATION_PREFIX, invoiceWithId.getId())));
          asyncResultHandler.handle(succeededFuture(response));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
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

  @Validate
  @Override
  public void getInvoiceInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper
      .getInvoice(id)
      .thenAccept(invoice -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(invoice))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putInvoiceInvoicesById(String id, String lang, Invoice invoice, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    invoice.setId(id);

    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    invoiceHelper
      .getInvoice(id)
      .thenAccept(existed -> {
        final Consumer<Void> success = ok -> asyncResultHandler.handle(succeededFuture(invoiceHelper.buildNoContentResponse()));
        if(invoice.getStatus() == Invoice.Status.APPROVED || invoice.getStatus() == Invoice.Status.PAID || invoice.getStatus() == Invoice.Status.CANCELLED) {
          Set<String> protectedAndModifiedFields = new HashSet<>();
          session.setGlobal($_PROTECTED_AND_MODIFIED_FIELDS, protectedAndModifiedFields);
          session.setGlobal($_EXISTED_INVOICE, existed);
          session.insert(invoice);
          if(protectedAndModifiedFields.isEmpty()) {
            invoiceHelper.updateInvoice(invoice, existed)
              .thenAccept(success);
          } else {
            invoiceHelper.addProcessingError(ErrorCodes.PROHIBITED_FIELD_CHANGING.toError().withAdditionalProperty(PROTECTED_AND_MODIFIED_FIELDS, protectedAndModifiedFields));
            asyncResultHandler.handle(succeededFuture(invoiceHelper.buildErrorResponse(HttpStatus.HTTP_BAD_REQUEST.toInt())));
          }
        } else {
          invoiceHelper.updateInvoice(invoice, existed)
            .thenAccept(success);
        }
      }).exceptionally(fail -> handleErrorResponse(asyncResultHandler, invoiceHelper, fail));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoicesById(String id, String lang, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    helper
      .deleteInvoice(id)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceLines(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);
    helper
      .getInvoiceLines(limit, offset, query)
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
      .thenAccept(invoiceLineWithId -> {
        Response response = PostInvoiceInvoiceLinesResponse.respond201WithApplicationJson(invoiceLineWithId,
            PostInvoiceInvoiceLinesResponse.headersFor201()
              .withLocation(String.format(INVOICE_LINE_LOCATION_PREFIX, invoiceLineWithId.getId())));
        asyncResultHandler.handle(succeededFuture(response));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);

    invoiceLineHelper
      .getInvoiceLine(id)
      .thenAccept(invoiceLine -> asyncResultHandler.handle(succeededFuture(invoiceLineHelper.buildOkResponse(invoiceLine))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLineHelper, t));
  }

  @Validate
  @Override
  public void putInvoiceInvoiceLinesById(String invoiceLineId, String lang, InvoiceLine invoiceLine,
                                         Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLinesHelper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, vertxContext, lang);

    if (StringUtils.isEmpty(invoiceLine.getId())) {
      invoiceLine.setId(invoiceLineId);
    }

    invoiceLinesHelper.getInvoiceLine(invoiceLineId)
      .thenAccept(existedInvoiceLine -> invoiceHelper.getInvoice(existedInvoiceLine.getInvoiceId())
        .thenAccept(existedInvoice -> {
          Consumer<Void> success = vVoid -> asyncResultHandler.handle(succeededFuture(invoiceLinesHelper.buildNoContentResponse()));
          if(existedInvoice.getStatus() == Invoice.Status.APPROVED || existedInvoice.getStatus() == Invoice.Status.PAID || existedInvoice.getStatus() == Invoice.Status.CANCELLED) {
            Set<String> protectedAndModifiedFields = new HashSet<>();
            session.setGlobal($_PROTECTED_AND_MODIFIED_FIELDS, protectedAndModifiedFields);
            session.setGlobal($_EXISTED_INVOICE_LINE, existedInvoiceLine);
            session.insert(invoiceLine);
            if(protectedAndModifiedFields.isEmpty()) {
              invoiceLinesHelper.updateInvoiceLine(invoiceLine)
                .thenAccept(success);
            } else {
              invoiceLinesHelper.addProcessingError(ErrorCodes.PROHIBITED_FIELD_CHANGING.toError().withAdditionalProperty(PROTECTED_AND_MODIFIED_FIELDS, protectedAndModifiedFields));
              asyncResultHandler.handle(succeededFuture(invoiceLinesHelper.buildErrorResponse(HttpStatus.HTTP_BAD_REQUEST.toInt())));
            }
          } else {
            invoiceLinesHelper.updateInvoiceLine(invoiceLine)
              .thenAccept(success)
              .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLinesHelper, t));
          }
        })
        .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLinesHelper, t))).exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLinesHelper, t));
  }

  @Validate
  @Override
  public void deleteInvoiceInvoiceLinesById(String id, String lang, Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    InvoiceLineHelper invoiceLineHelper = new InvoiceLineHelper(okapiHeaders, vertxContext, lang);

    invoiceLineHelper
      .deleteInvoiceLine(id)
      .thenAccept(invoiceLine -> asyncResultHandler.handle(succeededFuture(invoiceLineHelper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, invoiceLineHelper, t));
  }

  @Validate
  @Override
  public void getInvoiceInvoiceNumber(String lang, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(GetInvoiceInvoiceNumberResponse.respond500WithTextPlain(NOT_SUPPORTED)));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper,
                                   Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
