package org.folio.rest.imp;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

public class InvoicesTest {
  private static final Logger logger = LoggerFactory.getLogger(InvoicesTest.class);
  public static final String INVOICE_ID_PATH = "/invoices/{id}";

  private static Vertx vertx;
  private static int port = NetworkUtils.nextFreePort();
  private static final String TENANT_NAME = "diku";
  static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  @BeforeClass
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);


    vertx = Vertx.vertx();

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    options.setWorker(true);

    startVerticle(options);

  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    logger.info("Start verticle");

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if(res.succeeded()) {
        deploymentComplete.complete(res.result());
      }
      else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(60, TimeUnit.SECONDS);
  }

  public static URL storageUrl(String path) throws MalformedURLException {
    return new URL("http", "localhost", port, path);
  }

  @Test
  public void getInvoicesTest() throws MalformedURLException {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl("/invoices"))
        .then()
          .statusCode(500);
  }

  @Test
  public void getInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam("id", "1")
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void putInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam("id", "1")
      .body("{}")
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .put(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void deleteInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam("id", "1")
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .delete(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void postInvoicesTest() throws MalformedURLException {
    given()
      .body("{}")
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .post(storageUrl("/invoices"))
        .then()
          .statusCode(500);
  }

}
