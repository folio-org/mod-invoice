package org.folio.rest.impl;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.Message;
import org.folio.rest.jaxrs.resource.BatchVoucherExportConfigurations;
import javax.ws.rs.core.Response;
import java.util.Map;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import static io.vertx.core.Future.succeededFuture;

public class BatchVoucherExportConfigurationsImpl implements BatchVoucherExportConfigurations {
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_LOCATION_PREFIX = "/batch-voucher/export-configurations/%s";
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_LOCATION_PREFIX = "/batch-voucher/export-configurations/%s/credentials";

  @Validate
  @Override
  public void getBatchVoucherExportConfigurations(int offset, int limit, String query, String lang,
                                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.getExportConfigs(limit, offset, query)
      .thenAccept(lines -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(lines))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherExportConfigurations(String lang, ExportConfig entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.createExportConfig(entity)
      .thenAccept(exportConfigWithId -> asyncResultHandler.handle(succeededFuture(helper.buildResponseWithLocation(
        String.format(BATCH_VOUCHER_EXPORT_CONFIGS_LOCATION_PREFIX, exportConfigWithId.getId()), exportConfigWithId))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchVoucherExportConfigurationsById(String id, String lang, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.getExportConfig(id)
      .thenAccept(exportConfig -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(exportConfig))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void deleteBatchVoucherExportConfigurationsById(String id, String lang, Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.deleteExportConfig(id)
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchVoucherExportConfigurationsById(String id, String lang, ExportConfig exportConfig,
                                                      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    exportConfig.setId(id);

    helper.putExportConfig(exportConfig)
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherExportConfigurationsCredentialsById(String id, String lang, Credentials entity,
                                                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.createCredentials(id, entity)
      .thenAccept(credentials -> asyncResultHandler.handle(succeededFuture(helper
        .buildResponseWithLocation(String.format(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_LOCATION_PREFIX, id), credentials))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchVoucherExportConfigurationsCredentialsById(String id, String lang, Map<String, String> okapiHeaders,
                                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.getExportConfigCredentials(id)
      .thenAccept(credentials -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(credentials))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchVoucherExportConfigurationsCredentialsById(String id, String lang, Credentials entity,
                                                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);

    helper.putExportConfigCredentials(id, entity)
      .thenAccept(v -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void postBatchVoucherExportConfigurationsCredentialsTestById(String id, String lang, Map<String, String> okapiHeaders,
                                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchVoucherExportConfigHelper helper = new BatchVoucherExportConfigHelper(okapiHeaders, vertxContext, lang);
    helper.testUploadUri(id)
      .thenAccept(msg -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(new Message().withMessage(msg)))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
