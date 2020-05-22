package org.folio.services.voucher;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.services.ftp.UploadService;
import org.folio.services.ftp.UploadServiceFactory;
import org.folio.models.BatchVoucherUploadHolder;
import org.folio.rest.impl.BatchVoucherExportConfigHelper;
import org.folio.rest.impl.BatchVoucherExportsHelper;
import org.folio.rest.impl.BatchVoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.ExportConfig;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class UploadBatchVoucherExportService {
  private final static Logger LOG = LoggerFactory.getLogger(UploadBatchVoucherExportService.class);
  public static final String DATE_TIME_DELIMITER = "T";
  public static final String DELIMITER = "_";
  private final BatchVoucherHelper bvHelper;
  private final BatchVoucherExportConfigHelper bvExportConfigHelper;
  private final BatchVoucherExportsHelper bvExportsHelper;

  public UploadBatchVoucherExportService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    bvHelper = new BatchVoucherHelper(okapiHeaders, ctx, lang);
    bvExportConfigHelper = new BatchVoucherExportConfigHelper(okapiHeaders, ctx, lang);
    bvExportsHelper = new BatchVoucherExportsHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Void> uploadBatchVoucherExport(String batchVoucherExportId, Context ctx) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    BatchVoucherUploadHolder uploadHolder = new BatchVoucherUploadHolder();
    bvExportsHelper.getBatchVoucherExportById(batchVoucherExportId)
                   .thenAccept(uploadHolder::setBatchVoucherExport)
                   .thenCompose(v -> bvExportConfigHelper
                        .getExportConfigs(1, 0, buildExportConfigQuery(uploadHolder.getBatchVoucherExport().getBatchGroupId())))
                   .thenAccept(exportConfigs -> uploadHolder.setExportConfig(exportConfigs.getExportConfigs().get(0)))
                   .thenAccept(v -> {
                     String fileFormat = getFileFormat(uploadHolder.getExportConfig());
                     uploadHolder.setFileFormat(fileFormat);
                   })
                   .thenCompose(v -> bvExportConfigHelper.getExportConfigCredentials(uploadHolder.getExportConfig().getId()))
                   .thenAccept(uploadHolder::setCredentials)
                   .thenCompose(v -> getBatchVoucher(uploadHolder.getBatchVoucherExport().getBatchVoucherId(), uploadHolder.getExportConfig().getFormat()))
                   .thenAccept(uploadHolder::setBatchVoucher)
                   .thenCompose(v -> uploadBatchVoucher(ctx, uploadHolder))
                   .handle((v, throwable) -> {
                     if (throwable == null) {
                       succUploadUpdate(uploadHolder.getBatchVoucherExport());
                     }
                     else {
                       failUploadUpdate(uploadHolder.getBatchVoucherExport(), throwable);
                     }
                     future.complete(null);
                     return null;
                   });
    return future;
  }

  private void failUploadUpdate(BatchVoucherExport bvExport, Throwable t) {
    if (bvExport != null) {
      bvExport.setStatus(BatchVoucherExport.Status.ERROR);
      bvExport.setMessage(t.getCause().getMessage());
      updateBatchVoucher(bvExport);
    }
    LOG.error("Exception occurs, when uploading batch voucher", t.getMessage());
  }

  private void succUploadUpdate(BatchVoucherExport bvExport) {
    if (bvExport != null) {
      bvExport.setStatus(BatchVoucherExport.Status.UPLOADED);
      updateBatchVoucher(bvExport);
      LOG.debug("Batch voucher uploaded on FTP");
    }
  }

  private CompletableFuture<Void> updateBatchVoucher(BatchVoucherExport bvExport) {
    return bvExportsHelper.updateBatchVoucherExportRecord(bvExport)
                             .handle((voidP, throwable) -> {
                               closeHttpClient();
                               return null;
                             });
  }

  private CompletableFuture<Void> uploadBatchVoucher(Context ctx, BatchVoucherUploadHolder uploadHolder) {
    CompletableFuture<Void> future = new CompletableFuture<>();
      try {
          UploadService helper = UploadServiceFactory.get(uploadHolder.getExportConfig().getUploadURI());
          helper.login(uploadHolder.getCredentials().getUsername(), uploadHolder.getCredentials().getPassword())
            .thenAccept(LOG::info)
            .thenCompose(v -> {
              String fileName = generateFileName(uploadHolder.getBatchVoucher(), uploadHolder.getFileFormat());
              return helper.upload(ctx, fileName, uploadHolder.getBatchVoucher());
            })
            .thenAccept(replyString -> future.complete(null))
            .exceptionally(t -> {
              LOG.error(t);
              future.completeExceptionally(t);
              return null;
            });
        } catch (URISyntaxException e) {
          future.completeExceptionally(new CompletionException(e));
        }
      return future;
  }

  private CompletableFuture<BatchVoucher> getBatchVoucher(String batchVoucherId, ExportConfig.Format exportFormat) {
    String acceptHeader = exportFormat.value().toLowerCase();
    return bvHelper.getBatchVoucherById(batchVoucherId, acceptHeader)
            .thenApply(response -> (BatchVoucher)response.getEntity());
  }

  private String buildExportConfigQuery(String groupId) {
    return "batchGroupId==" + groupId;
  }

  private String generateFileName(BatchVoucher batchVoucher, String fileFormat) {
    JsonObject voucherJSON = JsonObject.mapFrom(batchVoucher);
    String voucherGroup = voucherJSON.getString("batchGroup");
    String voucherStart = voucherJSON.getString("start").split(DATE_TIME_DELIMITER)[0];
    String voucherEnd = voucherJSON.getString("end").split(DATE_TIME_DELIMITER)[0];
    return "bv" + DELIMITER + voucherGroup + DELIMITER + voucherStart + DELIMITER + voucherEnd + "." + fileFormat;
  }

  private String getFileFormat(ExportConfig exportConfig) {
    return exportConfig.getFormat().value().split("/")[1];
  }

  private void closeHttpClient(){
    bvHelper.closeHttpClient();
    bvExportConfigHelper.closeHttpClient();
    bvExportsHelper.closeHttpClient();
  }
}
