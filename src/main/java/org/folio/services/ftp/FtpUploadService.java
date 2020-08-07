package org.folio.services.ftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.folio.exceptions.FtpException;

import io.vertx.core.Context;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class FtpUploadService implements UploadService {

  private static final Logger logger = LoggerFactory.getLogger(FtpUploadService.class);
  public static final String DEFAULT_WORKING_DIR = "/files/invoices";

  private final FTPClient ftp;
  private final String server;
  private final int port;

  public FtpUploadService(String uri) throws URISyntaxException {
    if (!isUriValid(uri)) {
      throw new URISyntaxException(uri, "URI should be valid ftp path");
    }
    this.ftp = new FTPClient();
    URI u = new URI(uri);
    this.server = u.getHost();
    this.port = u.getPort() > 0 ? u.getPort() : 21;
  }

  public boolean isUriValid(String uri) throws URISyntaxException {
    String proto = new URI(uri).getScheme();
    return StringUtils.isEmpty(proto) || proto.equalsIgnoreCase("FTP");
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

  public CompletableFuture<String> upload(Context ctx, String filename, String content) {
  return VertxCompletableFuture.supplyBlockingAsync(ctx, () -> {
      try (InputStream is = new ByteArrayInputStream(content.getBytes())) {
        ftp.addProtocolCommandListener( FTPVertxCommandLogger.getDefListener(logger));
        ftp.setBufferSize(1024 * 1024);
        ftp.setControlKeepAliveTimeout(300);
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        ftp.setPassiveNatWorkaroundStrategy(new DefaultServerResolver(ftp));
        ftp.enterLocalPassiveMode();
        changeWorkingDirectory();
        if (ftp.storeFile(filename, is)) {
          return ftp.getReplyString().trim();
        } else {
          throw new FtpException(ftp.getReplyCode(), ftp.getReplyString().trim());
        }
      } catch (Exception e) {
        logger.error("Error uploading", e);
        throw new CompletionException(e);
      } finally {
        try {
          ftp.logout();
          ftp.disconnect();
        } catch (IOException e) {
          logger.error("Error logout", e);
        }
      }
    });
  }

  private void changeWorkingDirectory() throws IOException {
    if (isDirectoryAbsent(DEFAULT_WORKING_DIR)){
      ftp.makeDirectory(DEFAULT_WORKING_DIR);
    }
    ftp.changeWorkingDirectory(DEFAULT_WORKING_DIR);
  }

  public boolean isDirectoryAbsent(String dirPath) throws IOException {
    ftp.changeWorkingDirectory(dirPath);
    int returnCode = ftp.getReplyCode();
    return returnCode == 550;
  }

  public static class DefaultServerResolver implements FTPClient.HostnameResolver {
    private FTPClient client;

    public DefaultServerResolver(FTPClient client) {
      this.client = client;
    }

    @Override
    public String resolve(String hostname) {
      return this.client.getRemoteAddress().getHostAddress();
    }
  }

}
