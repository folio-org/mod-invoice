package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class VouchersImpl extends BaseApi implements org.folio.rest.jaxrs.resource.Voucher {

  private static final Logger logger = LogManager.getLogger(VouchersImpl.class);

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper,
                                   Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }

  @Override
  @Validate
  public void getVoucherVouchersById(String id, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, vertxContext);
    voucherHelper.getVoucher(id, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(voucher -> asyncResultHandler.handle(succeededFuture(buildOkResponse(voucher))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, t));
  }

  @Override
  @Validate
  public void putVoucherVouchersById(String id, Voucher entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, vertxContext);
    voucherHelper.partialVoucherUpdate(id, entity, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(voucher -> asyncResultHandler.handle(succeededFuture(buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, t));
  }

  @Override
  @Validate
  public void getVoucherVoucherLines(String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherLineHelper voucherLineHelper = new VoucherLineHelper(okapiHeaders, vertxContext);
    voucherLineHelper.getVoucherLines(limit, offset, query)
      .onSuccess(voucherLines -> asyncResultHandler.handle(succeededFuture(voucherLineHelper.buildOkResponse(voucherLines))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, voucherLineHelper, t));
  }

  @Validate
  @Override
  public void getVoucherVoucherLinesById(String id, Map<String, String> okapiHeaders,
                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Get Voucher Line by Id for an existing Voucher ==");
    VoucherLineHelper voucherLineHelper = new VoucherLineHelper(okapiHeaders, vertxContext);
    voucherLineHelper.getVoucherLine(id)
      .onSuccess(voucherLine -> asyncResultHandler.handle(succeededFuture(voucherLineHelper.buildOkResponse(voucherLine))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, voucherLineHelper, t));
  }

  @Override
  @Validate
  public void putVoucherVoucherLinesById(String voucherLineId, VoucherLine voucherLine, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Update Voucher Line by Id for an existing Voucher ==");
    VoucherLineHelper voucherLinesHelper = new VoucherLineHelper(okapiHeaders, vertxContext);

    if (StringUtils.isEmpty(voucherLine.getId())) {
      voucherLine.setId(voucherLineId);
    }
    voucherLinesHelper.updateVoucherLine(voucherLine)
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(voucherLinesHelper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, voucherLinesHelper, t));
  }

  @Validate
  @Override
  public void postVoucherVoucherNumberStartByValue(String value, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Re(set) the current start value of the voucher number sequence ==");
    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, vertxContext);

    voucherHelper.setStartValue(value, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(buildNoContentResponse())))
      .onFailure(fail -> handleErrorResponse(asyncResultHandler, fail));
  }

  @Override
  @Validate
  public void getVoucherVoucherNumberStart(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("== Getting the current start value of the voucher number sequence ==");
    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, vertxContext);

    voucherHelper.getStartValue(new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(number -> asyncResultHandler.handle(succeededFuture(buildOkResponse(number))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, t));
  }

  @Validate
  @Override
  public void getVoucherVouchers(String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherHelper voucherHelper = new VoucherHelper(okapiHeaders, vertxContext);

    voucherHelper.getVouchers(query, offset, limit, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(vouchers -> {
        logger.info("Successfully retrieved vouchers: {}", JsonObject.mapFrom(vouchers).encodePrettily());
        asyncResultHandler.handle(succeededFuture(buildOkResponse(vouchers)));
      })
      .onFailure(t -> handleErrorResponse(asyncResultHandler, t));
  }
}
