package org.folio.services.ftp;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
public class SftpUploadServiceTest {
  private static final Logger logger = LogManager.getLogger(SftpUploadServiceTest.class);
  private static final String FILENAME = "batchVoucher.json";
  private static final String USERNAME = "user";
  private static final String PASSWORD = "password";
  private static final String EXPORT_FOLDER_NAME = "ftp";
  private static final Context context = Vertx.vertx().getOrCreateContext();
  private static String uri;
  private static String id;
  private static String batch_group;
  private static final String INVALID_URI = "ftp://localhost:11/";
  private SshSimpleClient sshClient;

  @Container
  public static final GenericContainer sftp = new GenericContainer(
    new ImageFromDockerfile()
      .withDockerfileFromBuilder(builder ->
        builder
          .from("atmoz/sftp:latest")
          .run("mkdir -p " + File.separator + EXPORT_FOLDER_NAME + "; chmod -R 777 " + File.separator + EXPORT_FOLDER_NAME)
          .build()))
      .withExposedPorts(22)
      .withCommand(USERNAME + ":" + PASSWORD + ":::ftp");

  @BeforeAll
  public static void staticSetup() {
    sftp.start();
    uri = "ftp://"+sftp.getHost()+":"+sftp.getMappedPort(22)+"/";
  }

  @Test
  public void testSuccessfulUpload(VertxTestContext vertxTestContext) throws Exception {
    logger.info("=== Test successful upload ===");
    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);
    id = UUID.randomUUID().toString();
    batch_group = UUID.randomUUID().toString();
    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(id);
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(batch_group);
    batchVoucher.setCreated(new Date());

    SftpUploadService helper = new SftpUploadService(uri, 22);
    var future = helper.upload(context, USERNAME, PASSWORD, EXPORT_FOLDER_NAME, FILENAME , JsonObject.mapFrom(batchVoucher).encodePrettily())
      .onSuccess(logger::info)
      .onFailure(t -> {
        Assertions.fail(t.getMessage());
      })
      .onComplete(stringAsyncResult -> {
        SftpClient sftpClient = null;
        try {
          sftpClient = getSftpClient(USERNAME, PASSWORD, sftp.getHost(), sftp.getMappedPort(22));
          byte[] fileBytes = new byte[0];
          fileBytes = download(sftpClient, EXPORT_FOLDER_NAME + "/" + FILENAME);
          String s = new String(fileBytes, StandardCharsets.UTF_8);
          BatchVoucher downloadedBatchVoucher = new ObjectMapper().readValue(s, BatchVoucher.class);

          assertEquals(id, downloadedBatchVoucher.getId());
          assertEquals(batch_group, downloadedBatchVoucher.getBatchGroup());
          assertNotNull(fileBytes);

          sftpClient.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow());
  }

  @Test
  void testSuccessfulUploadForLongPath(VertxTestContext vertxTestContext) throws Exception {
    logger.info("=== Test successful upload ===");
    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());

    SftpUploadService helper = new SftpUploadService(uri, 22);
    var future = helper.upload(context, USERNAME, PASSWORD, EXPORT_FOLDER_NAME+"/test/long/path", FILENAME , JsonObject.mapFrom(batchVoucher).encodePrettily())
      .onSuccess(logger::info)
      .onFailure(t -> {
        Assertions.fail(t.getMessage());
      })
      .onComplete(stringAsyncResult -> {
        SftpClient sftpClient = null;
        try {
          sftpClient = getSftpClient(USERNAME, PASSWORD, sftp.getHost(), sftp.getMappedPort(22));
          byte[] fileBytes = new byte[0];
          fileBytes = download(sftpClient, EXPORT_FOLDER_NAME + "/" + FILENAME);
          String s = new String(fileBytes, StandardCharsets.UTF_8);
          BatchVoucher downloadedBatchVoucher = new ObjectMapper().readValue(s, BatchVoucher.class);

          assertEquals(id, downloadedBatchVoucher.getId());
          assertEquals(batch_group, downloadedBatchVoucher.getBatchGroup());
          assertNotNull(fileBytes);

          sftpClient.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow());
  }

  @Test
  void testSuccessfulUploadWithDefaultPath(VertxTestContext vertxTestContext) throws Exception {
    logger.info("=== Test successful upload for default location ===");
    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());

    SftpUploadService helper = new SftpUploadService(uri, 22);
    var future = helper.upload(context, USERNAME, PASSWORD, "", FILENAME , JsonObject.mapFrom(batchVoucher).encodePrettily())
      .onSuccess(logger::info)
      .onFailure(t -> {
        Assertions.fail(t.getMessage());
      })
      .onComplete(stringAsyncResult -> {
        SftpClient sftpClient = null;
        try {
          sftpClient = getSftpClient(USERNAME, PASSWORD, sftp.getHost(), sftp.getMappedPort(22));
          byte[] fileBytes = new byte[0];
          fileBytes = download(sftpClient, EXPORT_FOLDER_NAME + "/" + FILENAME);
          String s = new String(fileBytes, StandardCharsets.UTF_8);
          BatchVoucher downloadedBatchVoucher = new ObjectMapper().readValue(s, BatchVoucher.class);

          assertEquals(id, downloadedBatchVoucher.getId());
          assertEquals(batch_group, downloadedBatchVoucher.getBatchGroup());
          assertNotNull(fileBytes);

          sftpClient.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow());
  }


  @Test
  void testFailedConnect(VertxTestContext vertxTestContext) throws Exception {
    logger.info("=== Test failed connect ===");
    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);
    BatchVoucher batchVoucher = new BatchVoucher();

    SftpUploadService helper = new SftpUploadService(INVALID_URI, 22);
    var future = helper.upload(context, USERNAME, PASSWORD, EXPORT_FOLDER_NAME+"/test/long/path", FILENAME , JsonObject.mapFrom(batchVoucher).encodePrettily())
      .onSuccess(logger::info)
      .onFailure(logger::info)
      .onComplete(logger::info);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> vertxTestContext.completeNow());
  }

  public byte[] download(SftpClient sftpClient, String path) throws IOException {
    byte[] fileBytes = null;
    try (InputStream stream = sftpClient.read(path)) {
      fileBytes = stream.readAllBytes();
    }
    return fileBytes;
  }

  public SftpClient getSftpClient(String username, String password, String host, int port) throws IOException {
    sshClient = new SshSimpleClient(username, password, host, port);
    sshClient.startClient();
    ClientSession clientSession = sshClient.connect();

    AuthFuture auth = clientSession.auth();
    auth.await(30, TimeUnit.SECONDS);
    if (auth.isSuccess()) {
      return createSftpClient(clientSession);
    } else {
      clientSession.close();
      sshClient.stopClient();
      throw new IOException("SFTP server authentication failed");
    }
  }

  private SftpClient createSftpClient(ClientSession session) throws IOException {
    return SftpClientFactory.instance().createSftpClient(session);
  }

}
