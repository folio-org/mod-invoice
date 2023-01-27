package org.folio.services;

import static org.folio.invoices.utils.HelperUtils.SEARCH_PARAMS;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_GROUPS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.model.BatchGroupCollection;

import io.vertx.core.Future;

public class BatchGroupService {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  private static final String GET_BATCH_GROUPS_BY_QUERY = resourcesPath(BATCH_GROUPS) + SEARCH_PARAMS;
  private final RestClient restClient;
  public BatchGroupService(RestClient restClient) {
    this.restClient = restClient;

  }

  /**
   * Creates a batch group
   *
   * @param batchGroup     {@link BatchGroup} to be created
   * @param requestContext
   * @return completable future with {@link BatchGroup} on success or an exception if processing fails
   */
  public Future<BatchGroup> createBatchGroup(BatchGroup batchGroup, RequestContext requestContext) {
    return restClient.post(resourcesPath(BATCH_GROUPS), batchGroup, BatchGroup.class, requestContext);
  }

  /**
   * Gets list of batch groups
   *
   * @param limit          Limit the number of elements returned in the response
   * @param offset         Skip over a number of elements by specifying an offset value for the query
   * @param query          A query expressed as a CQL string using valid searchable fields
   * @param requestContext
   * @return completable future with {@link BatchGroupCollection} on success or an exception if processing fails
   */
  public Future<BatchGroupCollection> getBatchGroups(int limit, int offset, String query, RequestContext requestContext) {
    String queryParam = getEndpointWithQuery(query);
    String endpoint = String.format(GET_BATCH_GROUPS_BY_QUERY, limit, offset, queryParam);
    return restClient.get(endpoint, BatchGroupCollection.class, requestContext)
      .onFailure(t -> logger.error("Error getting batch groups", t));
  }

  /**
   * Gets batch group by id
   *
   * @param id             batch group uuid
   * @param requestContext
   * @return completable future with {@link BatchGroup} on success or an exception if processing fails
   */
  public Future<BatchGroup> getBatchGroup(String id, RequestContext requestContext) {
    String endpoint = resourceByIdPath(BATCH_GROUPS, id);
    return restClient.get(endpoint, BatchGroup.class, requestContext);
  }

  /**
   * Handles update of the batch group.
   *
   * @param batchGroup     updated {@link BatchGroup} invoice
   * @param requestContext
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public Future<Void> updateBatchGroupRecord(BatchGroup batchGroup, RequestContext requestContext) {
    String path = resourceByIdPath(BATCH_GROUPS, batchGroup.getId());
    return restClient.put(path, batchGroup, requestContext);
  }

  /**
   * Delete Batch group
   *
   * @param id             batch group id to be deleted
   * @param requestContext
   */
  public Future<Void> deleteBatchGroup(String id, RequestContext requestContext) {
    return restClient.delete(resourceByIdPath(BATCH_GROUPS, id), requestContext);
  }
}
