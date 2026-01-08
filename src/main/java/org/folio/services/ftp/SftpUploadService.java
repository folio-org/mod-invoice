package org.folio.services.ftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.spring.integration.ApacheSshdSftpSessionFactory;
import org.folio.HttpStatus;
import org.folio.exceptions.FtpException;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.utils.FutureUtils;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class SftpUploadService implements FileExchangeService {

  private static final Logger logger = LogManager.getLogger(SftpUploadService.class);

  private static final String FILE_SEPARATOR = "/";
  private static final String DEFAULT_WORKING_DIR = "/ftp/files/invoices";

  private final String server;
  private final int port;

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

  public Session<SftpClient.DirEntry> login(String username, String password) throws FtpException {
    try {
      SessionFactory<SftpClient.DirEntry> sshdFactory = getSshdSessionFactory(username, password);
      return sshdFactory.getSession();
    } catch (Exception e) {
      throw new FtpException(HttpStatus.HTTP_FORBIDDEN.toInt(), String.format("Unable to connect to %s:%d", server, port));
    }
  }

  public Future<String> upload(Context ctx, String username, String password, String folder, String filename, String content) {
    Promise<String> promise = Promise.promise();
    String remoteAbsPath;
    if (StringUtils.isNotEmpty(folder)) {
      String folderPath = StringUtils.isEmpty(folder) ? "" : (folder + FILE_SEPARATOR);
      remoteAbsPath = folderPath + filename;
    } else {
      remoteAbsPath = DEFAULT_WORKING_DIR + FILE_SEPARATOR + filename;
    }

    return ctx.owner().executeBlocking(() -> {
      var session = login(username, password);
      try (InputStream inputStream = new ByteArrayInputStream(content.getBytes()); session) {
        logger.debug("Start uploading file to SFTP path: {}", remoteAbsPath);
        if (StringUtils.isNotEmpty(folder)) {
          createRemoteDirectoryIfAbsent(session, folder);
        } else {
          createRemoteDirectoryIfAbsent(session, DEFAULT_WORKING_DIR);
        }
        session.write(inputStream, remoteAbsPath);
        logger.debug("File was uploaded to SFTP successfully to path: {}", remoteAbsPath);
        return "Uploaded successfully";
      } catch (Exception e) {
        logger.error("Error uploading the file {}", remoteAbsPath, e);
        throw new CompletionException(e);
      } finally {
        if (Objects.nonNull(session) && session.isOpen()) {
          session.close();
        }
      }
    }, false).onComplete(result -> {
      if (result.succeeded()) {
        logger.debug("Success upload to SFTP");
        promise.complete(result.result());
      } else {
        logger.error("Failed upload to Sftp", result.cause());
        promise.fail(result.cause());
      }
    });
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
      logger.debug("A directory has been created: {}", folder);
    }
  }

  @Override
  public ExportConfig.FtpFormat getExchangeConnectionFormat() {
    return ExportConfig.FtpFormat.SFTP;
  }

  @Override
  public Future<Void> testConnection(String username, String password) {
    return FutureUtils.asFuture(() -> login(username, password))
      .onSuccess(Session::close)
      .mapEmpty();
  }

}
