package org.folio.services.voucher;

import static org.folio.invoices.utils.HelperUtils.SEARCH_PARAMS;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORTS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchVoucherExportCollection;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class BatchVoucherExportsService {
  private final Logger logger = LogManager.getLogger(this.getClass());
  private static final String GET_BATCH_VOUCHER_EXPORTS_BY_QUERY = resourcesPath(BATCH_VOUCHER_EXPORTS_STORAGE) + SEARCH_PARAMS;

  private final RestClient restClient;
  public BatchVoucherExportsService(RestClient restClient) {
    this.restClient = restClient;
  }
  /**
   * Gets list of batch voucher exports
   *
   * @param limit  Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query  A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link BatchVoucherExportCollection} on success or an exception if processing fails
   */
  public Future<BatchVoucherExportCollection> getBatchVoucherExports(int limit, int offset, String query, RequestContext requestContext) {
    String queryParam = getEndpointWithQuery(query);
    String endpoint = String.format(GET_BATCH_VOUCHER_EXPORTS_BY_QUERY, limit, offset, queryParam);
    return restClient.get(endpoint, BatchVoucherExportCollection.class, requestContext);
  }

  /**
   * Gets batch voucher export by id
   *
   * @param id batch voucher export uuid
   * @return completable future with {@link BatchVoucherExport} on success or an exception if processing fails
   */
  public Future<BatchVoucherExport> getBatchVoucherExportById(String id, RequestContext requestContext) {
    String endpoint = resourceByIdPath(BATCH_VOUCHER_EXPORTS_STORAGE, id);
    return restClient.get(endpoint, BatchVoucherExport.class, requestContext)
      .onFailure(t -> logger.error("Failed to retrieve batch voucher export ", t.getCause()));
  }

  /**
   * Creates a batch voucher export
   *
   * @param batchVoucherExport {@link BatchVoucherExport} to be created
   * @return completable future with {@link BatchVoucherExport} on success or an exception if processing fails
   */
  public Future<BatchVoucherExport> createBatchVoucherExports(BatchVoucherExport batchVoucherExport, RequestContext requestContext) {
    return restClient.post(resourcesPath(BATCH_VOUCHER_EXPORTS_STORAGE), batchVoucherExport, BatchVoucherExport.class, requestContext);
  }

  /**
   * Handles update of the batch voucher export
   *
   * @param batchVoucherExport updated {@link BatchVoucherExport} batchVoucherExport
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public Future<Void> updateBatchVoucherExportRecord(BatchVoucherExport batchVoucherExport, RequestContext requestContext) {
    JsonObject jsonBatchVoucherExport = JsonObject.mapFrom(batchVoucherExport);
    String path = resourceByIdPath(BATCH_VOUCHER_EXPORTS_STORAGE, batchVoucherExport.getId());
    return restClient.put(path, jsonBatchVoucherExport, requestContext);
  }

  /**
   * Delete Batch voucher export
   * @param id batch voucher export id to be deleted
   */
  public Future<Void> deleteBatchVoucherExportById(String id, RequestContext requestContext) {
    return restClient.delete(resourceByIdPath(BATCH_VOUCHER_EXPORTS_STORAGE, id), requestContext);
  }
}
