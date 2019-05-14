package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.VoucherLine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VouchersImpl implements org.folio.rest.jaxrs.resource.Voucher {

  private static final Logger logger = LoggerFactory.getLogger(VouchersImpl.class);

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper,
                                   Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

  @Override
  public void postVoucherVoucherLines(String lang, VoucherLine entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }

  @Validate
  @Override
  public void getVoucherVoucherLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Get Voucher Line by Id for an existing invoice ==");
    VoucherLineHelper voucherLineHelper = new VoucherLineHelper(okapiHeaders, vertxContext, lang);
    voucherLineHelper
      .getVoucherLines(id)
      .thenAccept(voucherLine -> asyncResultHandler.handle(succeededFuture(voucherLineHelper.buildOkResponse(voucherLine))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, voucherLineHelper, t));
    
  }

  @Override
  public void deleteVoucherVoucherLinesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void putVoucherVoucherLinesById(String id, String lang, VoucherLine entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void getVoucherVoucherLines(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Auto-generated method stub
    
  }
}
