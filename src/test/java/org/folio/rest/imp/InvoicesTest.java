package org.folio.rest.imp;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class InvoicesTest {
  private static final Logger logger = LoggerFactory.getLogger(InvoicesTest.class);

  public static final String BASE_MOCK_DATA_PATH = "mockdata/";

  private static final String INVOICE_ID_PATH = "/invoice/invoices/{id}";
  private static final String INVOICE_LINE_ID_PATH = "/invoice/invoice-lines/{id}";
  private static final String INVOICE_PATH = "/invoice/invoices";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_NUMBER_PATH = "/invoice/invoice-number";
  private static final String INVOICE_NUMBER_VALIDATE_PATH = "/invoice/invoice-number/validate";
  private static final String INVOICE_PATH_BAD = "/invoice/bad";
  private static final String INVOICE_SAMPLE_PATH = "invoice.json";
  private static final String INVOICE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoices.json";
  private static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoice_lines.json";
  private static final String INVOICE_LINE_SAMPLE_PATH = "invoice_line.json";
  private static final String ID = "id";
  private static final String UUID = "8d3881f6-dd93-46f0-b29d-1c36bdb5c9f9";
  private static final String EXIST_CONFIG_TENANT_LIMIT_10 = "test_diku_limit_10";
  private static final String BAD_QUERY = "unprocessableQuery";
  private static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  private static final String BAD_INVOICE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String TENANT_NAME = "diku";
  private static final String QUERY_PARAM_NAME = "query";
  private static final String EXISTING_VENDOR_INV_NO = "existingVendorInvoiceNo";
  private static final String VENDOR_INVOICE_NUMBER_FIELD = "vendorInvoiceNo";

  private static final int mockPort = NetworkUtils.nextFreePort();
  private static final int okapiPort = NetworkUtils.nextFreePort();

  private static Vertx vertx;
  private static MockServer mockServer;

  private static final Header EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10 = new Header(OKAPI_HEADER_TENANT, EXIST_CONFIG_TENANT_LIMIT_10);
  private static final Header X_OKAPI_URL = new Header("X-Okapi-Url", "http://localhost:" + mockPort);
  static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  @BeforeClass
  public static void setUpOnce(TestContext context) {
    vertx = Vertx.vertx();

    mockServer = new MockServer(mockPort);
    mockServer.start(context);

    RestAssured.baseURI = "http://localhost:" + okapiPort;
    RestAssured.port = okapiPort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    final JsonObject conf = new JsonObject();
    conf.put("http.port", okapiPort);

    final DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @AfterClass
  public static void tearDownOnce(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    mockServer.close();
  }

  @Before
  public void setUp() {
    MockServer.serverRqRs.clear();
  }

  @Test
  public void getInvoicingInvoicesTest() throws MalformedURLException {
    logger.info("=== Test Get Invoices by without query - get 200 by successful retrieval of invoices ===");
    final Response resp = RestAssured
      .with()
       .header(X_OKAPI_URL)
       .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
     .get(INVOICE_PATH)
       .then()
         .statusCode(200)
         .extract()
         .response();

    assertEquals(3, resp.getBody().as(InvoiceCollection.class).getTotalRecords().intValue());
  }

  @Test
  public void getInvoicingInvoicesWithQueryParamTest() throws MalformedURLException {
    logger.info("=== Test Get Invoices with query - get 200 by successful retrieval of invoices by query ===");

    final Response resp = RestAssured
      .with()
       .header(X_OKAPI_URL)
       .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
       .param(QUERY_PARAM_NAME, VENDOR_INVOICE_NUMBER_FIELD + "==" + EXISTING_VENDOR_INV_NO)
     .get(INVOICE_PATH)
       .then()
         .statusCode(200)
         .extract()
         .response();

  	assertEquals(1, resp.getBody().as(InvoiceCollection.class).getTotalRecords().intValue());
  }

  @Test
  public void testGetInvoicesBadQuery() {
    logger.info("=== Test Get Invoices by query - unprocessable query to emulate 400 from storage ===");
    RestAssured
      .with()
        .header(X_OKAPI_URL)
        .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
        .param(QUERY_PARAM_NAME, BAD_QUERY)
      .get(INVOICE_PATH)
        .then()
          .statusCode(400);
  }

  @Test
  public void testGetInvoicesInternalServerError() {
    logger.info("=== Test Get Invoices by query - emulating 500 from storage ===");
    RestAssured
      .with()
        .header(X_OKAPI_URL)
        .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
        .param(QUERY_PARAM_NAME, ID_FOR_INTERNAL_SERVER_ERROR)
      .get(INVOICE_PATH)
        .then()
          .statusCode(500);
  }

  @Test
  public void getInvoicingInvoicesBadRequestUrlTest() throws MalformedURLException {
    logger.info("=== Test Get Invoices by query - emulating 400 by sending bad request Url ===");
    RestAssured
      .with()
        .header(X_OKAPI_URL)
          .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
        .get(INVOICE_PATH_BAD)
        .then()
          .statusCode(400);
  }

  @Test
  public void getInvoicingInvoiceLinesTest() throws MalformedURLException {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(INVOICE_LINES_PATH)
        .then()
          .statusCode(500);
  }

  @Test
  public void getInvoicingInvoiceNumberTest() throws MalformedURLException {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(INVOICE_NUMBER_PATH)
        .then()
          .statusCode(500);
  }

  @Test
  public void testGetInvoicingInvoicesById() throws IOException {
    logger.info("=== Test Get Invoice By Id ===");

    JsonObject invoicesList = new JsonObject(getMockData(INVOICE_MOCK_DATA_PATH));
    String id = invoicesList.getJsonArray("invoices").getJsonObject(0).getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", INVOICE_MOCK_DATA_PATH, id));

    final Invoice resp = RestAssured
        .with()
        .header(X_OKAPI_URL)
        .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
        .get(INVOICE_PATH + "/" + id)
         .then()
           .statusCode(200)
           .extract()
           .response()
           .as(Invoice.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void testGetInvoicingInvoicesByIdNotFound() throws MalformedURLException {
    logger.info("=== Test Get Invoices by Id - 404 Not found ===");

    String id = BAD_INVOICE_ID;

    final Response resp = RestAssured
      .with()
      .header(X_OKAPI_URL)
      .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
      .get(INVOICE_PATH + "/" + BAD_INVOICE_ID)
        .then()
          .statusCode(404)
          .extract()
          .response();

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(id, actual);
  }

  @Test
  public void testGetInvoicingInvoiceLinesById() throws Exception {
    JsonObject invoicesList = new JsonObject(getMockData(INVOICE_LINES_MOCK_DATA_PATH));
    String id = invoicesList.getJsonArray("invoice_lines").getJsonObject(0).getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", INVOICE_LINES_MOCK_DATA_PATH, id));

    final InvoiceLine resp = RestAssured
      .with()
      .header(X_OKAPI_URL)
      .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
      .get(INVOICE_LINES_PATH + "/" + id)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .as(InvoiceLine.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void testGetInvoicingInvoiceLinesByIdNotFound() throws MalformedURLException {
    logger.info("=== Test Get Invoice lines by Id - 404 Not found ===");

    final Response resp = RestAssured
      .with()
      .header(X_OKAPI_URL)
      .header(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10)
      .get(INVOICE_LINES_PATH + "/" + BAD_INVOICE_ID)
      .then()
      .statusCode(404)
      .extract()
      .response();

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_INVOICE_ID, actual);
  }

  @Test
  public void testPutInvoicingInvoicesById() throws Exception {
    Invoice reqData = getMockDraftInvoice().mapTo(Invoice.class);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    given()
      .pathParam(ID, reqData.getId())
      .body(jsonBody)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .put(INVOICE_ID_PATH)
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
      .put(INVOICE_LINE_ID_PATH)
        .then()
          .statusCode(500);
  }

  @Test
  public void deleteInvoicingInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .delete(INVOICE_ID_PATH)
        .then()
          .statusCode(500);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .delete(INVOICE_LINE_ID_PATH)
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
      .post(INVOICE_PATH)
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
      .post(INVOICE_NUMBER_VALIDATE_PATH)
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
      .post(INVOICE_LINES_PATH)
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
