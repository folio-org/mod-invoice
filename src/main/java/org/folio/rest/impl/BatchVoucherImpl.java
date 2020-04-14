package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVouchers;
import org.folio.helpers.BatchVoucherHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class BatchVoucherImpl implements  BatchVoucherBatchVouchers {

  @Validate
  @Override
  public void getBatchVoucherBatchVouchersById(String id, String lang, String contentType, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherHelper helper = new BatchVoucherHelper(okapiHeaders, vertxContext, lang);
    helper.getBatchVoucherById(id, contentType)
      .thenAccept(response -> asyncResultHandler.handle(succeededFuture(response)))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
