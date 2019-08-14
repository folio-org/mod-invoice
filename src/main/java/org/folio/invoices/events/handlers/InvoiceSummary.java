package org.folio.invoices.events.handlers;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.invoices.utils.HelperUtils.INVOICE;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.LANG;
import static org.folio.invoices.utils.HelperUtils.getOkapiHeaders;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.Response;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.jaxrs.model.Invoice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

@Component("invoiceSummaryHandler")
public class InvoiceSummary implements Handler<Message<JsonObject>> {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final Context ctx;

  @Autowired
  public InvoiceSummary(Vertx vertx) {
    ctx = vertx.getOrCreateContext();
  }

  @Override
  public void handle(Message<JsonObject> message) {
    JsonObject body = message.body();
    Map<String, String> okapiHeaders = getOkapiHeaders(message);

    logger.debug("Received message body: {}", body);

    InvoiceHelper helper = new InvoiceHelper(okapiHeaders, ctx, body.getString(LANG));

    getInvoiceRecord(helper, body)
      .thenCompose(invoice -> helper.recalculateTotals(invoice)
        .thenCompose(isOutOfSync -> {
          if (isOutOfSync) {
            logger.debug("The invoice with id={} is out of sync in storage and requires updates", invoice.getId());
            return helper.updateInvoiceRecord(invoice);
          } else {
            logger.debug("The invoice with id={} is up to date in storage", invoice.getId());
            return completedFuture(null);
          }
        }))
      .handle((ok, fail) -> {
        // Sending reply message just in case some logic requires it
        if (fail == null) {
          message.reply(Response.Status.OK.getReasonPhrase());
        } else {
          Throwable cause = fail.getCause();
          int code = INTERNAL_SERVER_ERROR.getStatusCode();
          if (cause instanceof HttpException) {
            code = ((HttpException) cause).getCode();
          }
          message.fail(code, cause.getMessage());
        }
        helper.closeHttpClient();
        return null;
      });
  }

  private CompletableFuture<Invoice> getInvoiceRecord(InvoiceHelper helper, JsonObject body) {
    if (body.containsKey(INVOICE)) {
      return VertxCompletableFuture.supplyAsync(ctx, () -> body.getJsonObject(INVOICE).mapTo(Invoice.class));
    } else {
      return helper.getInvoiceRecord(body.getString(INVOICE_ID));
    }
  }
}
