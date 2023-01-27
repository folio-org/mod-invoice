package org.folio.services.ftp;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.FtpException;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class FtpUploadServiceTest {

  private static final Logger logger = LogManager.getLogger(FtpUploadServiceTest.class);

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
  public void testFailedConnect(VertxTestContext vertxTestContext) throws URISyntaxException {
    logger.info("=== Test unsuccessful login ===");

    FtpUploadService helper = new FtpUploadService(context, "ftp://localhost:1");
    var future = helper.login(username_valid, password_valid)
      .onSuccess(m -> Assertions.fail("Expected a connection failure but got: " + m))
      .onFailure(t -> {
        String message;
        if (t.getCause() instanceof FtpException) {
          message = ((FtpException) t.getCause()).getReplyMessage();
        } else {
          message = t.getMessage();
        }
        logger.info(message);
      })
      .onComplete(result -> helper.logout());

    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void testSuccessfulLogin(VertxTestContext vertxTestContext) throws URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Test successful login ===");

    FtpUploadService helper = new FtpUploadService(context, uri);
    var future = helper.login(username_valid, password_valid)
      .onSuccess(logger::info)
      .onFailure(t -> {
        logger.error(t);
        Assertions.fail(t.getMessage());
      })
      .onComplete(asyncResult -> helper.logout());

    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow());
  }

  @Test
  public void testFailedLogin(VertxTestContext vertxTestContext) throws URISyntaxException {
    logger.info("=== Test unsuccessful login ===");

    FtpUploadService helper = new FtpUploadService(context, uri);
    var future = helper.login(username_valid, password_invalid)
      .onSuccess(m -> Assertions.fail("Expected a login failure but got: " + m))
      .onFailure(t -> {
        String message;
        if (t.getCause() instanceof FtpException) {
          message = ((FtpException) t.getCause()).getReplyMessage();
        } else {
          message = t.getMessage();
        }
        logger.info(message);
      });

    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void testSuccessfulUpload(VertxTestContext vertxTestContext) throws URISyntaxException {
    logger.info("=== Test successful upload ===");

    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());

    FtpUploadService helper = new FtpUploadService(context, uri);
    var future = helper.login(username_valid, password_valid)
      .onSuccess(logger::info)
      .compose(v -> helper.upload(context, filename, JsonObject.mapFrom(batchVoucher).encodePrettily()))
      .onSuccess(logger::info)
      .onFailure(t -> {
        logger.error(t);
        Assertions.fail(t.getMessage());
      })
      .onComplete(asyncResult -> helper.logout()
        .onComplete(logger::info));
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow());
  }

  @Test
  public void testFailedUpload(VertxTestContext vertxTestContext) throws URISyntaxException {
    logger.info("=== Test unsuccessful upload ===");

    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());

    FtpUploadService helper = new FtpUploadService(context, uri);
    var future = helper.login(username_valid, password_valid)
      .onSuccess(logger::info)
      .compose(v -> helper.upload(context,"/invalid/path/"+filename, JsonObject.mapFrom(batchVoucher).encodePrettily()))
      .onSuccess(m -> Assertions.fail("Expected upload failure but got " + m))
      .onFailure(logger::info)
      .onComplete(asyncResult -> helper.logout());
    vertxTestContext.assertFailure(future)
      .onComplete(result -> vertxTestContext.completeNow());
  }

}
