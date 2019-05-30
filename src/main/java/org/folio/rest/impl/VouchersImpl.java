package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import java.util.Map;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;


import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.rest.jaxrs.resource.Voucher;

public class VouchersImpl implements Voucher {

  private static final Logger logger = LoggerFactory.getLogger(VouchersImpl.class);

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper,
                                   Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

  @Override
  @Validate
  public void getVoucherVouchersById(String id, String lang, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherHelper helper = new VoucherHelper(okapiHeaders, vertxContext, lang);
    helper.getVoucher(id)
      .thenAccept(voucher -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(voucher))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }
  
  @Validate
  @Override
  public void getVoucherVoucherLinesById(String id, String lang, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Get Voucher Line by Id for an existing Voucher ==");
    VoucherLineHelper voucherLineHelper = new VoucherLineHelper(okapiHeaders, vertxContext, lang);
    voucherLineHelper.getVoucherLines(id)
      .thenAccept(voucherLine -> asyncResultHandler.handle(succeededFuture(voucherLineHelper.buildOkResponse(voucherLine))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, voucherLineHelper, t));
  }

  @Override
  @Validate
  public void putVoucherVoucherLinesById(String voucherLineId, String lang, VoucherLine voucherLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Update Voucher Line by Id for an existing Voucher ==");
    VoucherLineHelper voucherLinesHelper = new VoucherLineHelper(okapiHeaders, vertxContext, lang);

    if (StringUtils.isEmpty(voucherLine.getId())) {
      voucherLine.setId(voucherLineId);
    }
    voucherLinesHelper.updateVoucherLine(voucherLine)
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(voucherLinesHelper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, voucherLinesHelper, t));
  }

  @Validate
  @Override
  public void postVoucherVoucherNumberStartByValue(String value, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Re(set) the current start value of the voucher number sequence ==");
    VoucherHelper helper = new VoucherHelper(okapiHeaders, vertxContext, lang);
    helper.setStartValue(value)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  @Override
  @Validate
  public void getVoucherVoucherNumberStart(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Getting the current start value of the voucher number sequence ==");

    VoucherHelper helper = new VoucherHelper(okapiHeaders, vertxContext, lang);

    helper.getVoucherNumberStartValue()
      .thenAccept(number -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(number))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }
}
