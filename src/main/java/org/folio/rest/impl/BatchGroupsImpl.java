package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.resource.BatchGroups;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class BatchGroupsImpl implements BatchGroups {

  private static final Logger logger = LogManager.getLogger(BatchGroupsImpl.class);
  private static final String BATCH_GROUP_LOCATION_PREFIX = "/batch-groups/%s";

  @Validate
  @Override
  public void postBatchGroups(BatchGroup batchGroup, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext);

    helper.createBatchGroup(batchGroup)
      .onSuccess(bg -> asyncResultHandler.handle(succeededFuture(
        helper.buildResponseWithLocation(String.format(BATCH_GROUP_LOCATION_PREFIX, bg.getId()), bg))))
      .onFailure(t -> {
        logger.error("Failed to create batch group ", t);
        handleErrorResponse(asyncResultHandler, helper, t);
      });
  }

  @Validate
  @Override
  public void getBatchGroups(String totalRecords, int offset, int limit, String query, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext);

    helper.getBatchGroups(limit, offset, query)
      .onSuccess(batchGroups -> {
        logInfo("Successfully retrieved batch groups: {}", batchGroups);
        asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchGroups)));
      })
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void getBatchGroupsById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext);

    helper.getBatchGroup(id)
      .onSuccess(batchGroup -> asyncResultHandler.handle(succeededFuture(helper.buildOkResponse(batchGroup))))
      .onFailure(t -> handleErrorResponse(asyncResultHandler, helper, t));
  }

  @Validate
  @Override
  public void putBatchGroupsById(String id, BatchGroup batchGroup, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext);

    helper.updateBatchGroupRecord(batchGroup)
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(ex -> {
        logger.error("Failed to update batch group with id={}", batchGroup.getId(), ex);
        handleErrorResponse(asyncResultHandler, helper, ex);
      });
  }

  @Validate
  @Override
  public void deleteBatchGroupsById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    BatchGroupHelper helper = new BatchGroupHelper(okapiHeaders, vertxContext);

    helper.deleteBatchGroup(id)
      .onSuccess(ok -> asyncResultHandler.handle(succeededFuture(helper.buildNoContentResponse())))
      .onFailure(fail -> handleErrorResponse(asyncResultHandler, helper, fail));
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
