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
import org.folio.rest.jaxrs.model.InvoiceLine;
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
  private static final String INVOICE_LINE_ID_PATH = "/invoicing/invoice-lines/{id}";
  private static final String INVOICE_PATH = "/invoicing/invoices";
  private static final String INVOICE_LINES_PATH = "/invoicing/invoice-lines";
  private static final String INVOICE_NUMBER_PATH = "/invoicing/invoice-number";
  private static final String INVOICE_NUMBER_VALIDATE_PATH = "/invoicing/invoice-number/validate";
  private static final String INVOICE_SAMPLE_PATH = "invoice.json";
  private static final String INVOICE_LINE_SAMPLE_PATH = "invoice_line.json";
  private static final String ID = "id";
  private static final String UUID = "8d3881f6-dd93-46f0-b29d-1c36bdb5c9f9";

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
  public void getInvoicingInvoicesTest() throws MalformedURLException {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(INVOICE_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void getInvoicingInvoiceLinesTest() throws MalformedURLException {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(INVOICE_LINES_PATH))
        .then()
          .statusCode(500);
  }
  
  @Test
  public void getInvoicingInvoiceNumberTest() throws MalformedURLException {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(INVOICE_NUMBER_PATH))
        .then()
          .statusCode(500);
  }
  
  @Test
  public void getInvoicingInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void getInvoicingInvoiceLinesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(INVOICE_LINE_ID_PATH))
        .then()
          .statusCode(500);
  }
  
  @Test
  public void putInvoicingInvoicesByIdTest() throws Exception {
  	Invoice reqData = getMockDraftInvoice().mapTo(Invoice.class);
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
  public void putInvoicingInvoiceLinesByIdTest() throws Exception {
  	InvoiceLine reqData = getMockDraftInvoiceLine().mapTo(InvoiceLine.class);
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	
    given()
      .pathParam(ID, reqData.getId())
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .put(storageUrl(INVOICE_LINE_ID_PATH))
        .then()
          .statusCode(500);
  }
  
  @Test
  public void deleteInvoicingInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .delete(storageUrl(INVOICE_ID_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .delete(storageUrl(INVOICE_LINE_ID_PATH))
        .then()
          .statusCode(500);
  }
  
  @Test
  public void postInvoicingInvoicesTest() throws Exception {
  	Invoice reqData = getMockDraftInvoice().mapTo(Invoice.class);
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	
    given()
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .post(storageUrl(INVOICE_PATH))
        .then()
          .statusCode(500);
  }

  @Test
  public void postInvoicingInvoiceNumberValidateTest() throws Exception {
  	Invoice reqData = getMockDraftInvoice().mapTo(Invoice.class);
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	
    given()
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .post(storageUrl(INVOICE_NUMBER_VALIDATE_PATH))
        .then()
          .statusCode(500);
  }
  
  @Test
  public void postInvoicingInvoiceLinesTest() throws Exception {
  	InvoiceLine reqData = getMockDraftInvoiceLine().mapTo(InvoiceLine.class);
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	
    given()
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .post(storageUrl(INVOICE_LINES_PATH))
        .then()
          .statusCode(500);
  }
  
  private JsonObject getMockDraftInvoice() throws Exception {
    JsonObject invoice = new JsonObject(getMockData(INVOICE_SAMPLE_PATH));
    return invoice;
  }

  private JsonObject getMockDraftInvoiceLine() throws Exception {
    JsonObject invoiceLine = new JsonObject(getMockData(INVOICE_LINE_SAMPLE_PATH));
    return invoiceLine;
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
