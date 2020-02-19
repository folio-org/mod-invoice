package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.model.BatchGroupCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_GROUPS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class BatchGroupHelper extends AbstractHelper {

  private static final String GET_BATCH_GROUPS_BY_QUERY = resourcesPath(BATCH_GROUPS) + SEARCH_PARAMS;

  private final ProtectionHelper protectionHelper;

  public BatchGroupHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    protectionHelper = new ProtectionHelper(httpClient, okapiHeaders, ctx, lang);
  }

  /**
   * Creates a batch group
   *
   * @param batchGroup {@link BatchGroup} to be created
   * @return completable future with {@link BatchGroup} on success or an exception if processing fails
   */
  public CompletableFuture<BatchGroup> createBatchGroup(BatchGroup batchGroup) {
    return createRecordInStorage(JsonObject.mapFrom(batchGroup), resourcesPath(BATCH_GROUPS))
      .thenApply(batchGroup::withId);
  }

  /**
   * Gets list of batch groups
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link BatchGroupCollection} on success or an exception if processing fails
   */
  public CompletableFuture<BatchGroupCollection> getBatchGroups(int limit, int offset, String query) {
    CompletableFuture<BatchGroupCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String queryParam = getEndpointWithQuery(query, logger);
      String endpoint = String.format(GET_BATCH_GROUPS_BY_QUERY, limit, offset, queryParam, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonVouchers -> {
          logger.info("Successfully retrieved batch groups: " + jsonVouchers.encodePrettily());
          future.complete(jsonVouchers.mapTo(BatchGroupCollection.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting batch groups", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  /**
   * Gets batch group by id
   *
   * @param id batch group uuid
   * @return completable future with {@link BatchGroup} on success or an exception if processing fails
   */
  public CompletableFuture<BatchGroup> getBatchGroup(String id) {
    CompletableFuture<BatchGroup> future = new VertxCompletableFuture<>(ctx);
    getBatchGroupById(id, lang, httpClient, ctx, okapiHeaders, logger)
      .thenAccept(future::complete)
      .exceptionally(t -> {
        logger.error("Failed to retrieve batch group", t.getCause());
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  private static CompletableFuture<BatchGroup> getBatchGroupById(String id, String lang, HttpClientInterface httpClient, Context ctx,
    Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(BATCH_GROUPS, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApplyAsync(json -> json.mapTo(BatchGroup.class));
  }

  /**
   * Handles update of the batch group.
   *
   * @param batchGroup updated {@link BatchGroup} invoice
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public CompletableFuture<Void> updateBatchGroupRecord(BatchGroup batchGroup) {
    JsonObject jsonBatchGroup = JsonObject.mapFrom(batchGroup);
    String path = resourceByIdPath(BATCH_GROUPS, batchGroup.getId(), lang);
    return handlePutRequest(path, jsonBatchGroup, httpClient, ctx, okapiHeaders, logger);
  }

  /**
   * Delete Batch group
   * 1. Get batch group by id
   * 2. Check if this batch group is not in use by any invoice
   * 3. If no then delete batch group
   * @param id batch group id to be deleted
   */
  public CompletableFuture<Void> deleteBatchGroup(String id) {
    return getBatchGroup(id)
      .thenCompose(batchGroup -> protectionHelper.isBatchGroupDeleteAllowed(batchGroup.getId()))
      .thenCompose(v -> handleDeleteRequest(resourceByIdPath(BATCH_GROUPS, id, lang), httpClient, ctx, okapiHeaders, logger));
  }
}
