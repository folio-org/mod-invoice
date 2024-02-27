package org.folio.services.ftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.FtpException;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.ExportConfig;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public class FtpUploadService implements FileExchangeService {

  private static final Logger logger = LogManager.getLogger(FtpUploadService.class);
  private static final String DEFAULT_WORKING_DIR = "/files/invoices";
  public static final String URL_NOT_FOUND_FOR_FTP = "URI for FTP upload was not found";
  public static final String URI_SYNTAX_ERROR = "URI should be valid ftp path";

  private final String server;
  private final int port;
  private final Context ctx;

  public FtpUploadService(Context ctx, String uri, Integer portFromConfig) throws URISyntaxException {
    if (StringUtils.isBlank(uri)) {
      logger.error("FtpUploadService:: URI is not found");
      throw new HttpException(400, URL_NOT_FOUND_FOR_FTP);
    }
    if (!isUriValid(uri)) {
      logger.error("FtpUploadService:: URI '{}' is not valid", uri);
      throw new URISyntaxException(uri, URI_SYNTAX_ERROR);
    }
    URI u = new URI(uri);
    this.server = u.getHost();
    if (Objects.isNull(portFromConfig)) {
      portFromConfig = 21;
    }
    this.port = u.getPort() > 0 ? u.getPort() : portFromConfig;
    this.ctx = ctx;
  }

  public boolean isUriValid(String uri) throws URISyntaxException {
    String proto = new URI(uri).getScheme();
    return StringUtils.isEmpty(proto) || proto.equalsIgnoreCase("FTP");
  }

  @SuppressWarnings("java:S5332")
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
        logger.error("Error Connecting FTP server {} on port {}", server, port, e);
        blockingFeature.fail(e);
        disconnect(ftpClient);
      }
    }, false, asyncResultHandler(promise));
    return promise.future();
  }

  private Handler<AsyncResult<FTPClient>> asyncResultHandler(Promise<FTPClient> promise) {
    return result -> {
      if (result.succeeded()) {
        logger.debug("Success login to FTP");
        promise.complete(result.result());
      } else {
        logger.error("Failed login to FTP", result.cause());
        promise.fail(result.cause());
      }
    };
  }

  private Handler<AsyncResult<String>> asyncResult(Promise<String> promise) {
    return result -> {
      if (result.succeeded()) {
        logger.info("Success upload to FTP");
        promise.complete(result.result());
      } else {
        logger.error("Failed upload to FTP", result.cause());
        promise.fail(result.cause());
      }
    };
  }

  public Future<String> upload(Context ctx, String username, String password, String folder, String filename, String content) {
    Promise<String> promise = Promise.promise();
    ctx.owner().executeBlocking(blockingFeature -> login(username, password).compose(ftpClient -> {
      try (InputStream is = new ByteArrayInputStream(content.getBytes())) {
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.enterLocalPassiveMode();
        if (StringUtils.isNotBlank(folder)) {
          changeWorkingDirectory(folder, ftpClient);
        } else {
          logger.warn("upload:: folder is empty using default working directory={}", DEFAULT_WORKING_DIR);
          changeWorkingDirectory(DEFAULT_WORKING_DIR, ftpClient);
        }
        if (ftpClient.storeFile(filename, is)) {
          logger.info("Batch voucher uploaded on FTP {}", filename);
          blockingFeature.complete(ftpClient.getReplyString().trim());
        } else {
          blockingFeature.fail(new FtpException(ftpClient.getReplyCode(), ftpClient.getReplyString().trim()));
        }
      } catch (Exception e) {
        logger.error("Error uploading file {}", filename, e);
        blockingFeature.fail(new CompletionException(e));
      } finally {
        disconnect(ftpClient);
      }
      return promise.future();
    }), false, asyncResult(promise));

    return promise.future();
  }

  private void changeWorkingDirectory(String folder, FTPClient ftpClient) throws IOException {
    if (isDirectoryAbsent(folder, ftpClient)) {
      ftpClient.makeDirectory(folder);
    }
    ftpClient.changeWorkingDirectory(folder);
  }

  public boolean isDirectoryAbsent(String dirPath, FTPClient ftpClient) throws IOException {
    ftpClient.changeWorkingDirectory(dirPath);
    int returnCode = ftpClient.getReplyCode();
    return returnCode == 550;
  }

  @Override
  public ExportConfig.FtpFormat getExchangeConnectionFormat() {
    return ExportConfig.FtpFormat.FTP;
  }

  @Override
  public Future<Void> testConnection(String username, String password) {
    return login(username, password)
      .onSuccess(this::disconnect)
      .mapEmpty();
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
