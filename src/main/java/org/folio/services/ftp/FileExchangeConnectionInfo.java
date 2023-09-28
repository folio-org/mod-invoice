package org.folio.services.ftp;


import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ExportConfig;

public interface FileExchangeConnectionInfo {
  ExportConfig.FtpFormat getExchangeConnectionFormat();
  Future<Void> testConnection(String username, String password);
}
