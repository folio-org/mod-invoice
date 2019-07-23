package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import java.util.Map;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;


import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignment;
import org.folio.rest.jaxrs.model.VoucherLine;
import static org.folio.invoices.utils.ErrorCodes.MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY;

public class VouchersImpl implements org.folio.rest.jaxrs.resource.Voucher {

  private static final Logger logger = LoggerFactory.getLogger(VouchersImpl.class);

  private static final String VOUCHER_ACQUISITIONS_UNIT_ASSIGNMENTS_LOCATION_PREFIX = "/voucher/acquisitions-unit-assignments/%s";
  
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
          logger.info("Successfully retrieved vouchers: " + JsonObject.mapFrom(vouchers)
            .encodePrettily());
        }
        asyncResultHandler.handle(succeededFuture(vouchersHelper.buildOkResponse(vouchers)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, vouchersHelper, t));
  }

  @Validate
  @Override
  public void postVoucherAcquisitionsUnitAssignments(String lang, AcquisitionsUnitAssignment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherAcquisitionsUnitAssignmentsHelper helper = new VoucherAcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.createAcquisitionsUnitAssignment(entity)
     .thenAccept(unit -> {
       logInfo("Successfully created new acquisitions unit: {}", unit);
       asyncResultHandler.handle(succeededFuture(
           helper.buildResponseWithLocation(String.format(VOUCHER_ACQUISITIONS_UNIT_ASSIGNMENTS_LOCATION_PREFIX, unit.getId()), unit)));
     })
     .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getVoucherAcquisitionsUnitAssignments(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherAcquisitionsUnitAssignmentsHelper helper = new VoucherAcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.getAcquisitionsUnitAssignments(query, offset, limit)
     .thenAccept(units -> {
       logInfo("Successfully retrieved acquisitions unit assignment : {}", (units));
       asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(units)));
     })
     .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putVoucherAcquisitionsUnitAssignmentsById(String id, String lang, AcquisitionsUnitAssignment entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherAcquisitionsUnitAssignmentsHelper helper = new VoucherAcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    if (entity.getId() != null && !entity.getId()
     .equals(id)) {
     helper.addProcessingError(MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY.toError());
     asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(422)));
   } else {
     helper.updateAcquisitionsUnitAssignment(entity.withId(id))
       .thenAccept(units -> {
        logInfo("Successfully updated acquisitions unit assignment with id={}", id);
         asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse()));
       })
       .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
   }
  }

  @Validate
  @Override
  public void getVoucherAcquisitionsUnitAssignmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherAcquisitionsUnitAssignmentsHelper helper = new VoucherAcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.getAcquisitionsUnitAssignment(id)
     .thenAccept(unit -> {
       logInfo("Successfully retrieved acquisitions unit assignment: {}", unit);
       asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(unit)));
     })
     .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void deleteVoucherAcquisitionsUnitAssignmentsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    VoucherAcquisitionsUnitAssignmentsHelper helper = new VoucherAcquisitionsUnitAssignmentsHelper(okapiHeaders, vertxContext, lang);

    helper.deleteAcquisitionsUnitAssignment(id)
     .thenAccept(ok -> {
       logInfo("Successfully deleted acquisitions unit assignment with id={}", id);
       asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse()));
     })
     .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private void logInfo(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry).encodePrettily());
    }
  }
  
  private void logInfo(String message, String id) {
    if (logger.isInfoEnabled()) {
      logger.info(message, id);
    }
  }
}
