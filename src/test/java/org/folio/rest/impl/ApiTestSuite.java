package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.invoices.events.handlers.InvoiceSummaryTest;
import org.folio.rest.RestVerticle;
import org.folio.rest.impl.protection.InvoicesProtectionTest;
import org.folio.rest.impl.protection.LinesProtectionTest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  InvoicesApiTest.class,
  InvoiceLinesApiTest.class,
  VouchersApiTest.class,
  VoucherLinesApiTest.class,
  DocumentsApiTest.class,
  InvoiceSummaryTest.class,
  InvoicesProtectionTest.class,
  LinesProtectionTest.class
})
public class ApiTestSuite {
  private static final Logger logger = LoggerFactory.getLogger(ApiTestSuite.class);

  private static final int okapiPort = NetworkUtils.nextFreePort();
  static final int mockPort = NetworkUtils.nextFreePort();
  private static Vertx vertx;
  private static MockServer mockServer;
  private static boolean initialised;

  @BeforeClass
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    logger.info("=== Initializing mock server - START ===");

    if (vertx == null) {
      vertx = Vertx.vertx();
    }

    mockServer = new MockServer(mockPort);
    mockServer.start();

    RestAssured.baseURI = "http://localhost:" + okapiPort;
    RestAssured.port = okapiPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    final JsonObject conf = new JsonObject();
    conf.put("http.port", okapiPort);

    final DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();
    vertx.deployVerticle(RestVerticle.class.getName(), opt, res -> {
      if(res.succeeded()) {
        deploymentComplete.complete(res.result());
      }
      else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });
    deploymentComplete.get(60, TimeUnit.SECONDS);
    initialised = true;

    logger.info("=== Initializing mock server - END ===");
  }

  @AfterClass
  public static void after() {
    vertx.close();
    mockServer.close();
    initialised = false;
  }

  public static boolean isNotInitialised() {
    return !initialised;
  }

}
