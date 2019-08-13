package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.apache.commons.io.IOUtils;
import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.invoices.utils.HelperUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.awaitility.Awaitility.await;
import static org.folio.invoices.utils.HelperUtils.INVOICE;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.impl.ApiTestSuite.mockPort;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

public class ApiTestBase {

  public static final String VALID_UUID = "8d3881f6-dd93-46f0-b29d-1c36bdb5c9f9";
  static final String ID_BAD_FORMAT = "123-45-678-90-abc";
  static final String FOLIO_INVOICE_NUMBER_VALUE = "228D126";
  public static final Header X_OKAPI_URL = new Header(OKAPI_URL, "http://localhost:" + mockPort);
  static final Header X_OKAPI_TOKEN = new Header(OKAPI_HEADER_TOKEN, "eyJhbGciOiJIUzI1NiJ9");
  static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoiceimpltest");
  static final Header X_OKAPI_USERID = new Header(OKAPI_USERID_HEADER, "d1d0a10b-c563-4c4b-ae22-e5a0c11623eb");

  static final String BASE_MOCK_DATA_PATH = "mockdata/";

  static final String INVOICE_LINE_NUMBER_VALUE = "1";
  static final String VOUCHER_NUMBER_VALUE = "1";
  static final String LANG_PARAM = "lang";
  static final String INVALID_LANG = "english";

  public static final String ID_DOES_NOT_EXIST = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  static final String ID_FOR_INTERNAL_SERVER_ERROR_PUT = "bad500bb-bbbb-500b-bbbb-bbbbbbbbbbbb";

  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");
  }

  private static final Logger logger = LoggerFactory.getLogger(ApiTestBase.class);


  private static boolean runningOnOwn;

  // The variable is defined in main thread but the value is going to be inserted in vert.x event loop thread
  private static volatile List<Message<JsonObject>> eventMessages = new ArrayList<>();

  /**
   * Define unit test specific beans to override actual ones
   */
  @Configuration
  static class ContextConfiguration {

    @Bean("invoiceSummaryHandler")
    @Primary
    public Handler<Message<JsonObject>> mockedInvoiceSummaryHandler() {
      // As an implementation just add received message to list
      return message -> {
        logger.info("New message sent to {} address: {}", message.address(), message.body());
        eventMessages.add(message);
      };
    }
  }

  @BeforeClass
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {

    if(ApiTestSuite.isNotInitialised()) {
      logger.info("Running test on own, initialising suite manually");
      runningOnOwn = true;
      ApiTestSuite.before();
    }
  }

  @Before
  public void setUp() {
    clearServiceInteractions();
  }

  protected void clearServiceInteractions() {
    eventMessages.clear();
    MockServer.serverRqRs.clear();
  }

  @AfterClass
  public static void after() {

    if(runningOnOwn) {
      logger.info("Running test on own, un-initialising suite manually");
      ApiTestSuite.after();
    }
  }

  static String getMockData(String path) throws IOException {
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
      fail(e.getMessage());
    }
    return new JsonObject();
  }

  Response verifyPostResponse(String url, JsonObject body, Headers headers, String expectedContentType, int expectedCode) {
    return verifyPostResponse(url, body.encode(), headers, expectedContentType, expectedCode);
  }

  Response verifyPostResponse(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
    Response response = RestAssured
      .with()
        .header(X_OKAPI_URL)
        .header(X_OKAPI_TOKEN)
        .headers(headers)
        .contentType(APPLICATION_JSON)
        .body(body)
      .post(url)
        .then()
          .log()
          .all()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
          .extract()
            .response();

    int msgQty = (201 == expectedCode && url.startsWith(INVOICE_LINES_PATH)) ? 1 : 0;
    verifyInvoiceSummaryUpdateEvent(msgQty);

    return response;
  }

  Response verifyPut(String url, String body, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN);
    return verifyPut(url, body, headers,expectedContentType, expectedCode);
  }

  Response verifyPut(String url, JsonObject body, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN);
    return verifyPut(url, body.encode(), headers,expectedContentType, expectedCode);
  }

  Response verifyPut(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
    return RestAssured
      .with()
        .headers(headers)
        .header(X_OKAPI_URL)
        .body(body)
        .contentType(APPLICATION_JSON)
      .put(url)
        .then()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
          .extract()
            .response();
  }

  Response verifyGet(String url, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT);
    return verifyGet(url, headers, expectedContentType, expectedCode);
  }

  Response verifyGet(String url, Headers headers, String expectedContentType, int expectedCode) {
    Response response = RestAssured
      .with()
        .headers(headers)
      .get(url)
        .then()
        .log().all()
        .statusCode(expectedCode)
        .contentType(expectedContentType)
        .extract()
          .response();

    if (!url.startsWith(INVOICE_LINES_PATH)) {
      verifyInvoiceSummaryUpdateEvent(0);
    }

    return response;
  }

  <T> T verifySuccessGet(String url, Class<T> clazz) {
    return verifyGet(url, APPLICATION_JSON, 200).as(clazz);
  }

  Response verifyDeleteResponse(String url, String expectedContentType, int expectedCode) {
    Headers headers =  prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT);
    return verifyDeleteResponse(url, headers, expectedContentType, expectedCode);
  }

  Response verifyDeleteResponse(String url, Headers headers, String expectedContentType, int expectedCode) {
    Response response = RestAssured
      .with()
        .headers(headers)
      .delete(url)
        .then()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
          .extract()
            .response();

    int msgQty = (204 == expectedCode && url.startsWith(INVOICE_LINES_PATH)) ? 1 : 0;
    verifyInvoiceSummaryUpdateEvent(msgQty);

    return response;
  }

  Headers prepareHeaders(Header... headers) {
    return new Headers(headers);
  }

  void verifyInvoiceSummaryUpdateEvent(int msgQty) {
    logger.debug("Verifying event bus messages");
    // Wait until event bus registers message
    await().atLeast(50, MILLISECONDS)
      .atMost(1, SECONDS)
      .until(() -> eventMessages, hasSize(msgQty));
    for (int i = 0; i < msgQty; i++) {
      Message<JsonObject> message = eventMessages.get(i);
      assertThat(message.address(), equalTo(MessageAddress.INVOICE_TOTALS.address));
      assertThat(message.headers(), not(emptyIterable()));

      JsonObject body = message.body();
      assertThat(body, notNullValue());
      assertThat(body.getString(HelperUtils.LANG), not(isEmptyOrNullString()));
      if (body.containsKey(INVOICE_ID)) {
        assertThat(body.getString(INVOICE_ID), not(isEmptyOrNullString()));
      } else {
        assertThat(body.getJsonObject(INVOICE), notNullValue());
      }
    }
  }

}
