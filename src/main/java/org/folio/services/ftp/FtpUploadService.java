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

public class FtpUploadService {
  private static final Logger logger = LogManager.getLogger(FtpUploadService.class);
  public static final String DEFAULT_WORKING_DIR = "/files/invoices";
  private final String server;
  private final int port;
  private final Context ctx;

  public FtpUploadService(Context ctx, String uri) throws URISyntaxException {
    if (!isUriValid(uri)) {
      throw new URISyntaxException(uri, "URI should be valid ftp path");
    }
    URI u = new URI(uri);
    this.server = u.getHost();
    this.port = u.getPort() > 0 ? u.getPort() : 21;
    this.ctx = ctx;
  }

  public boolean isUriValid(String uri) throws URISyntaxException {
    String proto = new URI(uri).getScheme();
    return StringUtils.isEmpty(proto) || proto.equalsIgnoreCase("FTP");
  }

  public Future<FTPClient> login(String username, String password) {
    FTPClient ftpClient = new FTPClient();
    Promise<FTPClient> promise = Promise.promise();
    ctx.owner().executeBlocking(blockingFeature -> {
      try {
        ftpClient.connect(server, port);
        ftpClient.setDefaultTimeout(30000);
        ftpClient.addProtocolCommandListener(FTPVertxCommandLogger.getDefListener(logger));
        ftpClient.setControlKeepAliveTimeout(Duration.ofSeconds(30));
        ftpClient.setBufferSize(1024 * 1024);
        ftpClient.setPassiveNatWorkaroundStrategy(new DefaultServerResolver(ftpClient));
        logger.info("Connected to {}:{}", server, port);
        if (ftpClient.login(username, password)) {
          blockingFeature.complete(ftpClient);
        } else {
          blockingFeature.fail(new FtpException(ftpClient.getReplyCode(), ftpClient.getReplyString().trim()));
        }
      } catch (Exception e) {
        logger.error("Error Connecting", e);
        blockingFeature.fail(e);
        disconnect(ftpClient);
      }
    }, false, asyncResultHandler(promise, "Success login to FTP", "Failed login to FTP"));
    return promise.future();
  }

  private Handler<AsyncResult<FTPClient>> asyncResultHandler(Promise<FTPClient> promise, String s, String s2) {
    return result -> {
      if (result.succeeded()) {
        logger.debug(s);
        promise.complete(result.result());
      } else {
        String message = Optional.ofNullable(result.cause())
          .map(Throwable::getMessage)
          .orElse(s2);
        logger.error(message);
        promise.fail(result.cause());
      }
    };
  }
  private Handler<AsyncResult<String>> asyncResult(Promise<String> promise) {
    return result -> {
      if (result.succeeded()) {
        logger.debug("Success upload to FTP");
        promise.complete(result.result());
      } else {
        String message = Optional.ofNullable(result.cause())
          .map(Throwable::getMessage)
          .orElse("Failed upload to FTP");
        logger.error(message);
        promise.fail(result.cause());
      }
    };
  }

  public Future<String> upload(Context ctx, String username, String password, String filename, String content) {
    Promise<String> promise = Promise.promise();
    ctx.owner().executeBlocking(blockingFeature -> login(username, password).compose(ftpClient -> {
    try (InputStream is = new ByteArrayInputStream(content.getBytes())) {
      ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
      ftpClient.enterLocalPassiveMode();
      changeWorkingDirectory(ftpClient);
      if (ftpClient.storeFile(filename, is)) {
        logger.info("Batch voucher uploaded on FTP");
        blockingFeature.complete(ftpClient.getReplyString().trim());
      } else {
        blockingFeature.fail(new FtpException(ftpClient.getReplyCode(), ftpClient.getReplyString().trim()));
      }
    } catch (Exception e) {
      logger.error("Error uploading", e);
      blockingFeature.fail(new CompletionException(e));
    } finally {
      try {
        ftpClient.logout();
      } catch (IOException e) {
        logger.error("Error logout from FTP", e);
      } finally {
        disconnect(ftpClient);
      }
    }
    return promise.future();
    }), false, asyncResult(promise));
    return promise.future();
  }

  private void changeWorkingDirectory(FTPClient ftpClient) throws IOException {
    if (isDirectoryAbsent(DEFAULT_WORKING_DIR, ftpClient)){
      ftpClient.makeDirectory(DEFAULT_WORKING_DIR);
    }
    ftpClient.changeWorkingDirectory(DEFAULT_WORKING_DIR);
  }

  public boolean isDirectoryAbsent(String dirPath, FTPClient ftpClient) throws IOException {
    ftpClient.changeWorkingDirectory(dirPath);
    int returnCode = ftpClient.getReplyCode();
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

  private void disconnect(FTPClient ftpClient) {
    try {
      ftpClient.disconnect();
    } catch (IOException e) {
      logger.error("Error disconnect from FTP", e);
    }
  }

}
