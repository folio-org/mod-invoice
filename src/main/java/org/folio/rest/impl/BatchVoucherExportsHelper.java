package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.BATCH_VOUCHER_EXPORT;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORTS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchVoucherExportCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class BatchVoucherExportsHelper extends AbstractHelper {
  private static final String GET_BATCH_VOUCHER_EXPORTS_BY_QUERY = resourcesPath(BATCH_VOUCHER_EXPORTS_STORAGE) + SEARCH_PARAMS;

  public BatchVoucherExportsHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
  }

  /**
   * Gets list of batch voucher exports
   *
   * @param limit  Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query  A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link BatchVoucherExportCollection} on success or an exception if processing fails
   */
  public CompletableFuture<BatchVoucherExportCollection> getBatchVoucherExports(int limit, int offset, String query) {
    String queryParam = getEndpointWithQuery(query, logger);
    String endpoint = String.format(GET_BATCH_VOUCHER_EXPORTS_BY_QUERY, limit, offset, queryParam, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenCompose(jsonVouchers -> VertxCompletableFuture
      .supplyBlockingAsync(ctx, () -> jsonVouchers.mapTo(BatchVoucherExportCollection.class)));
  }

  /**
   * Gets batch voucher export by id
   *
   * @param id batch voucher export uuid
   * @return completable future with {@link BatchVoucherExport} on success or an exception if processing fails
   */
  public CompletableFuture<BatchVoucherExport> getBatchVoucherExportById(String id) {
    CompletableFuture<BatchVoucherExport> future = new VertxCompletableFuture<>(ctx);
    getBatchVoucherExportById(id, lang, httpClient, ctx, okapiHeaders, logger).thenAccept(future::complete)
      .exceptionally(t -> {
        logger.error("Failed to retrieve batch voucher export ", t.getCause());
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  private static CompletableFuture<BatchVoucherExport> getBatchVoucherExportById(String id, String lang,
      HttpClientInterface httpClient, Context ctx, Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(BATCH_VOUCHER_EXPORTS_STORAGE, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApplyAsync(json -> json.mapTo(BatchVoucherExport.class));
  }

  /**
   * Creates a batch voucher export
   *
   * @param batchVoucherExport {@link BatchVoucherExport} to be created
   * @return completable future with {@link BatchVoucherExport} on success or an exception if processing fails
   */
  public CompletableFuture<BatchVoucherExport> createBatchVoucherExports(BatchVoucherExport batchVoucherExport) {
    return createRecordInStorage(JsonObject.mapFrom(batchVoucherExport), resourcesPath(BATCH_VOUCHER_EXPORTS_STORAGE))
                  .thenApply(batchVoucherExport::withId)
                  .thenApply(this::persistBatchVoucher);
  }

  private BatchVoucherExport persistBatchVoucher(BatchVoucherExport batchVoucherExport) {
    VertxCompletableFuture.runAsync(ctx,
      () -> sendEvent(MessageAddress.BATCH_VOUCHER_PERSIST_TOPIC
              , new JsonObject().put(BATCH_VOUCHER_EXPORT, JsonObject.mapFrom(batchVoucherExport))));
    return batchVoucherExport;
  }

  /**
   * Handles update of the batch voucher export
   *
   * @param batchVoucherExport updated {@link BatchVoucherExport} batchVoucherExport
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public CompletableFuture<Void> updateBatchVoucherExportRecord(BatchVoucherExport batchVoucherExport) {
    JsonObject jsonBatchVoucherExport = JsonObject.mapFrom(batchVoucherExport);
    String path = resourceByIdPath(BATCH_VOUCHER_EXPORTS_STORAGE, batchVoucherExport.getId(), lang);
    return handlePutRequest(path, jsonBatchVoucherExport, httpClient, ctx, okapiHeaders, logger);
  }

  /**
   * Delete Batch voucher export
   * @param id batch voucher export id to be deleted
   */
  public CompletableFuture<Void> deleteBatchVoucherExportById(String id) {
    return handleDeleteRequest(resourceByIdPath(BATCH_VOUCHER_EXPORTS_STORAGE, id, lang), httpClient, ctx, okapiHeaders, logger);
  }
}
