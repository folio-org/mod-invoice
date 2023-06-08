package org.folio.services.ftp;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

import java.io.File;
import java.util.Date;
import java.util.UUID;

@ExtendWith(VertxExtension.class)
public class SftpUploadServiceTest {

  private static final Logger logger = LogManager.getLogger(SftpUploadServiceTest.class);

  private static final String FOLDER = "test";
  private static final String FILENAME = "batchVoucher.json";

  private static final String USERNAME = "user";
  private static final String PASSWORD = "password";
  private static final String EXPORT_FOLDER_NAME = "upload";
  private static final Context context = Vertx.vertx().getOrCreateContext();
  private static String uri;


  @Container
  public static final GenericContainer sftp = new GenericContainer(
    new ImageFromDockerfile()
      .withDockerfileFromBuilder(builder ->
        builder
          .from("atmoz/sftp:latest")
          .run("mkdir -p " + File.separator + EXPORT_FOLDER_NAME + "; chmod -R 777 " + File.separator + EXPORT_FOLDER_NAME)
          .build()))
    .withExposedPorts(21)
    .withCommand(USERNAME + ":" + PASSWORD + ":::upload");


  @BeforeAll
  public static void staticSetup() {
    uri = "ftp://localhost:" + sftp.getExposedPorts().get(0) + "/";
  }


  @Test
  public void testSuccessfulUpload(VertxTestContext vertxTestContext) throws Exception {
    logger.info("=== Test successful upload ===");

    Date end = new Date();
    end.setTime(System.currentTimeMillis() - 864000000);

    BatchVoucher batchVoucher = new BatchVoucher();
    batchVoucher.setId(UUID.randomUUID().toString());
    batchVoucher.setStart(end);
    batchVoucher.setEnd(end);
    batchVoucher.setBatchGroup(UUID.randomUUID().toString());
    batchVoucher.setCreated(new Date());
    SftpUploadService helper = new SftpUploadService(context, uri);


    var future = helper.upload(USERNAME, PASSWORD, FOLDER, FILENAME , JsonObject.mapFrom(batchVoucher).encodePrettily())
      .onSuccess(logger::info)
      .onFailure(t -> {
        logger.error(t);
        Assertions.fail(t.getMessage());
      })
        .onComplete(logger::info);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow());
  }

}
