package org.folio.invoices.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.commons.net.ftp.FTPClient;
import org.folio.exceptions.FtpException;
import org.folio.rest.jaxrs.model.BatchVoucher;


import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class FtpUploadHelper implements UploadHelper {

  private static final Logger logger = LoggerFactory.getLogger(FtpUploadHelper.class);

  private final FTPClient ftp;
  private final String server;
  private final int port;

  public FtpUploadHelper(String uri) throws URISyntaxException {
    this.ftp = new FTPClient();
    URI u = new URI(uri);
    this.server = u.getHost();
    this.port = u.getPort() > 0 ? u.getPort() : 21;
  }

  public CompletableFuture<String> login(String username, String password) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        ftp.connect(server, port);
        if (logger.isInfoEnabled()) {
          logger.info("Connected to {}:{}", server, port);
        }
      } catch (Exception e) {
        logger.error("Error Connecting", e);
        throw new CompletionException(e);
      }

      try {
        if (ftp.login(username, password)) {
          return ftp.getReplyString().trim();
        } else {
          throw new FtpException(ftp.getReplyCode(), ftp.getReplyString().trim());
        }
      } catch (Exception e) {
        logger.error("Error logging in", e);
        throw new CompletionException(e);
      }
    });
  }

  public CompletableFuture<String> logout() {
    return CompletableFuture.supplyAsync(() -> {
      try {
        if (ftp != null && ftp.isConnected()) {
          ftp.logout();
          return ftp.getReplyString().trim();
        }
        return null;
      } catch (Exception e) {
        logger.error("Error logging out", e);
        throw new CompletionException(e);
      }
    });
  }

  public CompletableFuture<String> upload(String filename, BatchVoucher batchVoucher) {
    return CompletableFuture.supplyAsync(() -> {
      try (InputStream is = new ByteArrayInputStream(JsonObject.mapFrom(batchVoucher)
        .encode()
        .getBytes())) {
        if (ftp.storeFile(filename, is)) {
          return ftp.getReplyString().trim();
        } else {
          throw new FtpException(ftp.getReplyCode(), ftp.getReplyString().trim());
        }
      } catch (Exception e) {
        logger.error("Error uploading", e);
        throw new CompletionException(e);
      }
    });
  }

}
