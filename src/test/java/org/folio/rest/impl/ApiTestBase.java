package org.folio.rest.impl;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.ApiTestSuite;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.Envs;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.utils.UserPermissionsUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class ApiTestBase {

  public static final String VALID_UUID = "8d3881f6-dd93-46f0-b29d-1c36bdb5c9f9";
  public static final String APPROVED_INVOICE_ID = "c0d08448-347b-418a-8c2f-5fb50248d67e";
  public static final String APPROVED_INVOICE_WITHOUT_FISCAL_YEAR_ID = "bf4ace65-e35c-4e7a-92e1-b9f59921b13e";
  public static final String APPROVED_INVOICE_MULTIPLE_ADJUSTMENTS_FISCAL_YEAR_ID = "ee210bec-b2e8-4e65-8ab3-a66f918fbf32";
  public static final String APPROVED_INVOICE_ID_WITHOUT_FISCAL_YEAR = "ff634ec9-67fd-4239-9f2b-91d15bb2a996";
  public static final String PAID_INVOICE_ID = "c15a6442-ba7d-4198-acf1-940ba99e6929";
  public static final String ID_BAD_FORMAT = "123-45-678-90-abc";
  public static final String FOLIO_INVOICE_NUMBER_VALUE = "228D126";
  public static final Header X_OKAPI_URL = new Header(OKAPI_URL, "http://localhost:" + mockPort);
  public static final Header X_OKAPI_TOKEN = new Header(OKAPI_HEADER_TOKEN, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6Ijg3MTIyM2Q1LTYxZDEtNWRiZi1hYTcxLWVhNTcwOTc5MTQ1NSIsImlhdCI6MTU4NjUyMDA0NywidGVuYW50IjoiZGlrdSJ9._qlH5LDM_FaTH8MxIHKua-zsLmrBY7vpcJ-WrGupbHM");
  public static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoiceimpltest");
  public static final Header X_OKAPI_USER_ID = new Header(OKAPI_USERID_HEADER, "d1d0a10b-c563-4c4b-ae22-e5a0c11623eb");
  public static final String PROTECTED_READ_ONLY_TENANT = "protected_read";
  public static final Header X_OKAPI_PROTECTED_READ_TENANT = new Header(OKAPI_HEADER_TENANT, PROTECTED_READ_ONLY_TENANT);
  public static final String USER_ID_ASSIGNED_TO_ACQ_UNITS = "480dba68-ee84-4b9c-a374-7e824fc49227";
  public static final Header X_OKAPI_USER_ID_WITH_ACQ_UNITS = new Header(OKAPI_USERID_HEADER, USER_ID_ASSIGNED_TO_ACQ_UNITS);
  public static final Header ACCEPT_JSON_HEADER = new Header(HttpHeaders.ACCEPT, APPLICATION_JSON);
  public static final Header ACCEPT_XML_HEADER = new Header(HttpHeaders.ACCEPT, APPLICATION_XML);
  public static final String BAD_QUERY = "unprocessableQuery";
  public static final String ID = "id";
  public static final String ID_DOES_NOT_EXIST = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";

  public static final String BASE_MOCK_DATA_PATH = "mockdata/";
  static final String INVOICE_LINE_NUMBER_VALUE = "1";
  static final String VOUCHER_NUMBER_VALUE = "1";
  static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  static final String ID_FOR_INTERNAL_SERVER_ERROR_PUT = "bad500bb-bbbb-500b-bbbb-bbbbbbbbbbbb";
  static final int READY_MESSAGE_TIMES = 2;
  static final int STARTUP_ATTEMPTS = 3;

  public static final String MIN_INVOICE_ID = UUID.randomUUID().toString();

  private static final Logger logger = LogManager.getLogger(ApiTestBase.class);


  private static boolean runningOnOwn;

  public static final List<String> permissionsList = Arrays.asList(
    "invoice.item.approve.execute",
    "invoice.item.pay.execute",
    "invoice.invoices.item.put",
    "invoices.acquisitions-units-assignments.manage",
    "invoices.acquisitions-units-assignments.assign",
    "invoices.fiscal-year.update.execute"
  );

  public static final JsonArray permissionsArray = new JsonArray(permissionsList);
  public static final String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
  public static final Header X_OKAPI_PERMISSION = new Header(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

  public static final List<String> permissionsWithoutApproveAndPayList = Arrays.asList(
    "invoice.invoices.item.put",
    "invoices.acquisitions-units-assignments.manage",
    "invoices.acquisitions-units-assignments.assign",
    "invoices.fiscal-year.update.execute"
  );

  public static final JsonArray permissionsWithoutApproveAndPayArray = new JsonArray(permissionsWithoutApproveAndPayList);
  public static final String permissionsWithoutApproveAndPayJsonArrayString = new JsonArray(permissionsWithoutApproveAndPayList).encode();
  public static final Header X_OKAPI_PERMISSION_WITHOUT_PAY_APPROVE = new Header(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsWithoutApproveAndPayJsonArrayString);

  public static final List<String> permissionsWithoutPaidList = Arrays.asList(
    "invoice.item.approve.execute",
    "invoice.invoices.item.put",
    "invoices.acquisitions-units-assignments.manage",
    "invoices.acquisitions-units-assignments.assign",
    "invoices.fiscal-year.update.execute"
  );

  public static final JsonArray permissionsWithoutPaidArray = new JsonArray(permissionsWithoutPaidList);
  public static final String permissionsWithoutPaidJsonArrayString = new JsonArray(permissionsWithoutPaidList).encode();
  public static final Header X_OKAPI_PERMISSION_WITHOUT_PAY = new Header(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsWithoutPaidJsonArrayString);

  public static final String POSTGRES_IMAGE = "postgres:16-alpine";
  private static PostgreSQLContainer<?> postgresSQLContainer;

  private static final String INVOICES_TABLE = "records_invoices";
  protected static final String TOKEN = "token";
  private static final String HTTP_PORT = "http.port";
  private static int port;
  private static String useExternalDatabase;
  protected static Vertx vertx;
  protected static final String TENANT_ID = "diku";

  public static final String OKAPI_URL_ENV = "OKAPI_URL";

  @BeforeAll
  public static void before(final VertxTestContext context) throws Exception {

    if (ApiTestSuite.isNotInitialised()) {
      logger.info("Running test on own, initialising suite manually");
      runningOnOwn = true;
      ApiTestSuite.before();
    }
    vertx = Vertx.vertx();
    runDatabase();
    deployVerticle(context);
  }

  @BeforeEach
  public void setUp(final VertxTestContext context) {
    clearServiceInteractions();
    clearTable(context);
  }

  protected void clearServiceInteractions() {
    MockServer.release();
  }

  @AfterAll
  public static void after(final VertxTestContext context) {
    if (runningOnOwn) {
      if ("embedded".equals(useExternalDatabase)) {
        PostgresClient.stopPostgresTester();
        postgresSQLContainer.stop();
      }
      logger.info("Running test on own, un-initialising suite manually");
      ApiTestSuite.after(context);
    } else {
      context.completeNow();
    }
  }

  private static void runDatabase() throws Exception {
    PostgresClient.stopPostgresTester();
    PostgresClient.closeAllClients();
    useExternalDatabase = System.getProperty(
      "org.folio.invoice.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment" -> System.out.println("Using environment settings");
      case "external" -> {
        String postgresConfigPath = System.getProperty(
          "org.folio.invoice.test.config",
          "/postgres-conf-local.json");
        PostgresClient.setConfigFilePath(postgresConfigPath);
      }
      case "embedded" -> {
        postgresSQLContainer = new PostgreSQLContainer<>(POSTGRES_IMAGE)
          .withStartupAttempts(STARTUP_ATTEMPTS)
          .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", READY_MESSAGE_TIMES));
        postgresSQLContainer.start();
        Envs.setEnv(
          postgresSQLContainer.getHost(),
          postgresSQLContainer.getFirstMappedPort(),
          postgresSQLContainer.getUsername(),
          postgresSQLContainer.getPassword(),
          postgresSQLContainer.getDatabaseName()
        );
      }
      default -> {
        String message = "No understood database choice made." +
          "Please set org.folio.invoice.test.database" +
          "to 'external', 'environment' or 'embedded'";
        throw new Exception(message);
      }
    }
  }

  public static String constructModuleName() {
    return ModuleName.getModuleName().replace("_", "-") + "-" + ModuleName.getModuleVersion();
  }

  private static void deployVerticle(final VertxTestContext testContext) {
    port = NetworkUtils.nextFreePort();
    String okapiUrl = "http://localhost:" + port;
    final DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put(HTTP_PORT, port));
    vertx.deployVerticle(RestVerticle.class.getName(), options).onComplete(deployVerticleAr -> {
      if (deployVerticleAr.failed()) {
        testContext.failNow(deployVerticleAr.cause());
        return;
      }

      TenantAPI tenantAPI = new TenantAPI();
      TenantAttributes tenantAttributes = new TenantAttributes();
      tenantAttributes.setModuleTo(constructModuleName());
      Map<String, String> okapiHeaders = new HashMap<>();
      okapiHeaders.put(OKAPI_HEADER_TOKEN, TOKEN);
      okapiHeaders.put(OKAPI_URL_ENV, okapiUrl);
      okapiHeaders.put(OKAPI_HEADER_TENANT, TENANT_ID);

      Future<javax.ws.rs.core.Response> future = Future.future(promise ->
        tenantAPI.postTenant(tenantAttributes, okapiHeaders, promise, vertx.getOrCreateContext()));

      future.onComplete(postResult -> {
        if (postResult.failed()) {
          testContext.failNow(postResult.cause());
          return;
        }
        testContext.verify(() -> {
          javax.ws.rs.core.Response result = postResult.result();
          if (result.getStatus() == 204) {
            testContext.completeNow();
          } else if (result.getStatus() == 201) {
            Future<javax.ws.rs.core.Response> future2 =
              Future.future(promise -> tenantAPI.getTenantByOperationId(((TenantJob)result.getEntity()).getId(), 60000,
                okapiHeaders, promise, vertx.getOrCreateContext()));

            future2.onComplete(getResult -> testContext.verify(() -> {
              if (getResult.failed()) {
                testContext.failNow(getResult.cause());
                return;
              }
              TenantJob tenantJob = (TenantJob) getResult.result().getEntity();
              assertTrue(tenantJob.getComplete(), "Tenant job should be complete.");

              String error = tenantJob.getError();
              if (error != null) {
                assertTrue(error.contains("EventDescriptor was not registered for eventType"), "Error message should contain expected text.");
              }
              testContext.completeNow();
            }));
          } else {
            JsonObject response = JsonObject.mapFrom(result.getEntity());
            if (response != null) {
              fail(format("Failed to make post tenant. Received status code: {}, response: {}", result.getStatus(), response.encodePrettily()));
            } else {
              fail(format("Failed to make post tenant. Received status code: {}", result.getStatus()));
            }
          }
        });
      });
    });
  }

  private static void clearTable(VertxTestContext context) {
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(INVOICES_TABLE, new Criterion(), event1 -> {
      if (event1.failed()) {
        context.failNow(event1.cause());
      }
      context.completeNow();
    });
  }


  public static String getMockData(String path) throws IOException {
    logger.info("Using mock datafile: {}", path);
    try (InputStream resourceAsStream = ApiTestBase.class.getClassLoader().getResourceAsStream(path)) {
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

  public JsonObject getMockAsJson(String fullPath) {
    try {
      return new JsonObject(getMockData(fullPath));
    } catch (IOException e) {
      logger.error("Failed to load mock data: {}", fullPath, e);
      Assertions.fail(e.getMessage());
    }
    return new JsonObject();
  }

  public Response verifySuccessPost(String url, Object body, Header... headersArr) {
    Headers headers = headersArr.length == 0 ? prepareHeaders(X_OKAPI_TENANT) : prepareHeaders(headersArr);
    return verifyPostResponse(url, body, headers, APPLICATION_JSON, 201);
  }

  public Response verifyPostResponse(String url, Object body, Headers headers, String expectedContentType, int expectedCode) {
    Response response = RestAssured
      .with()
      .header(X_OKAPI_URL)
      .header(X_OKAPI_TOKEN)
      .headers(headers)
      .contentType(APPLICATION_JSON)
      .body(convertToString(body))
      .post(url)
      .then()
      .log()
      .all()
      .statusCode(expectedCode)
      .contentType(expectedContentType)
      .extract()
      .response();
    // sleep needed to avoid some issues with async processing - otherwise some tests start running in parallel and fail randomly (see MODINVOICE-265)
    // FIXME : apply async approach
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return response;
  }

  Response verifySuccessPut(String url, Object body) {
    return verifyPut(url, body, "", 204);
  }

  Response verifyPut(String url, Object body, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN);
    return verifyPut(url, body, headers, expectedContentType, expectedCode);
  }

  public Response verifyPut(String url, Object body, Headers headers, String expectedContentType, int expectedCode) {
    return RestAssured
      .with()
      .headers(headers)
      .header(X_OKAPI_URL)
      .body(convertToString(body))
      .contentType(APPLICATION_JSON)
      .put(url)
      .then()
      .statusCode(expectedCode)
      .contentType(expectedContentType)
      .extract()
      .response();
  }

  Response verifyGet(String url, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(X_OKAPI_TENANT);
    return verifyGet(url, headers, expectedContentType, expectedCode);
  }

  public Response verifyGet(String url, Headers headers, String expectedContentType, int expectedCode) {
    return RestAssured
      .with()
      .headers(headers)
      .header(X_OKAPI_URL)
      .get(url)
      .then()
      .log().all()
      .statusCode(expectedCode)
      .contentType(expectedContentType)
      .extract()
      .response();
  }

  <T> T verifySuccessGet(String url, Class<T> clazz) {
    return verifyGet(url, APPLICATION_JSON, 200).as(clazz);
  }

  <T> T verifySuccessGet(String url, Class<T> clazz, Header header) {
    return verifyGet(url, prepareHeaders(header), APPLICATION_JSON, 200).as(clazz);
  }

  Response verifyDeleteResponse(String url, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(X_OKAPI_TENANT);
    return verifyDeleteResponse(url, headers, expectedContentType, expectedCode);
  }

  public Response verifyDeleteResponse(String url, Headers headers, String expectedContentType, int expectedCode) {
    return RestAssured
      .with()
      .headers(headers)
      .header(X_OKAPI_URL)
      .delete(url)
      .then()
      .statusCode(expectedCode)
      .contentType(expectedContentType)
      .extract()
      .response();
  }

  public Headers prepareHeaders(Header... headers) {

    Header permissionsHeader = new Header(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Header[] updatedHeaders = Arrays.copyOf(headers, headers.length + 1);
    updatedHeaders[headers.length] = permissionsHeader;

    return new Headers(updatedHeaders);
  }

  public Headers prepareHeadersWithoutPermissions(Header... headers) {
    return new Headers(headers);
  }

  @SuppressWarnings("unchecked")
  <T> T copyObject(T object) {
    return JsonObject.mapFrom(object)
      .mapTo((Class<T>) object.getClass());
  }

  String convertToString(Object body) {
    if (body instanceof String) {
      return body.toString();
    } else if (body instanceof JsonObject) {
      return ((JsonObject) body).encodePrettily();
    } else {
      return JsonObject.mapFrom(body).encodePrettily();
    }
  }

  public static Invoice getMinimalContentInvoice() {
    return getMinimalContentInvoice(Invoice.Status.OPEN);
  }

  public static Invoice getMinimalContentInvoice(Invoice.Status invoiceStatus) {
    return new Invoice()
      .withId(MIN_INVOICE_ID)
      .withAccountingCode("CODE")
      .withCurrency("EUR")
      .withExportToAccounting(false)
      .withInvoiceDate(new Date())
      .withPaymentMethod("Cash")
      .withStatus(invoiceStatus)
      .withSource(Invoice.Source.API)
      .withVendorInvoiceNo("vendorNumber")
      .withVendorId(UUID.randomUUID().toString())
      .withTotal(0.0)
      .withSubTotal(0.0)
      .withAdjustmentsTotal(0.0);
  }

  public static InvoiceLine getMinimalContentInvoiceLine() {
    return getMinimalContentInvoiceLine(MIN_INVOICE_ID);
  }

  public static InvoiceLine getMinimalContentInvoiceLine(String invoiceId) {
    return new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withDescription("Test line")
      .withInvoiceId(invoiceId)
      .withInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.OPEN)
      .withSubTotal(1.0)
      .withQuantity(1)
      .withAdjustmentsTotal(0.0)
      .withTotal(1.0)
      .withReleaseEncumbrance(true);
  }

  public static FundDistribution getFundDistribution() {
    return new FundDistribution()
      .withCode("USHIST")
      .withFundId("1d1574f1-9196-4a57-8d1f-3b2e4309eb81")
      .withEncumbrance("388c0f7b-9fff-451c-b300-6509e443bef5")
      .withExpenseClassId("6e6ed3c9-d959-4c91-a436-6076fb373816")
      .withDistributionType(PERCENTAGE)
      .withValue(100.0);
  }

  public static InvoiceLine getMinimalContentInvoiceLineWithZeroAmount(String invoiceId) {
    return new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withDescription("Test line")
      .withInvoiceId(invoiceId)
      .withInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.OPEN)
      .withSubTotal(0.0)
      .withQuantity(1)
      .withAdjustmentsTotal(0.0)
      .withTotal(0.0)
      .withReleaseEncumbrance(true);
  }

  protected static Adjustment createAdjustment(Adjustment.Prorate prorate, Adjustment.Type type, double value) {
    return new Adjustment()
      .withId(prorate != Adjustment.Prorate.NOT_PRORATED ? UUID.randomUUID().toString() : null)
      .withDescription("Test")
      .withProrate(prorate)
      .withType(type)
      .withValue(value);
  }


  public void assertAllFieldsExistAndEqual(JsonObject sample, Response response) {
    JsonObject sampleJson = JsonObject.mapFrom(sample.mapTo(BatchVoucher.class));
    JsonObject responseJson = JsonObject.mapFrom(response.then().extract().as(BatchVoucher.class));
    testAllFieldsExists(responseJson, sampleJson);
  }

  public void testAllFieldsExists(JsonObject extracted, JsonObject sampleObject) {
    sampleObject.remove("id");
    Set<String> fieldsNames = sampleObject.fieldNames();
    for (String fieldName : fieldsNames) {
      Object sampleField = sampleObject.getValue(fieldName);
      if (sampleField instanceof JsonObject) {
        testAllFieldsExists((JsonObject) sampleField, (JsonObject) extracted.getValue(fieldName));
      } else {
        Assertions.assertEquals(sampleObject.getValue(fieldName).toString(), extracted.getValue(fieldName).toString());
      }
    }
  }
}
