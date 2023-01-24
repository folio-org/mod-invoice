package org.folio.services.ftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.FtpException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public class FtpUploadService implements UploadService {

  private static final Logger logger = LogManager.getLogger(FtpUploadService.class);
  public static final String DEFAULT_WORKING_DIR = "/files/invoices";

  private final FTPClient ftp;
  private final String server;
  private final int port;
  private final Context ctx;

  public FtpUploadService(Context ctx, String uri) throws URISyntaxException {
    if (!isUriValid(uri)) {
      throw new URISyntaxException(uri, "URI should be valid ftp path");
    }
    this.ftp = new FTPClient();
    this.ftp.setDefaultTimeout(30000);
    this.ftp.addProtocolCommandListener(FTPVertxCommandLogger.getDefListener(logger));
    this.ftp.setControlKeepAliveTimeout(Duration.ofSeconds(30));
    this.ftp.setBufferSize(1024 * 1024);
    this.ftp.setPassiveNatWorkaroundStrategy(new DefaultServerResolver(ftp));
    URI u = new URI(uri);
    this.server = u.getHost();
    this.port = u.getPort() > 0 ? u.getPort() : 21;
    this.ctx = ctx;
  }

  public boolean isUriValid(String uri) throws URISyntaxException {
    String proto = new URI(uri).getScheme();
    return StringUtils.isEmpty(proto) || proto.equalsIgnoreCase("FTP");
  }

  public Future<String> login(String username, String password) {
    Promise<String> promise = Promise.promise();
    ctx.owner().executeBlocking(blockingFeature -> {
      try {
        ftp.connect(server, port);
        logger.info("Connected to {}:{}", server, port);
        if (ftp.login(username, password)) {
          blockingFeature.complete(ftp.getReplyString().trim());
        } else {
          blockingFeature.fail(new FtpException(ftp.getReplyCode(), ftp.getReplyString().trim()));
        }
      } catch (Exception e) {
        logger.error("Error Connecting", e);
        blockingFeature.fail(e);
        disconnect();
      }
        blockingFeature.complete();
      },
      false,
      asyncResultHandler(promise, "Success login to FTP", "Failed login to FTP"));
    return promise.future();
  }

  public Future<String> logout() {
    Promise<String> promise = Promise.promise();
    ctx.owner().executeBlocking(blockingFeature -> {
        try {
          if (ftp != null && ftp.isConnected()) {
            ftp.logout();
            blockingFeature.complete(ftp.getReplyString().trim());
          }
          blockingFeature.complete(null);
        } catch (Exception e) {
          logger.error("Error logging out", e);
          blockingFeature.fail(new CompletionException(e));
        } finally {
          disconnect();
        }
      },
        false,
        asyncResultHandler(promise, "Success logout from FTP", "Failed logout from FTP")
      );
      return promise.future();
  }

  private Handler<AsyncResult<Object>> asyncResultHandler(Promise<String> promise, String s, String s2) {
    return result -> {
      if (result.succeeded()) {
        logger.debug(s);
        promise.complete(result.result().toString());
      } else {
        String message = Optional.ofNullable(result.cause())
          .map(Throwable::getMessage)
          .orElse(s2);
        logger.error(message);
        promise.fail(result.cause());
      }
    };
  }

  public Future<String> upload(Context ctx, String filename, String content) {
    Promise<String> promise = Promise.promise();
    ctx.owner().executeBlocking(blockingFeature -> {
      try (InputStream is = new ByteArrayInputStream(content.getBytes())) {
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        ftp.enterLocalPassiveMode();
        changeWorkingDirectory();
        if (ftp.storeFile(filename, is)) {
          logger.debug("Batch voucher uploaded on FTP");
          blockingFeature.complete(ftp.getReplyString().trim());
        } else {
          blockingFeature.fail(new FtpException(ftp.getReplyCode(), ftp.getReplyString().trim()));
        }
      } catch (Exception e) {
        logger.error("Error uploading", e);
        blockingFeature.fail(new CompletionException(e));
      } finally {
        try {
          ftp.logout();
        } catch (IOException e) {
          logger.error("Error logout from FTP", e);
        } finally {
          disconnect();
        }
      }
    },
      false,
      asyncResultHandler(promise, "Success upload to FTP", "Failed upload to FTP"));
    return promise.future();
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

  private void disconnect() {
    try {
      ftp.disconnect();
    } catch (IOException e) {
      logger.error("Error disconnect from FTP", e);
    }
  }

}
