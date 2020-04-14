package org.folio.invoices.events.handlers;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.invoices.utils.HelperUtils.BATCH_VOUCHER_EXPORT;
import static org.folio.invoices.utils.HelperUtils.LANG;
import static org.folio.invoices.utils.HelperUtils.getOkapiHeaders;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.Response;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.impl.BatchVoucherPersisteHelper;
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

@Component("batchVoucherPersistHandler")
public class BatchVoucherPersistHandler implements Handler<Message<JsonObject>> {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final Context ctx;

  @Autowired
  public BatchVoucherPersistHandler(Vertx vertx) {
    ctx = vertx.getOrCreateContext();
  }

  @Override
  public void handle(Message<JsonObject> message) {
    JsonObject body = message.body();
    Map<String, String> okapiHeaders = getOkapiHeaders(message);

    logger.debug("Received message body: {}", body);

    BatchVoucherPersisteHelper manager = new BatchVoucherPersisteHelper(okapiHeaders, ctx, body.getString(LANG));
    getBatchVoucherBody(body)
      .thenCompose(manager::persistBatchVoucher)
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
        return null;
      });
  }

  private CompletableFuture<BatchVoucherExport> getBatchVoucherBody(JsonObject body) {
     return VertxCompletableFuture.supplyAsync(ctx,
       () -> body.getJsonObject(BATCH_VOUCHER_EXPORT).mapTo(BatchVoucherExport.class));
  }
}
