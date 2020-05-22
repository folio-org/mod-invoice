package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.resource.BatchVoucherBatchVoucherExports.PostBatchVoucherBatchVoucherExportsUploadByIdResponse.respond202WithApplicationJson;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVoucherExports;
import org.folio.services.voucher.UploadBatchVoucherExportService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class BatchVoucherExportsImpl implements BatchVoucherBatchVoucherExports {
  private static final String BATCH_VOUCHER_EXPORTS_LOCATION_PREFIX = "/batch-voucher/batch-voucher-exports/%s";
  private static final String NOT_SUPPORTED = "Not supported";  // To overcome sonarcloud warning

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExports(String lang, BatchVoucherExport entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);
    helper.createBatchVoucherExports(entity)
      .thenAccept(bve -> asyncResultHandler.handle(succeededFuture(
          helper.buildResponseWithLocation(String.format(BATCH_VOUCHER_EXPORTS_LOCATION_PREFIX, bve.getId()), bve))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchVoucherBatchVoucherExports(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);
    helper.getBatchVoucherExports(limit, offset, query)
      .thenAccept(batchVoucherExports -> {
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchVoucherExports)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchVoucherBatchVoucherExportsById(String id, String lang, BatchVoucherExport batchVoucherExport,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);

    helper.updateBatchVoucherExportRecord(batchVoucherExport)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(ex -> handleErrorResponse(asyncResultHandler, helper, ex));
  }

  @Validate
  @Override
  public void deleteBatchVoucherBatchVoucherExportsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);

    helper.deleteBatchVoucherExportById(id)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  @Validate
  @Override
  public void getBatchVoucherBatchVoucherExportsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, vertxContext, lang);
    helper.getBatchVoucherExportById(id)
      .thenAccept(batchVoucherExport -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchVoucherExport))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExportsUploadById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    UploadBatchVoucherExportService uploadService = new UploadBatchVoucherExportService(okapiHeaders, vertxContext, lang);
    uploadService.uploadBatchVoucherExport(id, vertxContext)
      .thenAccept(batchVoucherExport -> asyncResultHandler.handle(succeededFuture(respond202WithApplicationJson(batchVoucherExport))))
      .exceptionally(t ->  handleErrorResponse(asyncResultHandler));
  }

  @Validate
  @Override
  public void postBatchVoucherBatchVoucherExportsScheduled(String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(succeededFuture(PostBatchVoucherBatchVoucherExportsScheduledResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler) {
    asyncResultHandler.handle(succeededFuture(PostBatchVoucherBatchVoucherExportsScheduledResponse.respond500WithApplicationJson(NOT_SUPPORTED)));
    return null;
  }
}
