package org.folio.rest.impl;

import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.BatchVoucherGenerateService;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchVoucherPersisteHelper extends AbstractHelper {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final BatchVoucherGenerateService batchVoucherGenerateService;
  private final BatchVoucherExportsHelper batchVoucherExportsHelper;

  public BatchVoucherPersisteHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
    this.batchVoucherGenerateService = new BatchVoucherGenerateService(okapiHeaders, ctx, lang);
    this.batchVoucherExportsHelper = new BatchVoucherExportsHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<String> persistBatchVoucher(BatchVoucherExport batchVoucherExport) {
    return batchVoucherGenerateService.generateBatchVoucher(batchVoucherExport)
      .thenApply(JsonObject::mapFrom)
      .thenCompose(jsonInvoice -> createRecordInStorage(jsonInvoice, resourcesPath(BATCH_VOUCHER_STORAGE)))
      .thenApply(batchVoucherId -> {
        batchVoucherExport.setStatus(BatchVoucherExport.Status.GENERATED);
        batchVoucherExport.setBatchVoucherId(batchVoucherId);
        return batchVoucherId;
      })
      .thenApply(batchVoucherId -> {
        batchVoucherExportsHelper.updateBatchVoucherExportRecord(batchVoucherExport)
          .thenAccept(v-> closeHttpClient());
        logger.debug("Batch voucher generated and batch voucher export updated");
        return batchVoucherId;
      })
      .exceptionally(t -> {
        batchVoucherExport.setStatus(BatchVoucherExport.Status.ERROR);
        batchVoucherExportsHelper.updateBatchVoucherExportRecord(batchVoucherExport)
          .thenAccept(v-> closeHttpClient());
        logger.error("Exception occurs, when generating batch voucher", t);
        return null;
      });
  }

  @Override
  public void closeHttpClient(){
    httpClient.closeClient();
    batchVoucherExportsHelper.closeHttpClient();
  }
}
