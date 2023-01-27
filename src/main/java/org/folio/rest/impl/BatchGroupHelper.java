package org.folio.rest.impl;

import java.util.Map;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.model.BatchGroupCollection;
import org.folio.services.BatchGroupService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class BatchGroupHelper extends AbstractHelper {
  @Autowired
  private BatchGroupService batchGroupService;
  private final RequestContext requestContext;
  public BatchGroupHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.requestContext = new RequestContext(ctx, okapiHeaders);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  /**
   * Creates a batch group
   *
   * @param batchGroup {@link BatchGroup} to be created
   * @return completable future with {@link BatchGroup} on success or an exception if processing fails
   */
  public Future<BatchGroup> createBatchGroup(BatchGroup batchGroup) {
    return batchGroupService.createBatchGroup(batchGroup, requestContext);
  }

  /**
   * Gets list of batch groups
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link BatchGroupCollection} on success or an exception if processing fails
   */
  public Future<BatchGroupCollection> getBatchGroups(int limit, int offset, String query) {
    return batchGroupService.getBatchGroups(limit,offset,query, requestContext);
  }

  /**
   * Gets batch group by id
   *
   * @param id batch group uuid
   * @return completable future with {@link BatchGroup} on success or an exception if processing fails
   */
  public Future<BatchGroup> getBatchGroup(String id) {
    return batchGroupService.getBatchGroup(id, requestContext);
  }

  /**
   * Handles update of the batch group.
   *
   * @param batchGroup updated {@link BatchGroup} invoice
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public Future<Void> updateBatchGroupRecord(BatchGroup batchGroup) {
    return batchGroupService.updateBatchGroupRecord(batchGroup, requestContext);
  }

  /**
   * Delete Batch group
   *
   * @param id batch group id to be deleted
   */
  public Future<Void> deleteBatchGroup(String id) {
    return batchGroupService.deleteBatchGroup(id, requestContext);
  }
}
