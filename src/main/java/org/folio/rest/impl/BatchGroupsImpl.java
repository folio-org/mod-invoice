package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.resource.BatchGroups;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchGroupsImpl implements BatchGroups {

  private static final Logger logger = LoggerFactory.getLogger(BatchGroupsImpl.class);
  private static final String BATCH_GROUP_LOCATION_PREFIX = "/batch-groups/%s";

  @Validate
  @Override
  public void postBatchGroups(String lang, BatchGroup batchGroup, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext, lang);

    helper.createBatchGroup(batchGroup)
      .thenAccept(bg -> asyncResultHandler.handle(succeededFuture(
        helper.buildResponseWithLocation(String.format(BATCH_GROUP_LOCATION_PREFIX, bg.getId()), bg))))
      .exceptionally(t -> {
        logger.error("Failed to create batch group ", t);
        return handleErrorResponse(asyncResultHandler, helper, t);
      });
  }

  @Validate
  @Override
  public void getBatchGroups(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext, lang);

    helper.getBatchGroups(limit, offset, query)
      .thenAccept(batchGroups -> {
        logInfo("Successfully retrieved batch groups: {}", batchGroups);
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchGroups)));
      })
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchGroupsById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext, lang);

    helper.getBatchGroup(id)
      .thenAccept(batchGroup -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchGroup))))
      .exceptionally(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchGroupsById(String id, String lang, BatchGroup batchGroup, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext, lang);

    helper.updateBatchGroupRecord(batchGroup)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(ex -> {
        logger.error("Failed to update batch group with id={}", ex, batchGroup.getId());
        return handleErrorResponse(asyncResultHandler, helper, ex);
      });
  }

  @Validate
  @Override
  public void deleteBatchGroupsById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext, lang);

    helper.deleteBatchGroup(id)
      .thenAccept(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .exceptionally(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
  }

  private void logInfo(String message, Object entry) {
    if (logger.isInfoEnabled()) {
      logger.info(message, JsonObject.mapFrom(entry).encodePrettily());
    }
  }

  private Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, AbstractHelper helper, Throwable t) {
    asyncResultHandler.handle(succeededFuture(helper.buildErrorResponse(t)));
    return null;
  }
}
