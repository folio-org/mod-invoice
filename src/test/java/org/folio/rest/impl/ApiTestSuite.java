package org.folio.rest.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.converters.BatchVoucherModelConverterTest;
import org.folio.converters.BatchedVoucherLinesModelConverterTest;
import org.folio.converters.BatchedVoucherModelConverterTest;
import org.folio.invoices.events.handlers.InvoiceSummaryTest;
import org.folio.invoices.util.FtpUploadHelperTest;
import org.folio.invoices.util.HelperUtilsTest;
import org.folio.invoices.utils.HelperUtils;
import org.folio.jaxb.DefaultJAXBRootElementNameResolverTest;
import org.folio.jaxb.JAXBUtilTest;
import org.folio.jaxb.XMLConverterTest;
import org.folio.rest.RestVerticle;
import org.folio.rest.impl.protection.InvoicesProtectionTest;
import org.folio.rest.impl.protection.LinesProtectionTest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.schemas.xsd.BatchVoucherSchemaXSDTest;
import org.folio.services.BatchVoucherGenerateService;
import org.folio.services.BatchVoucherGenerateServiceTest;
import org.folio.services.InvoiceRetrieveServiceTest;
import org.folio.services.VoucherLinesRetrieveServiceTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  InvoicesApiTest.class,
  InvoiceLinesApiTest.class,
  VouchersApiTest.class,
  VoucherLinesApiTest.class,
  DocumentsApiTest.class,
  InvoiceSummaryTest.class,
  InvoicesProtectionTest.class,
  LinesProtectionTest.class,
  InvoicesProratedAdjustmentsTest.class,
  InvoiceLinesProratedAdjustmentsTest.class,
  BatchVoucherExportConfigTest.class,
  BatchVoucherExportConfigCredentialsTest.class,
  BatchGroupsApiTest.class,
  BatchVoucherSchemaXSDTest.class,
  BatchVoucherImplTest.class,
  BatchedVoucherLinesModelConverterTest.class,
  BatchedVoucherModelConverterTest.class,
  BatchVoucherModelConverterTest.class,
  DefaultJAXBRootElementNameResolverTest.class,
  JAXBUtilTest.class,
  XMLConverterTest.class,
  FtpUploadHelperTest.class,
  BatchVoucherExportsApiTest.class,
  HelperUtilsTest.class,
  InvoiceRetrieveServiceTest.class,
  VoucherLinesRetrieveServiceTest.class,
  BatchVoucherGenerateServiceTest.class
})
public class ApiTestSuite {
  private static final Logger logger = LoggerFactory.getLogger(ApiTestSuite.class);

  private static final int okapiPort = NetworkUtils.nextFreePort();
  public static final int mockPort = NetworkUtils.nextFreePort();
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

    logger.info("Using port: " + mockPort + " for MockServer");

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
