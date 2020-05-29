package org.folio.models;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfig;

public class BatchVoucherUploadHolder {
  private String fileFormat;
  private Credentials credentials;
  private ExportConfig exportConfig;
  private BatchVoucher batchVoucher;
  private BatchVoucherExport batchVoucherExport;

  public Credentials getCredentials() {
    return credentials;
  }

  public void setCredentials(Credentials credentials) {
    this.credentials = credentials;
  }

  public ExportConfig getExportConfig() {
    return exportConfig;
  }

  public void setExportConfig(ExportConfig exportConfig) {
    this.exportConfig = exportConfig;
  }

  public BatchVoucher getBatchVoucher() {
    return batchVoucher;
  }

  public void setBatchVoucher(BatchVoucher batchVoucher) {
    this.batchVoucher = batchVoucher;
  }

  public BatchVoucherExport getBatchVoucherExport() {
    return batchVoucherExport;
  }

  public void setBatchVoucherExport(BatchVoucherExport batchVoucherExport) {
    this.batchVoucherExport = batchVoucherExport;
  }

  public String getFileFormat() {
    return fileFormat;
  }

  public void setFileFormat(String fileFormat) {
    this.fileFormat = fileFormat;
  }
}
