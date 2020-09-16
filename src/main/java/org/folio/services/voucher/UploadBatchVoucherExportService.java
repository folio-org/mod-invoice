package org.folio.services.voucher;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.BatchVoucherUploadHolder;
import org.folio.rest.impl.BatchVoucherExportConfigHelper;
import org.folio.rest.impl.BatchVoucherExportsHelper;
import org.folio.rest.impl.BatchVoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.services.ftp.FtpUploadService;
import org.folio.services.ftp.UploadService;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class UploadBatchVoucherExportService {
  private static final Logger LOG = LoggerFactory.getLogger(UploadBatchVoucherExportService.class);
  public static final String DATE_TIME_DELIMITER = "T";
  public static final String DELIMITER = "_";
  private final Context ctx;
  private final BatchVoucherHelper bvHelper;
  private final BatchVoucherExportConfigHelper bvExportConfigHelper;
  private final BatchVoucherExportsHelper bvExportsHelper;

  public UploadBatchVoucherExportService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this.bvHelper = new BatchVoucherHelper(okapiHeaders, ctx, lang);
    this.bvExportConfigHelper = new BatchVoucherExportConfigHelper(okapiHeaders, ctx, lang);
    this.bvExportsHelper = new BatchVoucherExportsHelper(okapiHeaders, ctx, lang);
    this.ctx = ctx;
  }

  public UploadBatchVoucherExportService(Context ctx, BatchVoucherHelper bvHelper, BatchVoucherExportConfigHelper bvExportConfigHelper
    , BatchVoucherExportsHelper bvExportsHelper) {
    this.bvHelper = bvHelper;
    this.bvExportConfigHelper = bvExportConfigHelper;
    this.bvExportsHelper = bvExportsHelper;
    this.ctx = ctx;
  }

  public CompletableFuture<Void> uploadBatchVoucherExport(String batchVoucherExportId) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    BatchVoucherUploadHolder uploadHolder = new BatchVoucherUploadHolder();
    bvExportsHelper.getBatchVoucherExportById(batchVoucherExportId)
                   .thenAccept(uploadHolder::setBatchVoucherExport)
                   .thenCompose(v -> updateHolderWithExportConfig(uploadHolder))
                   .thenAccept(v ->  updateHolderWithFileFormat(uploadHolder))
                   .thenCompose(v -> updateHolderWithCredentials(uploadHolder))
                   .thenCompose(v -> updateHolderWithBatchVoucher(uploadHolder))
                   .thenCompose(v -> uploadBatchVoucher(uploadHolder))
                   .handle((v, throwable) -> {
                     if (throwable == null) {
                       succUploadUpdate(uploadHolder);
                     }
                     else {
                       failUploadUpdate(uploadHolder.getBatchVoucherExport(), throwable);
                     }
                     future.complete(null);
                     return null;
                   });
    return future;
  }

  public CompletableFuture<Void> uploadBatchVoucherExport(BatchVoucherExport batchVoucherExport) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    BatchVoucherUploadHolder uploadHolder = new BatchVoucherUploadHolder();
    uploadHolder.setBatchVoucherExport(batchVoucherExport);
    updateHolderWithExportConfig(uploadHolder)
      .thenAccept(v ->  updateHolderWithFileFormat(uploadHolder))
      .thenCompose(v -> updateHolderWithCredentials(uploadHolder))
      .thenCompose(v -> updateHolderWithBatchVoucher(uploadHolder))
      .thenCompose(v -> uploadBatchVoucher(uploadHolder))
      .handle((v, throwable) -> {
        if (throwable == null) {
          succUploadUpdate(uploadHolder);
        }
        else {
          failUploadUpdate(uploadHolder.getBatchVoucherExport(), throwable);
        }
        future.complete(null);
        return null;
      });
    return future;
  }

  public CompletableFuture<Void> uploadBatchVoucher(BatchVoucherUploadHolder uploadHolder) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    try {
      UploadService helper = new FtpUploadService(ctx, uploadHolder.getExportConfig().getUploadURI());
      helper.login(uploadHolder.getCredentials().getUsername(), uploadHolder.getCredentials().getPassword())
        .thenAccept(LOG::info)
        .thenCompose(v -> {
          String fileName = generateFileName(uploadHolder.getBatchVoucher(), uploadHolder.getFileFormat());

          BatchVoucher batchVoucher = uploadHolder.getBatchVoucher();
          String format = uploadHolder.getExportConfig().getFormat().value();
          String content = bvHelper.convertBatchVoucher(batchVoucher, format);

          return helper.upload(ctx, fileName, content);
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

  public String generateFileName(BatchVoucher batchVoucher, String fileFormat) {
    JsonObject voucherJSON = JsonObject.mapFrom(batchVoucher);
    String bvShortUUID = batchVoucher.getId().substring(batchVoucher.getId().lastIndexOf('-') + 1);
    String voucherGroup = voucherJSON.getString("batchGroup");
    String voucherStart = voucherJSON.getString("start").split(DATE_TIME_DELIMITER)[0];
    String voucherEnd = voucherJSON.getString("end").split(DATE_TIME_DELIMITER)[0];
    return "bv" + DELIMITER + bvShortUUID + DELIMITER + voucherGroup + DELIMITER + voucherStart + DELIMITER + voucherEnd + "." + fileFormat;
  }

  private CompletableFuture<Void> updateHolderWithExportConfig(BatchVoucherUploadHolder uploadHolder) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    bvExportConfigHelper
              .getExportConfigs(1, 0, buildExportConfigQuery(uploadHolder.getBatchVoucherExport().getBatchGroupId()))
              .thenAccept(exportConfigs -> uploadHolder.setExportConfig(exportConfigs.getExportConfigs().get(0)))
              .thenAccept(v -> future.complete(null))
              .exceptionally(t -> {
                future.completeExceptionally(new HttpException(404, "Batch export configuration was not found"));
                return null;
              });
    return future;
  }

  private void updateHolderWithFileFormat(BatchVoucherUploadHolder uploadHolder) {
    String fileFormat = uploadHolder.getExportConfig().getFormat().value().split("/")[1];
    uploadHolder.setFileFormat(fileFormat);
  }

  private CompletableFuture<Void> updateHolderWithCredentials(BatchVoucherUploadHolder uploadHolder) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    bvExportConfigHelper.getExportConfigCredentials(uploadHolder.getExportConfig().getId())
      .thenAccept(uploadHolder::setCredentials)
      .thenAccept(v -> future.complete(null))
      .exceptionally(t -> {
        future.completeExceptionally(new HttpException(404, "Credentials for export configuration was not found"));
        return null;
      });
    return future;
  }

  private CompletableFuture<Void> updateHolderWithBatchVoucher(BatchVoucherUploadHolder uploadHolder) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    try {
      bvHelper.getBatchVoucherById(uploadHolder.getBatchVoucherExport().getBatchVoucherId())
        .thenAccept(uploadHolder::setBatchVoucher)
        .thenAccept(v -> future.complete(null))
        .exceptionally(t -> {
          future.completeExceptionally(new HttpException(404, "Batch voucher was not found"));
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(new HttpException(500, "File format error"));
    }
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

  private void succUploadUpdate(BatchVoucherUploadHolder uploadHolder) {
    BatchVoucherExport bvExport = uploadHolder.getBatchVoucherExport();
    if (bvExport != null) {
      bvExport.setStatus(BatchVoucherExport.Status.UPLOADED);
      bvExport.setMessage(generateFileName(uploadHolder.getBatchVoucher(), uploadHolder.getFileFormat()));
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

  private String buildExportConfigQuery(String groupId) {
    return "batchGroupId==" + groupId;
  }

  private void closeHttpClient(){
    bvHelper.closeHttpClient();
    bvExportConfigHelper.closeHttpClient();
    bvExportsHelper.closeHttpClient();
  }
}
