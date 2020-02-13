package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.ExportConfigCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class BatchVoucherExportConfigHelper extends AbstractHelper {

  public static final String GET_EXPORT_CONFIGS_BY_QUERY = resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS) + SEARCH_PARAMS;

  BatchVoucherExportConfigHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  BatchVoucherExportConfigHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  public CompletableFuture<ExportConfig> createExportConfig(ExportConfig exportConfig) {
    return CompletableFuture.supplyAsync(() -> JsonObject.mapFrom(exportConfig))
      .thenCompose(jsonExportConfig -> createRecordInStorage(jsonExportConfig,
          resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS)).thenApply(exportConfig::withId));
  }

  public CompletableFuture<ExportConfig> getExportConfig(String id) {
    CompletableFuture<ExportConfig> future = new VertxCompletableFuture<>(ctx);

    try {
      handleGetRequest(resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, id, lang), httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonExportConfig -> {
          logger.info("Successfully retrieved batch voucher export configuration: " + jsonExportConfig.encodePrettily());
          future.complete(jsonExportConfig.mapTo(ExportConfig.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting batch voucher export configuration by id ", id);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  public CompletableFuture<Void> putExportConfig(ExportConfig exportConfig) {
    JsonObject jsonExportConfig = JsonObject.mapFrom(exportConfig);
    String path = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, exportConfig.getId(), lang);
    return handlePutRequest(path, jsonExportConfig, httpClient, ctx, okapiHeaders, logger);
  }

  public CompletableFuture<Void> deleteExportConfig(String id) {
    String path = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, id, lang);
    return handleDeleteRequest(path, httpClient, ctx, okapiHeaders, logger);
  }

  public CompletableFuture<ExportConfigCollection> getExportConfigs(int limit, int offset, String query) {
    String endpoint = String.format(GET_EXPORT_CONFIGS_BY_QUERY, limit, offset, query, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(json -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> json.mapTo(ExportConfigCollection.class)));
  }
}
