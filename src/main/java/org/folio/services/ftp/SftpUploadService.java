package org.folio.services.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.spring.integration.ApacheSshdSftpSessionFactory;
import org.folio.HttpStatus;
import org.folio.exceptions.FtpException;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class SftpUploadService {
  private static final Logger logger = LogManager.getLogger(SftpUploadService.class);
  private final String server;
  private final int port;
  private static final String FILE_SEPARATOR = "/";
  public static final String DEFAULT_WORKING_DIR = "/ftp/files/invoices";

  public SftpUploadService(String uri, Integer portFromConfig) throws URISyntaxException {
    URI u = new URI(uri);
    this.server = u.getHost();
    if (Objects.isNull(portFromConfig)) {
      portFromConfig = 22;
    }
    this.port = u.getPort() > 0 ? u.getPort() : portFromConfig;
  }

  private ApacheSshdSftpSessionFactory getSshdSessionFactory(String username, String password) throws Exception {
    var ssh = SshClient.setUpDefaultClient();
    ssh.start();
    ApacheSshdSftpSessionFactory factory = new ApacheSshdSftpSessionFactory(false);
    factory.setHost(server);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setSshClient(ssh);
    factory.setConnectTimeout(TimeUnit.SECONDS.toMillis(30L));
    factory.setAuthenticationTimeout(TimeUnit.SECONDS.toMillis(30L));
    factory.afterPropertiesSet();
    return factory;
  }

  public Future<String> upload(Context ctx, String username, String password, String folder, String filename, String content)
      throws Exception {
    Promise<String> promise = Promise.promise();
    String folderPath = StringUtils.isEmpty(folder) ? "" : (folder + FILE_SEPARATOR);
    String remoteAbsPath = folderPath + filename;

    SessionFactory<SftpClient.DirEntry> sshdFactory;
    try {
      sshdFactory = getSshdSessionFactory(username, password);
    } catch (Exception e) {
      throw new FtpException(HttpStatus.HTTP_FORBIDDEN.toInt(),String.format("Unable to connect to %s:%d", server, port));
    }
    ctx.owner().executeBlocking(blockingFeature -> {
      try (InputStream inputStream = new ByteArrayInputStream(content.getBytes()); var session = sshdFactory.getSession()) {
        logger.info("Start uploading file to SFTP path: {}", remoteAbsPath);
        if (Objects.nonNull(folder)) {
          createRemoteDirectoryIfAbsent(session, folder);
        } else {
          createRemoteDirectoryIfAbsent(session, DEFAULT_WORKING_DIR);
        }
        session.write(inputStream, remoteAbsPath);
        logger.info("File was uploaded to SFTP successfully to path: {}", remoteAbsPath);
        blockingFeature.complete("Uploaded successfully");
      } catch (Exception e) {
        logger.error("Error uploading the file {}", remoteAbsPath, e);
        blockingFeature.fail(new CompletionException(e));
      }
    }, false, asyncResultHandler(promise));
    return promise.future();
  }

  private void createRemoteDirectoryIfAbsent(Session<SftpClient.DirEntry> session, String folder) throws IOException {
    if (!session.exists(folder)) {
      String[] folders = folder.split("/");
      StringBuilder path = new StringBuilder(folders[0]).append("/");

      for (int i = 0; i < folders.length; i++) {
        if (!session.exists(path.toString())) {
          session.mkdir(path.toString());
        }
        if (i == folders.length - 1) return;
        path.append(folders[i + 1]).append("/");
      }
      logger.info("A directory has been created: {}", folder);
    }
  }

  private Handler<AsyncResult<String>> asyncResultHandler(Promise<String> promise) {
    return result -> {
      if (result.succeeded()) {
        logger.debug("Success upload to SFTP");
        promise.complete(result.result());
      } else {
        logger.error("Failed upload to Sftp", result.cause());
        promise.fail(result.cause());
      }
    };
  }
}
