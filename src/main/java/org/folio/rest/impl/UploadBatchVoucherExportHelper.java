package org.folio.rest.impl;

import static org.folio.invoices.utils.ErrorCodes.BATCH_VOUCHER_NOT_FOUND;
import static org.folio.services.ftp.FtpUploadService.URL_NOT_FOUND_FOR_FTP;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.BatchVoucherUploadHolder;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.ftp.FtpUploadService;
import org.folio.services.ftp.SftpUploadService;
import org.folio.services.voucher.BatchVoucherExportConfigService;
import org.folio.services.voucher.BatchVoucherExportsService;
import org.folio.services.voucher.BatchVoucherService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class UploadBatchVoucherExportHelper extends AbstractHelper {
  private static final Logger log = LogManager.getLogger(UploadBatchVoucherExportHelper.class);
  public static final String DATE_TIME_DELIMITER = "T";
  public static final String DELIMITER = "_";

  @Autowired
  private BatchVoucherService batchVoucherService;
  @Autowired
  private BatchVoucherExportConfigService batchVoucherExportConfigService;
  @Autowired
  private BatchVoucherExportsService batchVoucherExportsService;
  private static final String CREDENTIALS_NOT_FOUND = "Credentials for FTP upload were not found";

  private final RequestContext requestContext;

  public UploadBatchVoucherExportHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    SpringContextUtil.autowireDependencies(this, ctx);
    this.requestContext = new RequestContext(ctx, okapiHeaders);
  }

  public Future<Void> uploadBatchVoucherExport(String batchVoucherExportId) {
    BatchVoucherUploadHolder uploadHolder = new BatchVoucherUploadHolder();
    return batchVoucherExportsService.getBatchVoucherExportById(batchVoucherExportId, requestContext)
      .map(bve -> {
        uploadHolder.setBatchVoucherExport(bve);
        return null;
      })
      .compose(ok -> updateHolderWithExportConfig(uploadHolder)
        .onSuccess(v -> updateHolderWithFileFormat(uploadHolder))
        .compose(v -> updateHolderWithCredentials(uploadHolder))
        .compose(v -> updateHolderWithBatchVoucher(uploadHolder))
        .compose(v -> uploadBatchVoucher(uploadHolder))
        .onSuccess(v -> successfulUploadUpdate(uploadHolder))
        .onFailure(t -> failUploadUpdate(uploadHolder.getBatchVoucherExport(), t)));
  }

  public Future<Void> uploadBatchVoucherExport(BatchVoucherExport batchVoucherExport) {
    BatchVoucherUploadHolder uploadHolder = new BatchVoucherUploadHolder();
    uploadHolder.setBatchVoucherExport(batchVoucherExport);
    return updateHolderWithExportConfig(uploadHolder)
      .onSuccess(v -> updateHolderWithFileFormat(uploadHolder))
      .compose(v -> updateHolderWithCredentials(uploadHolder))
      .compose(v -> updateHolderWithBatchVoucher(uploadHolder))
      .compose(v -> uploadBatchVoucher(uploadHolder))
      .onSuccess(v -> successfulUploadUpdate(uploadHolder))
      .onFailure(t -> failUploadUpdate(uploadHolder.getBatchVoucherExport(), t));
  }

  public Future<Void> uploadBatchVoucher(BatchVoucherUploadHolder uploadHolder) {
    try {
      String fileName = generateFileName(uploadHolder.getBatchVoucher(), uploadHolder.getFileFormat());
      BatchVoucher batchVoucher = uploadHolder.getBatchVoucher();
      String format = uploadHolder.getExportConfig().getFormat().value();
      String content = batchVoucherService.convertBatchVoucher(batchVoucher, format);
      Integer port = uploadHolder.getExportConfig().getFtpPort();
      if (ExportConfig.FtpFormat.FTP == uploadHolder.getExportConfig().getFtpFormat()) {
        return new FtpUploadService(ctx, uploadHolder.getExportConfig().getUploadURI(), port)
          .upload(ctx, uploadHolder.getCredentials().getUsername(), uploadHolder.getCredentials().getPassword(), uploadHolder.getExportConfig().getUploadDirectory(), fileName, content).mapEmpty();
      } else {
        return new SftpUploadService(uploadHolder.getExportConfig().getUploadURI(), port)
          .upload(ctx, uploadHolder.getCredentials().getUsername(), uploadHolder.getCredentials().getPassword(), uploadHolder.getExportConfig().getUploadDirectory(), fileName, content).mapEmpty();
      }
    } catch (Exception e) {
      log.error("Ftp OR Sftp UploadService creation failed", e);
      return Future.failedFuture(e);
    }
  }

  public String generateFileName(BatchVoucher batchVoucher, String fileFormat) {
    JsonObject voucherJSON = JsonObject.mapFrom(batchVoucher);
    String bvShortUUID = batchVoucher.getId().substring(batchVoucher.getId().lastIndexOf('-') + 1);
    String voucherGroup = voucherJSON.getString("batchGroup");
    String voucherStart = voucherJSON.getString("start").split(DATE_TIME_DELIMITER)[0];
    String voucherEnd = voucherJSON.getString("end").split(DATE_TIME_DELIMITER)[0];
    return "bv" + DELIMITER + bvShortUUID + DELIMITER + voucherGroup + DELIMITER + voucherStart + DELIMITER + voucherEnd + "." + fileFormat;
  }

  private Future<Void> updateHolderWithExportConfig(BatchVoucherUploadHolder uploadHolder) {
    var query = buildExportConfigQuery(uploadHolder.getBatchVoucherExport().getBatchGroupId());
    return batchVoucherExportConfigService.getExportConfigs(1, 0, query, requestContext)
      .map(exportConfigs -> {
        if (!exportConfigs.getExportConfigs().isEmpty()) {
          uploadHolder.setExportConfig(exportConfigs.getExportConfigs().get(0));
          return null;
        }
        var param = new Parameter().withKey("query").withValue(query);
        var error = new Error().withMessage("Batch export configuration was not found").withParameters(List.of(param));
        log.error("Failed to retrieve export configuration with query={}", query);
        throw new HttpException(404, error);
      });
  }

  private void updateHolderWithFileFormat(BatchVoucherUploadHolder uploadHolder) {
    String fileFormat = uploadHolder.getExportConfig().getFormat().value().split("/")[1];
    uploadHolder.setFileFormat(fileFormat);
  }

  private Future<Void> updateHolderWithCredentials(BatchVoucherUploadHolder uploadHolder) {
    return batchVoucherExportConfigService.getExportConfigCredentials(uploadHolder.getExportConfig().getId(), requestContext)
      .onSuccess(credentials -> {
        if (StringUtils.isBlank(credentials.getUsername()) || StringUtils.isBlank(credentials.getPassword())) {
          throw new HttpException(404, CREDENTIALS_NOT_FOUND);
        }
        uploadHolder.setCredentials(credentials);
      })
      .recover(t -> {
        throw new HttpException(404, CREDENTIALS_NOT_FOUND);
      })
      .mapEmpty();
  }

  private Future<Void> updateHolderWithBatchVoucher(BatchVoucherUploadHolder uploadHolder) {
     return batchVoucherService.getBatchVoucherById(uploadHolder.getBatchVoucherExport().getBatchVoucherId(), buildRequestContext())
       .onSuccess(uploadHolder::setBatchVoucher)
       .recover(t -> {
         var parameter = new Parameter().withKey("batchVoucherId").withValue(uploadHolder.getBatchVoucherExport().getBatchVoucherId());
         var causeParam = new Parameter().withKey("cause").withValue(t.getMessage());
         var error = BATCH_VOUCHER_NOT_FOUND.toError().withParameters(List.of(parameter, causeParam));
         log.error("Failed to fetch batch voucher by id: {}", JsonObject.mapFrom(error).encodePrettily());
         throw new HttpException(404, error);
       })
       .mapEmpty();
   }

  private Future<Void> failUploadUpdate(BatchVoucherExport bvExport, Throwable t) {
    if (bvExport != null) {
      if (!CREDENTIALS_NOT_FOUND.equals(t.getMessage())
                      && !(t instanceof URISyntaxException || URL_NOT_FOUND_FOR_FTP.equals(t.getMessage()))) {
        bvExport.setStatus(BatchVoucherExport.Status.ERROR);
      }
      bvExport.setMessage(t.getMessage());
      return batchVoucherExportsService.updateBatchVoucherExportRecord(bvExport, buildRequestContext());
    }
    log.error("Exception occurs, when uploading batch voucher", t);
    return Future.failedFuture(t);
  }

  private Future<Void> successfulUploadUpdate(BatchVoucherUploadHolder uploadHolder) {
    BatchVoucherExport bvExport = uploadHolder.getBatchVoucherExport();
    if (bvExport != null) {
      bvExport.setStatus(BatchVoucherExport.Status.UPLOADED);
      bvExport.setMessage(generateFileName(uploadHolder.getBatchVoucher(), uploadHolder.getFileFormat()));
      log.debug("Batch voucher uploaded on FTP");
      return updateBatchVoucher(bvExport);

    }
    return Future.succeededFuture();
  }

  private Future<Void> updateBatchVoucher(BatchVoucherExport bvExport) {
    return batchVoucherExportsService.updateBatchVoucherExportRecord(bvExport, buildRequestContext());
  }

  private String buildExportConfigQuery(String groupId) {
    return "batchGroupId==" + groupId;
  }
}
