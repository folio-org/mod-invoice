package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.resource.Voucher;

public class VouchersImpl implements Voucher {

  @Override
  @Validate
  public void getVoucherVouchersById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherHelper helper = new VoucherHelper(okapiHeaders, vertxContext, lang);
    helper.getVoucher(id)
      .thenAccept(voucher -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(voucher))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));

  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;

  }
}
