package org.folio.rest.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.converters.BatchVoucherModelConverterTest;
import org.folio.converters.BatchedVoucherLinesModelConverterTest;
import org.folio.converters.BatchedVoucherModelConverterTest;
import org.folio.invoices.events.handlers.InvoiceSummaryTest;
import org.folio.jaxb.DefaultJAXBRootElementNameResolverTest;
import org.folio.jaxb.JAXBUtilTest;
import org.folio.jaxb.XMLConverterTest;
import org.folio.rest.RestVerticle;
import org.folio.rest.impl.protection.InvoicesProtectionTest;
import org.folio.rest.impl.protection.LinesProtectionTest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.schemas.xsd.BatchVoucherSchemaXSDTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@RunWith(JUnitPlatform.class)
public class ApiTestSuite {
  private static final Logger logger = LoggerFactory.getLogger(ApiTestSuite.class);

  private static final int okapiPort = NetworkUtils.nextFreePort();
  public static final int mockPort = NetworkUtils.nextFreePort();
  private static MockServer mockServer;
  private static Vertx vertx;
  private static boolean initialised;

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
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
      if (res.succeeded()) {
        deploymentComplete.complete(res.result());
      } else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });
    deploymentComplete.get(60, TimeUnit.SECONDS);
    initialised = true;
  }

  @AfterAll
  public static void after() {
    mockServer.close();
    vertx.close();
    initialised = false;
  }

  public static boolean isNotInitialised() {
    return !initialised;
  }

  @Nested
  class InvoicesApiTestNested extends InvoicesApiTest {
  }

  @Nested
  class InvoiceLinesApiTestNested extends InvoiceLinesApiTest {
  }

  @Nested
  class VouchersApiTestNested extends VouchersApiTest {
  }

  @Nested
  class VoucherLinesApiTestNested extends VoucherLinesApiTest {
  }

  @Nested
  class DocumentsApiTestNested extends DocumentsApiTest {
  }

  @Nested
  class InvoiceSummaryTestNested extends InvoiceSummaryTest {
  }

  @Nested
  class InvoicesProtectionTestNested extends InvoicesProtectionTest {
  }

  @Nested
  class LinesProtectionTestNested extends LinesProtectionTest {
  }

  @Nested
  class InvoicesProratedAdjustmentsTestNested extends InvoicesProratedAdjustmentsTest {
  }

  @Nested
  class InvoiceLinesProratedAdjustmentsTestNested extends InvoiceLinesProratedAdjustmentsTest {
  }

  @Nested
  class BatchVoucherExportConfigTestNested extends BatchVoucherExportConfigTest {
  }

  @Nested
  class BatchVoucherExportConfigCredentialsTestNested extends BatchVoucherExportConfigCredentialsTest {
  }

  @Nested
  class BatchGroupsApiTestNested extends BatchGroupsApiTest {
  }

  @Nested
  class BatchVoucherSchemaXSDTestNested extends BatchVoucherSchemaXSDTest {
  }

  @Nested
  class BatchVoucherImplTestNested extends BatchVoucherImplTest {
  }

  @Nested
  class BatchedVoucherLinesModelConverterTestNested extends BatchedVoucherLinesModelConverterTest {
  }

  @Nested
  class BatchedVoucherModelConverterTestNested extends BatchedVoucherModelConverterTest {
  }

  @Nested
  class BatchVoucherModelConverterTestNested extends BatchVoucherModelConverterTest {
  }

  @Nested
  class DefaultJAXBRootElementNameResolverTestNested extends DefaultJAXBRootElementNameResolverTest {
  }

  @Nested
  class JAXBUtilTestNested extends JAXBUtilTest {
  }

  @Nested
  class XMLConverterTestNested extends XMLConverterTest {
  }

}
