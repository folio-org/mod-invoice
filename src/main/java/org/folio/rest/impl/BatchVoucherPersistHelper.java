package org.folio.rest.impl;

import java.util.Map;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.voucher.BatchVoucherExportsService;
import org.folio.services.voucher.BatchVoucherGenerateService;
import org.folio.services.voucher.BatchVoucherService;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class BatchVoucherPersistHelper extends AbstractHelper {

  @Autowired
  private BatchVoucherGenerateService batchVoucherGenerateService;
  @Autowired
  private BatchVoucherExportsService batchVoucherExportsService;
  @Autowired
  private BatchVoucherService batchVoucherService;
  private final RequestContext requestContext;

  public BatchVoucherPersistHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.requestContext = new RequestContext(ctx, okapiHeaders);
  }

  public Future<String> persistBatchVoucher(BatchVoucherExport batchVoucherExport) {
    return batchVoucherGenerateService.buildBatchVoucherObject(batchVoucherExport, new RequestContext(ctx, okapiHeaders))
      .compose(batchVoucher -> batchVoucherService.createBatchVoucher(batchVoucher, requestContext))
      .compose(batchVoucher -> {
        batchVoucherExport.setMessage("Batch voucher was generated");
        batchVoucherExport.setStatus(BatchVoucherExport.Status.GENERATED);
        batchVoucherExport.setBatchVoucherId(batchVoucher.getId());
        return batchVoucherExportsService.updateBatchVoucherExportRecord(batchVoucherExport, requestContext)
          .map(batchVoucher.getId());
      })
      .onSuccess(v -> logger.debug("Batch voucher generated and batch voucher export updated"))
      .onFailure(t -> {
        batchVoucherExport.setMessage(t.getCause().getMessage());
        batchVoucherExport.setStatus(BatchVoucherExport.Status.ERROR);
        batchVoucherExportsService.updateBatchVoucherExportRecord(batchVoucherExport, requestContext);
        logger.error("Exception occurs, when generating batch voucher", t);
      });
  }
}
