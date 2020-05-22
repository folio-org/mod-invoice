package org.folio.invoices.util;

import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.exceptions.FtpException;
import org.folio.services.ftp.FtpUploadService;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class FtpUploadServiceTest {

  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");
  }

  private static final Logger logger = LoggerFactory.getLogger(FtpUploadServiceTest.class);

  private static FakeFtpServer fakeFtpServer;

  private static final String user_home_dir = "/home/bob";
  private static final String filename = "batchVoucher.json";

  private static final String username_valid = "bob";

  private static final String password_valid = "letmein";
  private static final String password_invalid = "dontletmein";

  private static String uri;

  private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSz", Locale.ENGLISH);
  static {
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
  private static final Context context = Vertx.vertx().getOrCreateContext();

  @BeforeAll
  public static void setup() {
    fakeFtpServer = new FakeFtpServer();
    fakeFtpServer.setServerControlPort(0); // use any free port

    FileSystem fileSystem = new UnixFakeFileSystem();
    fileSystem.add(new DirectoryEntry(user_home_dir));
    fakeFtpServer.setFileSystem(fileSystem);

    UserAccount userAccount = new UserAccount(username_valid, password_valid, user_home_dir);

    fakeFtpServer.addUserAccount(userAccount);

    fakeFtpServer.start();

    uri = "ftp://localhost:" + fakeFtpServer.getServerControlPort() + "/";
    logger.info("Mock FTP server running at: " + uri);
  }

  @AfterAll
  public static void teardown() {
    if(fakeFtpServer != null && !fakeFtpServer.isShutdown()) {
      logger.info("Shutting down mock FTP server");
      fakeFtpServer.stop();
    }
  }

  @Test
  public void testFailedConnect() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Test unsuccessful login ===");

    FtpUploadService helper = new FtpUploadService("ftp://localhost:1");
    helper.login(username_valid, password_valid)
      .thenAccept(m -> fail("Expected a connection failure but got: " + m))
      .exceptionally(t -> {
        String message;
        if (t.getCause() instanceof FtpException) {
          message = ((FtpException) t.getCause()).getReplyMessage();
        } else {
          message = t.getMessage();
        }
        logger.info(message);
        return null;
      })
      .whenComplete((i, t) -> helper.logout().thenAccept(logger::info))
      .get(10, TimeUnit.SECONDS);
  }

  @Test
  public void testSuccessfulLogin() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Test successful login ===");

    FtpUploadService helper = new FtpUploadService(uri);
    helper.login(username_valid, password_valid)
      .thenAccept(logger::info)
      .exceptionally(t -> {
        logger.error(t);
        fail(t.getMessage());
        return null;
      })
      .whenComplete((i, t) -> helper.logout()
        .thenAccept(logger::info))
      .get(10, TimeUnit.SECONDS);
  }

  @Test
  public void testFailedLogin() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Test unsuccessful login ===");

    FtpUploadService helper = new FtpUploadService(uri);
    helper.login(username_valid, password_invalid)
      .thenAccept(m -> fail("Expected a login failure but got: " + m))
      .exceptionally(t -> {
        String message;
        if (t.getCause() instanceof FtpException) {
          message = ((FtpException) t.getCause()).getReplyMessage();
        } else {
          message = t.getMessage();
        }
        logger.info(message);
        return null;
      }).get(10, TimeUnit.SECONDS);
  }

  @Test
  public void testSuccessfulUpload() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Test successful upload ===");

    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());

    FtpUploadService helper = new FtpUploadService(uri);
    helper.login(username_valid, password_valid)
      .thenAccept(logger::info)
      .thenCompose(v -> helper.upload(context, filename, batchVoucher))
      .thenAccept(logger::info)
      .exceptionally(t -> {
        logger.error(t);
        fail(t.getMessage());
        return null;
      })
      .whenComplete((i, t) -> helper.logout()
        .thenAccept(logger::info))
      .get(10, TimeUnit.SECONDS);
  }

  @Test
  public void testFailedUpload() throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Test unsuccessful upload ===");

    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());

    FtpUploadService helper = new FtpUploadService(uri);
    helper.login(username_valid, password_valid)
      .thenAccept(logger::info)
      .thenCompose(v -> helper.upload(context,"/invalid/path/"+filename, batchVoucher))
      .thenAccept(m -> fail("Expected upload failure but got " + m))
      .exceptionally(t -> {
        logger.info(t);
        return null;
      })
      .whenComplete((i, t) -> helper.logout()
        .thenAccept(logger::info))
      .get(10, TimeUnit.SECONDS);
  }

}
