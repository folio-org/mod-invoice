package org.folio.services;

import org.folio.invoices.utils.UploadHelper;
import org.folio.invoices.utils.UploadHelperFactory;
import org.folio.models.BatchVoucherUploadHolder;
import org.folio.rest.impl.BatchVoucherExportConfigHelper;
import org.folio.rest.impl.BatchVoucherExportsHelper;
import org.folio.rest.impl.BatchVoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import io.vertx.core.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class UploadBatchVoucherExportService {
  private final static Logger LOG = LoggerFactory.getLogger(UploadBatchVoucherExportService.class);
  private final BatchVoucherHelper bvHelper;
  private final BatchVoucherExportConfigHelper bvExportConfigHelper;
  private final BatchVoucherExportsHelper bvExportsHelper;
  private final BatchVoucherExportsHelper batchVoucherExportsHelper;

  public UploadBatchVoucherExportService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    bvHelper = new BatchVoucherHelper(okapiHeaders, ctx, lang);
    bvExportConfigHelper = new BatchVoucherExportConfigHelper(okapiHeaders, ctx, lang);
    bvExportsHelper = new BatchVoucherExportsHelper(okapiHeaders, ctx, lang);
    batchVoucherExportsHelper = new BatchVoucherExportsHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Void> uploadBatchVoucherExport(Context ctx, String acceptHeader, String batchVoucherExportId) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    BatchVoucherUploadHolder uploadHolder = new BatchVoucherUploadHolder();
    return bvExportsHelper.getBatchVoucherExportById(batchVoucherExportId)
                   .thenAccept(uploadHolder::setBatchVoucherExport)
                   .thenAccept(v -> uploadHolder.setFileFormat(acceptHeader.split("/")[1]))
                   .thenCompose(v -> getBatchVoucher(uploadHolder.getBatchVoucherExport().getBatchVoucherId(), acceptHeader))
                   .thenAccept(uploadHolder::setBatchVoucher)
                   .thenCompose(v -> bvExportConfigHelper
                        .getExportConfigs(1, 0, buildExportConfigQuery(uploadHolder.getBatchVoucherExport().getBatchGroupId())))
                   .thenAccept(exportConfigs -> uploadHolder.setExportConfig(exportConfigs.getExportConfigs().get(0)))
                   .thenCompose(v -> bvExportConfigHelper.getExportConfigCredentials(uploadHolder.getExportConfig().getId()))
                   .thenAccept(uploadHolder::setCredentials)
                   .thenAccept(v -> uploadBatchVoucher(uploadHolder))
                   .handle((v, t) -> {
                     if (Objects.nonNull(t)) {
                       updateBatchVoucherStatus(uploadHolder.getBatchVoucherExport(), BatchVoucherExport.Status.ERROR);
                       LOG.error("Exception occurs, when uploading batch voucher", t.getMessage());
                       future.complete(null);
                     }
                     else {
                       updateBatchVoucherStatus(uploadHolder.getBatchVoucherExport(), BatchVoucherExport.Status.UPLOADED);
                       LOG.debug("Batch voucher uploaded on FTP");
                       future.complete(null);
                     }
                     return null;
                   });
  }

  private void updateBatchVoucherStatus(BatchVoucherExport bvExport, BatchVoucherExport.Status status) {
    bvExport.setStatus(status);
    batchVoucherExportsHelper.updateBatchVoucherExportRecord(bvExport)
                             .handle((voidP, throwable) -> {
                               closeHttpClient();
                               return null;
                             });
  }

  private CompletableFuture<Void> uploadBatchVoucher(BatchVoucherUploadHolder uploadHolder) {
      try {
          UploadHelper helper = UploadHelperFactory.get(uploadHolder.getExportConfig().getUploadURI());
          return helper.login(uploadHolder.getCredentials().getUsername(), uploadHolder.getCredentials().getPassword())
            .thenAccept(LOG::info)
            .thenCompose(v -> {
              String fileName = generateFileName(uploadHolder.getBatchVoucher(), uploadHolder.getFileFormat());
              return helper.upload(fileName, uploadHolder.getBatchVoucher());
            })
            .thenAccept(LOG::info)
            .exceptionally(t -> {
              LOG.error(t);
              return null;
            })
            .whenComplete((i, t) -> helper.logout()
              .thenAccept(LOG::info));
        } catch (URISyntaxException e) {
          throw new CompletionException(e);
        }
  }

  private CompletableFuture<BatchVoucher> getBatchVoucher(String acceptHeader, String batchVoucherId) {
    return bvHelper.getBatchVoucherById(batchVoucherId, acceptHeader)
            .thenApply(response -> response.readEntity(BatchVoucher.class));
  }

  private String buildExportConfigQuery(String groupId) {
    return "batchGroupId==" + groupId;
  }

  private String generateFileName(BatchVoucher batchVoucher, String fileFormat) {
    return "/files/bv" + "_" + batchVoucher.getBatchGroup()
              + "_" + batchVoucher.getStart().toString()
                + "_to_" + batchVoucher.getEnd().toString()
                  + "." + fileFormat;
  }

  private void closeHttpClient(){
    bvHelper.closeHttpClient();
    bvExportConfigHelper.closeHttpClient();
    bvExportsHelper.closeHttpClient();
    batchVoucherExportsHelper.closeHttpClient();
  }
}
