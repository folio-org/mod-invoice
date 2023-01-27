package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.BATCH_VOUCHER_EXPORT;

import java.util.Map;

import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchVoucherExportCollection;
import org.folio.services.voucher.BatchVoucherExportsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class BatchVoucherExportsHelper extends AbstractHelper {
  @Autowired
  private BatchVoucherExportsService batchVoucherExportsService;

  public BatchVoucherExportsHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  /**
   * Gets list of batch voucher exports
   *
   * @param limit  Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query  A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link BatchVoucherExportCollection} on success or an exception if processing fails
   */
  public Future<BatchVoucherExportCollection> getBatchVoucherExports(int limit, int offset, String query) {
    return batchVoucherExportsService.getBatchVoucherExports(limit, offset, query, new RequestContext(ctx, okapiHeaders));
  }

  /**
   * Gets batch voucher export by id
   *
   * @param id batch voucher export uuid
   * @return completable future with {@link BatchVoucherExport} on success or an exception if processing fails
   */
  public Future<BatchVoucherExport> getBatchVoucherExportById(String id) {
    return batchVoucherExportsService.getBatchVoucherExportById(id, new RequestContext(ctx, okapiHeaders));
  }

  /**
   * Creates a batch voucher export
   *
   * @param batchVoucherExport {@link BatchVoucherExport} to be created
   * @return completable future with {@link BatchVoucherExport} on success or an exception if processing fails
   */
  public Future<BatchVoucherExport> createBatchVoucherExports(BatchVoucherExport batchVoucherExport) {
    return batchVoucherExportsService.createBatchVoucherExports(batchVoucherExport, new RequestContext(ctx,okapiHeaders))
      .onSuccess(this::persistBatchVoucher);
  }

  private void persistBatchVoucher(BatchVoucherExport batchVoucherExport) {
    buildRequestContext().getContext()
      .runOnContext(v -> sendEvent(MessageAddress.BATCH_VOUCHER_PERSIST_TOPIC,
        new JsonObject().put(BATCH_VOUCHER_EXPORT, JsonObject.mapFrom(batchVoucherExport))));
  }

  /**
   * Handles update of the batch voucher export
   *
   * @param batchVoucherExport updated {@link BatchVoucherExport} batchVoucherExport
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public Future<Void> updateBatchVoucherExportRecord(BatchVoucherExport batchVoucherExport) {
    return batchVoucherExportsService.updateBatchVoucherExportRecord(batchVoucherExport, new RequestContext(ctx, okapiHeaders));
  }

  /**
   * Delete Batch voucher export
   * @param id batch voucher export id to be deleted
   */
  public Future<Void> deleteBatchVoucherExportById(String id) {
    return batchVoucherExportsService.deleteBatchVoucherExportById(id, buildRequestContext());
  }
}
