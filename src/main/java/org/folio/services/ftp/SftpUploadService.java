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
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SftpUploadService {
  private static final Logger logger = LogManager.getLogger(SftpUploadService.class);
  private final String server;
  private final int port;
  private final Context ctx;
  private static final String FILE_SEPARATOR = "/";

  public SftpUploadService(Context ctx, String uri) throws URISyntaxException {
    URI u = new URI(uri);
    this.server = u.getHost();
    this.port = u.getPort() > 0 ? u.getPort() : 22;
    this.ctx = ctx;
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

  public Future<String> upload(String username, String password, String folder, String filename, String content)
      throws Exception {
    Promise<String> promise = Promise.promise();
    String folderPath = StringUtils.isEmpty(folder) ? "" : (folder + FILE_SEPARATOR);
    String remoteAbsPath = folderPath + filename;

    SessionFactory<SftpClient.DirEntry> sshdFactory;
    try {
      sshdFactory = getSshdSessionFactory(username, password);
    } catch (Exception e) {
      throw new IllegalStateException(String.format("Unable to connect to %s:%d", server, port));
    }
    ctx.owner().executeBlocking(blockingFeature -> {
    try (InputStream inputStream = new ByteArrayInputStream(content.getBytes()); var session = sshdFactory.getSession()) {
      logger.info("Start uploading file to SFTP path: {}", remoteAbsPath);

      createRemoteDirectoryIfAbsent(session, folder);
      session.write(inputStream, remoteAbsPath);
      logger.info("uploaded: {}", remoteAbsPath);
      blockingFeature.complete();
    } catch (Exception e) {
      logger.info("Error uploading the file", e);
      blockingFeature.fail(e);
    }}, false, asyncResultHandler(promise));
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
        if (i == folders.length - 1)
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
        String message = Optional.ofNullable(result.cause())
          .map(Throwable::getMessage)
          .orElse("Failed upload to SFTP");
        logger.error(message);
        promise.fail(result.cause());
      }
    };
  }

}
