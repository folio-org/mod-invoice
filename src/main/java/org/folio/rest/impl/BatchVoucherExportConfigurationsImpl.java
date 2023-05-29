package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.Message;
import org.folio.rest.jaxrs.resource.BatchVoucherExportConfigurations;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class BatchVoucherExportConfigurationsImpl implements BatchVoucherExportConfigurations {
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_LOCATION_PREFIX = "/batch-voucher/export-configurations/%s";
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_LOCATION_PREFIX = "/batch-voucher/export-configurations/%s/credentials";

  @Validate
  @Override
  public void getBatchVoucherExportConfigurations(String totalRecords, int offset, int limit, String query,
                                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.getExportConfigs(limit, offset, query)
      .onSuccess(exportConfigCollection -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(exportConfigCollection))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherExportConfigurations(ExportConfig entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.createExportConfig(entity)
      .onSuccess(exportConfigWithId -> asyncResultHandler.handle(succeededFuture(helper.buildResponseWithLocation(
        String.format(BATCH_VOUCHER_EXPORT_CONFIGS_LOCATION_PREFIX, exportConfigWithId.getId()), exportConfigWithId))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchVoucherExportConfigurationsById(String id, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.getExportConfig(id)
      .onSuccess(exportConfig -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(exportConfig))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void deleteBatchVoucherExportConfigurationsById(String id, Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.deleteExportConfig(id)
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchVoucherExportConfigurationsById(String id, ExportConfig exportConfig,
                                                      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    exportConfig.setId(id);

    helper.putExportConfig(exportConfig)
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherExportConfigurationsCredentialsById(String id, Credentials entity,
                                                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.createCredentials(id, entity)
      .onSuccess(credentials -> asyncResultHandler.handle(succeededFuture(helper
        .buildResponseWithLocation(String.format(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_LOCATION_PREFIX, id), credentials))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchVoucherExportConfigurationsCredentialsById(String id, Map<String, String> okapiHeaders,
                                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.getExportConfigCredentials(id)
      .onSuccess(credentials -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(credentials))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchVoucherExportConfigurationsCredentialsById(String id, Credentials entity,
                                                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);

    helper.putExportConfigCredentials(id, entity)
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherExportConfigurationsCredentialsTestById(String id, Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext);
    helper.testUploadUri(id)
      .onSuccess(msg -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(new Message().withMessage(msg)))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
