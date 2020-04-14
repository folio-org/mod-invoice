package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVoucherExports;
import org.folio.helpers.BatchVoucherExportsHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchVoucherExportsImpl implements BatchVoucherBatchVoucherExports {

  private static final Logger logger = LoggerFactory.getLogger(BatchVoucherExportsImpl.class);
  private static final String BATCH_VOUCHER_EXPORTS_LOCATION_PREFIX = "/batch-voucher/batch-voucher-exports/%s";
  private static final String NOT_SUPPORTED = "Not supported";  // To overcome sonarcloud warning

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExports(String lang, BatchVoucherExport entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper service = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);
    service.createBatchVoucherExports(entity)
      .thenAccept(bve -> asyncResultHandler.handle(succeededFuture(
          service.buildResponseWithLocation(String.format(BATCH_VOUCHER_EXPORTS_LOCATION_PREFIX, bve.getId()), bve))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, service, t));
  }

  @Validate
  @Override
  public void getBatchVoucherBatchVoucherExports(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper service = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);
    service.getBatchVoucherExports(limit, offset, query)
      .thenAccept(batchVoucherExports -> {
        logInfo("Successfully retrieved batch voucher exports: {}", batchVoucherExports);
        asyncResultHandler.handle(succeededFuture(service.buildOkResponse(batchVoucherExports)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, service, t));
  }

  @Validate
  @Override
  public void putBatchVoucherBatchVoucherExportsById(String id, String lang, BatchVoucherExport batchVoucherExport,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper service = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);

    service.updateBatchVoucherExportRecord(batchVoucherExport)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(service.buildNoContentResponse())))
      .exceptionally(ex -> handleErrorResponse(asyncResultHandler, service, ex));
  }

  @Validate
  @Override
  public void deleteBatchVoucherBatchVoucherExportsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper service = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);

    service.deleteBatchVoucherExportById(id)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(service.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, service, fail));
  }

  @Validate
  @Override
  public void getBatchVoucherBatchVoucherExportsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper service = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);
    service.getBatchVoucherExportById(id)
      .thenAccept(batchVoucherExport -> asyncResultHandler.handle(succeededFuture(service.buildOkResponse(batchVoucherExport))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, service, t));
  }

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExportsUploadById(String id, String lang, Map<String, String> okapiHeaders,
                                                            Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PostBatchVoucherBatchVoucherExportsUploadByIdResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
  }

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExportsScheduled(String lang, Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PostBatchVoucherBatchVoucherExportsScheduledResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
  }

  private void logInfo(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry)
        .encodePrettily());
    }
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper service, Throwable t) {
    asyncResultHandler.handle(succeededFuture(service.buildErrorResponse(t)));
    return null;
  }
}
