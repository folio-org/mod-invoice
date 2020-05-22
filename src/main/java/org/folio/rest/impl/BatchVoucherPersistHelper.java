package org.folio.rest.impl;

import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.voucher.BatchVoucherGenerateService;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;

public class BatchVoucherPersistHelper extends AbstractHelper {
  private final BatchVoucherGenerateService batchVoucherGenerateService;
  private final BatchVoucherExportsHelper batchVoucherExportsHelper;

  public BatchVoucherPersistHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
    this.batchVoucherGenerateService = new BatchVoucherGenerateService(okapiHeaders, ctx, lang);
    this.batchVoucherExportsHelper = new BatchVoucherExportsHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<String> persistBatchVoucher(BatchVoucherExport batchVoucherExport) {
    CompletableFuture<String> future = new CompletableFuture<>();
    batchVoucherGenerateService.generateBatchVoucher(batchVoucherExport)
      .thenApply(JsonObject::mapFrom)
      .thenCompose(jsonInvoice -> createRecordInStorage(jsonInvoice, resourcesPath(BATCH_VOUCHER_STORAGE)))
      .thenApply(batchVoucherId -> {
        batchVoucherExport.setStatus(BatchVoucherExport.Status.GENERATED);
        batchVoucherExport.setBatchVoucherId(batchVoucherId);
        return batchVoucherId;
      })
      .thenAccept(batchVoucherId -> {
        batchVoucherExportsHelper.updateBatchVoucherExportRecord(batchVoucherExport)
          .thenAccept(v-> closeHttpClient());
        logger.debug("Batch voucher generated and batch voucher export updated");
        future.complete(batchVoucherId);
      })
      .exceptionally(t -> {
        batchVoucherExport.setMessage(t.getCause().getMessage());
        batchVoucherExport.setStatus(BatchVoucherExport.Status.ERROR);
        batchVoucherExportsHelper.updateBatchVoucherExportRecord(batchVoucherExport)
          .thenAccept(v-> closeHttpClient());
        logger.error("Exception occurs, when generating batch voucher", t.getMessage());
        future.complete(null);
        return null;
      });
    return future;
  }

  @Override
  public void closeHttpClient(){
    httpClient.closeClient();
    batchVoucherExportsHelper.closeHttpClient();
  }
}
