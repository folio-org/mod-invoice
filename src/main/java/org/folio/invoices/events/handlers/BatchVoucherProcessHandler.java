package org.folio.invoices.events.handlers;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.invoices.utils.HelperUtils.BATCH_VOUCHER_EXPORT;
import static org.folio.invoices.utils.HelperUtils.getOkapiHeaders;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.BatchVoucherGenerationException;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.impl.BatchVoucherPersistHelper;
import org.folio.rest.impl.UploadBatchVoucherExportHelper;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

@Component("batchVoucherProcessHandler")
public class BatchVoucherProcessHandler implements Handler<Message<JsonObject>> {
  protected final Logger logger = LogManager.getLogger(this.getClass());
  private final Context ctx;

  @Autowired
  public BatchVoucherProcessHandler(Vertx vertx) {
    ctx = vertx.getOrCreateContext();
  }

  @Override
  public void handle(Message<JsonObject> message) {
    JsonObject body = message.body();
    Map<String, String> okapiHeaders = getOkapiHeaders(message);

    BatchVoucherPersistHelper manager = new BatchVoucherPersistHelper(okapiHeaders, ctx);
    UploadBatchVoucherExportHelper uploadService = new UploadBatchVoucherExportHelper(okapiHeaders, ctx);

    var bvExport = getBatchVoucherExportBody(body);
    manager.persistBatchVoucher(bvExport)
      .map(bvId -> {
        bvExport.setBatchVoucherId(bvId);
        isBatchVoucherCreated(bvExport);
        return null;
      })
      .compose(v -> uploadService.uploadBatchVoucherExport(bvExport))
      .onComplete(asyncResult -> {
        // Sending reply message just in case some logic requires it
        if (asyncResult.succeeded()) {
          message.reply(Response.Status.OK.getReasonPhrase());
        } else {
          Throwable cause = asyncResult.cause();
          int code = INTERNAL_SERVER_ERROR.getStatusCode();
          if (cause instanceof HttpException) {
            code = ((HttpException) cause).getCode();
          }
          message.fail(code, cause.getMessage());
        }
      });
  }

  private void isBatchVoucherCreated(BatchVoucherExport bvExport) {
    if(bvExport.getStatus() == BatchVoucherExport.Status.ERROR) {
      throw new BatchVoucherGenerationException(bvExport.getMessage());
    }
  }

  private BatchVoucherExport getBatchVoucherExportBody(JsonObject body) {
     return body.getJsonObject(BATCH_VOUCHER_EXPORT)
       .mapTo(BatchVoucherExport.class);
  }
}
