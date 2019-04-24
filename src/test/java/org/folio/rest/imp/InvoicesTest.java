package org.folio.rest.imp;

import static io.restassured.RestAssured.given;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

@RunWith(VertxUnitRunner.class)
public class InvoicesTest {
  private static final Logger logger = LoggerFactory.getLogger(InvoicesTest.class);
  private static final String INVOICE_ID_PATH = "/invoice/invoices/{id}";
  private static final String INVOICE_LINE_ID_PATH = "/invoice/invoice-lines/{id}";
  private static final String INVOICE_PATH = "/invoice/invoices";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_NUMBER_PATH = "/invoice/invoice-number";
  private static final String INVOICE_NUMBER_VALIDATE_PATH = "/invoice/invoice-number/validate";
  private static final String INVOICE_SAMPLE_PATH = "invoice.json";
  private static final String INVOICE_LINE_SAMPLE_PATH = "invoice_line.json";
  private static final String ID = "id";
  private static final String UUID = "8d3881f6-dd93-46f0-b29d-1c36bdb5c9f9";

  private static final String EXIST_CONFIG_TENANT_LIMIT_10 = "test_diku_limit_10";
  private static final Header EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10 = new Header(OKAPI_HEADER_TENANT, EXIST_CONFIG_TENANT_LIMIT_10);
  private static final String BAD_QUERY = "unprocessableQuery";
  private static final String APPLICATION_JSON = "application/json";
  private static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  
  private static Vertx vertx;
  private static final String TENANT_NAME = "diku";
  static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);


  private static final int mockPort = NetworkUtils.nextFreePort();
  private static final int okapiPort = NetworkUtils.nextFreePort();
  private static MockServer mockServer;
  
  private static final Header X_OKAPI_URL = new Header("X-Okapi-Url", "http://localhost:" + mockPort);
  
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
  	Response resp = RestAssured.with()
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
  public void getInvoicingInvoicesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(INVOICE_ID_PATH)
        .then()
          .statusCode(500);
  }

  @Test
  public void getInvoicingInvoiceLinesByIdTest() throws MalformedURLException {
    given()
      .pathParam(ID, UUID)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(INVOICE_LINE_ID_PATH)
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
  
  public static class MockServer {
  	 private static final String TOTAL_RECORDS = "totalRecords";
     static Table<String, HttpMethod, List<JsonObject>> serverRqRs = HashBasedTable.create();
     private static final Logger logger = LoggerFactory.getLogger(MockServer.class);

     final int port;
     final Vertx vertx;
     
     MockServer(int port) {
       this.port = port;
       this.vertx = Vertx.vertx();
     }

     void start(TestContext context) {
       // Setup Mock Server...
       HttpServer server = vertx.createHttpServer();

       final Async async = context.async();
       server.requestHandler(defineRoutes()::accept).listen(port, result -> {
         if (result.failed()) {
           logger.warn(result.cause());
         }
         context.assertTrue(result.succeeded());
         async.complete();
       });
     }
     
     void close() {
       vertx.close(res -> {
         if (res.failed()) {
           logger.error("Failed to shut down mock server", res.cause());
           fail(res.cause().getMessage());
         } else {
           logger.info("Successfully shut down mock server");
         }
       });
     }
     
     Router defineRoutes() {
       Router router = Router.router(vertx);

       router.route().handler(BodyHandler.create());
       router.route(HttpMethod.GET, resourcesPath(INVOICES)).handler(this::handleGetInvoices);
       return router;
     }
     
     private void serverResponse(RoutingContext ctx, int statusCode, String contentType, String body) {
       ctx.response()
          .setStatusCode(statusCode)
          .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
          .end(body);
     }
     
     private void addServerRqRsData(HttpMethod method, String objName, JsonObject data) {
       List<JsonObject> entries = serverRqRs.get(objName, method);
       if (entries == null) {
         entries = new ArrayList<>();
       }
       entries.add(data);
       serverRqRs.put(objName, method, entries);
     }
     
     private void handleGetInvoices(RoutingContext ctx) {

       String queryParam = StringUtils.trimToEmpty(ctx.request().getParam("query"));
       if (queryParam.contains(BAD_QUERY)) {
         serverResponse(ctx, 400, APPLICATION_JSON, Status.BAD_REQUEST.getReasonPhrase());
       } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR)) {
         serverResponse(ctx, 500, APPLICATION_JSON, Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
       } else {
         JsonObject invoice = new JsonObject();
         addServerRqRsData(HttpMethod.GET, INVOICES, invoice);
         switch (queryParam) {
           case EMPTY:
          	 invoice.put(TOTAL_RECORDS, 3);
             break;
           default:
             //modify later as needed
          	 invoice.put(TOTAL_RECORDS, 0);
         }
         addServerRqRsData(HttpMethod.GET, INVOICES, invoice);
         serverResponse(ctx, 200, APPLICATION_JSON, invoice.encodePrettily());
       }
     }
  }
}
