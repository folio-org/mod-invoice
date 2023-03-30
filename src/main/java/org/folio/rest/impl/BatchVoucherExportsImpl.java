package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.BatchVoucherBatchVoucherExports.PostBatchVoucherBatchVoucherExportsUploadByIdResponse.respond202WithApplicationJson;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVoucherExports;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class BatchVoucherExportsImpl implements BatchVoucherBatchVoucherExports {
  private static final String BATCH_VOUCHER_EXPORTS_LOCATION_PREFIX = "/batch-voucher/batch-voucher-exports/%s";
  private static final String NOT_SUPPORTED = "Not supported";  // To overcome sonarcloud warning
  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExports(BatchVoucherExport entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext);
    helper.createBatchVoucherExports(entity)
      .onSuccess(bve -> asyncResultHandler.handle(succeededFuture(
          helper.buildResponseWithLocation(String.format(BATCH_VOUCHER_EXPORTS_LOCATION_PREFIX, bve.getId()), bve))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchVoucherBatchVoucherExports(String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext);
    helper.getBatchVoucherExports(limit, offset, query)
      .onSuccess(batchVoucherExports -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchVoucherExports))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchVoucherBatchVoucherExportsById(String id, BatchVoucherExport batchVoucherExport,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext);

    helper.updateBatchVoucherExportRecord(batchVoucherExport)
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(ex -> handleErrorResponse(asyncResultHandler, helper, ex));
  }

  @Validate
  @Override
  public void deleteBatchVoucherBatchVoucherExportsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext);

    helper.deleteBatchVoucherExportById(id)
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  @Validate
  @Override
  public void getBatchVoucherBatchVoucherExportsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext);
    helper.getBatchVoucherExportById(id)
      .onSuccess(batchVoucherExport -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchVoucherExport))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExportsUploadById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    UploadBatchVoucherExportHelper uploadService = new UploadBatchVoucherExportHelper(okapiHeaders, vertxContext);
    uploadService.uploadBatchVoucherExport(id)
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(respond202WithApplicationJson(v))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, uploadService, t));
  }

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExportsScheduled(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PostBatchVoucherBatchVoucherExportsScheduledResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
