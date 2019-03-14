package org.folio.rest.imp;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

public class InvoicesTest {
  private static final Logger logger = LoggerFactory.getLogger(InvoicesTest.class);
  private static final String INVOICE_ID_PATH = "/invoicing/invoices/{id}";
  private static final String INVOICE_PATH = "/invoicing/invoices";
  private static final String INVOICE_SAMPLE_PATH = "invoice.json";
  private static final String ID = "id";

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
      .get(storageUrl(INVOICE_PATH))
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
  public void putInvoicesByIdTest() throws Exception {
  	Invoice reqData = getMockDraftOrder().mapTo(Invoice.class);
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	
    given()
      .pathParam(ID, reqData.getId())
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .put(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void deleteInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, "1")
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .delete(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void postInvoicesTest() throws Exception {
  	Invoice reqData = getMockDraftOrder().mapTo(Invoice.class);
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	
    given()
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .post(storageUrl(INVOICE_PATH))
        .then()
          .statusCode(500);
  }

  private JsonObject getMockDraftOrder() throws Exception {
    JsonObject invoice = new JsonObject(getMockData(INVOICE_SAMPLE_PATH));
    return invoice;
  }

  public static String getMockData(String path) throws IOException {
    logger.info("Using mock datafile: {}", path);
    try (InputStream resourceAsStream = InvoicesTest.class.getClassLoader().getResourceAsStream(path)) {
      if (resourceAsStream != null) {
        return IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
      } else {
        StringBuilder sb = new StringBuilder();
        try (Stream<String> lines = Files.lines(Paths.get(path))) {
          lines.forEach(sb::append);
        }
        return sb.toString();
      }
    }
  }
}
