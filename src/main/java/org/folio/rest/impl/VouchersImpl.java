package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class VouchersImpl implements org.folio.rest.jaxrs.resource.Voucher {

  private static final Logger logger = LogManager.getLogger(VouchersImpl.class);

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

  @Override
  @Validate
  public void putVoucherVouchersById(String id, String lang, Voucher entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherHelper helper = new VoucherHelper(okapiHeaders, vertxContext, lang);

    helper.partialVoucherUpdate(id, entity)
      .thenAccept(voucher -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Override
  @Validate
  public void getVoucherVoucherLines(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherLineHelper voucherLineHelper = new VoucherLineHelper(okapiHeaders, vertxContext, lang);
    voucherLineHelper.getVoucherLines(limit, offset, query)
      .thenAccept(voucherLines -> asyncResultHandler.handle(succeededFuture(voucherLineHelper.buildOkResponse(voucherLines))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, voucherLineHelper, t));
  }

  @Validate
  @Override
  public void getVoucherVoucherLinesById(String id, String lang, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Get Voucher Line by Id for an existing Voucher ==");
    VoucherLineHelper voucherLineHelper = new VoucherLineHelper(okapiHeaders, vertxContext, lang);
    voucherLineHelper.getVoucherLine(id)
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

  @Validate
  @Override
  public void getVoucherVouchers(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherHelper vouchersHelper = new VoucherHelper(okapiHeaders, vertxContext, lang);

    vouchersHelper.getVouchers(limit, offset, query)
      .thenAccept(vouchers -> {
        if (logger.isInfoEnabled()) {
          logger.info("Successfully retrieved vouchers: {}", JsonObject.mapFrom(vouchers)
            .encodePrettily());
        }
        asyncResultHandler.handle(succeededFuture(vouchersHelper.buildOkResponse(vouchers)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, vouchersHelper, t));
  }
}
