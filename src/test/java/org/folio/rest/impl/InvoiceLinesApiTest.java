package org.folio.rest.impl;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.awaitility.Awaitility.await;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_INVOICE_LINE_CREATION;
import static org.folio.invoices.utils.HelperUtils.getNoAcqUnitCQL;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.InvoicesApiTest.APPROVED_INVOICE_ID;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_ID;
import static org.folio.rest.impl.InvoicesApiTest.REVIEWED_INVOICE_ID;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;
import static org.folio.rest.impl.MockServer.INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getAcqMembershipsSearches;
import static org.folio.rest.impl.MockServer.getAcqUnitsSearches;
import static org.folio.rest.impl.MockServer.getInvoiceLineCreations;
import static org.folio.rest.impl.MockServer.getInvoiceLineRetrievals;
import static org.folio.rest.impl.MockServer.getInvoiceLineUpdates;
import static org.folio.rest.impl.MockServer.getInvoiceRetrievals;
import static org.folio.rest.impl.MockServer.getQueryParams;
import static org.folio.rest.impl.ProtectionHelper.ACQUISITIONS_UNIT_IDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpStatus;
import org.folio.invoices.utils.InvoiceLineProtectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import io.restassured.response.Response;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class InvoiceLinesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceLinesApiTest.class);

  static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoiceLines/";
  static final String INVOICE_LINES_LIST_PATH = INVOICE_LINES_MOCK_DATA_PATH + "invoice_lines.json";
  public static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_LINE_ID_PATH = INVOICE_LINES_PATH + "/%s";


  private static final String INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH = INVOICE_LINES_MOCK_DATA_PATH + "29846620-8fb6-4433-b84e-0b6051eb76ec.json";


  static final String INVOICE_ID = "invoiceId";
  private static final String NULL = "null";

  private static final String INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID = "e0d08448-343b-118a-8c2f-4fb50248d672";
  private static final String INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID = "5cb6d270-a54c-4c38-b645-3ae7f249c606";
  private static final String INVOICE_LINE_WITH_INTERNAL_ERROR_ON_GET_INVOICE = "4051b42d-c6cf-4306-a331-209514af9877";
  private static final String INVOICE_LINE_OUTDATED_TOTAL = "55e4b6f5-f974-42da-9a77-24d4e8ef0e70";
  private static final String INVOICE_LINE_OUTDATED_TOTAL_PATH = INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_OUTDATED_TOTAL + ".json";
  static final String INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH = INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID + ".json";


  @Test
  public void getInvoicingInvoiceLinesTest() {
    verifyGet(INVOICE_LINES_PATH, prepareHeaders(X_OKAPI_PROTECTED_READ_TENANT), APPLICATION_JSON, 200);

    assertThat(getAcqUnitsSearches(), hasSize(1));
    assertThat(getAcqMembershipsSearches(), hasSize(1));

    List<String> queryParams = getQueryParams(INVOICE_LINES);
    assertThat(queryParams, hasSize(1));
    assertThat(queryParams.get(0), equalTo(getNoAcqUnitCQL(INVOICE_LINES)));
  }

  @Test
  public void testGetInvoiceLinesByInvoiceId() {
    logger.info("=== Test Get Invoice lines - by Invoice id ===");

    String sortBy = " sortBy subTotal";
    String cql = String.format("%s==%s", INVOICE_ID, APPROVED_INVOICE_ID);
    String endpointQuery = String.format("%s?query=%s%s", INVOICE_LINES_PATH, cql, sortBy);

    final InvoiceLineCollection invoiceLineCollection = verifySuccessGet(endpointQuery, InvoiceLineCollection.class, X_OKAPI_PROTECTED_READ_TENANT);

    assertThat(invoiceLineCollection.getTotalRecords(), is(3));

    assertThat(getAcqUnitsSearches(), hasSize(1));
    assertThat(getAcqMembershipsSearches(), hasSize(1));

    List<String> queryParams = getQueryParams(INVOICE_LINES);
    assertThat(queryParams, hasSize(1));
    String queryToStorage = queryParams.get(0);
    assertThat(queryToStorage, containsString("(" + cql + ")"));
    assertThat(queryToStorage, containsString(APPROVED_INVOICE_ID));
    assertThat(queryToStorage, not(containsString(ACQUISITIONS_UNIT_IDS + "=")));
    assertThat(queryToStorage, containsString(getNoAcqUnitCQL(INVOICE_LINES)));
    assertThat(queryToStorage, endsWith(sortBy));
  }

  @Test
  public void testGetInvoiceLinesInternalServerError() {
    logger.info("=== Test Get Invoice Lines by query - emulating 500 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_LINES_PATH, ID_FOR_INTERNAL_SERVER_ERROR);

    verifyGet(endpointQuery, APPLICATION_JSON, 500);
  }

  @Test
  public void testGetInvoiceLinesBadQuery() {
    logger.info("=== Test Get Invoice Lines by query - unprocessable query to emulate 400 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_LINES_PATH, BAD_QUERY);

    verifyGet(endpointQuery, APPLICATION_JSON, 400);

  }

  @Test
  public void getInvoicingInvoiceLinesByIdTest() {
    logger.info("=== Test Get Invoice line By Id ===");

    verifySuccessGetById(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID, false, false);
  }

  @Test
  public void testGetInvoicingInvoiceLinesByIdUpdateTotal() {
    logger.info("=== Test 200 when correct calculated invoice line total is returned without waiting to update in storage ===");

    final InvoiceLine resp = verifySuccessGetById(INVOICE_LINE_OUTDATED_TOTAL, true, true);

    Double expectedTotal = 4.62;
    assertThat(resp.getTotal(), equalTo(expectedTotal));
  }

  @Test
  public void testGetInvoicingInvoiceLinesByIdUpdateTotalException() {
    logger.info("=== Test 200 when correct calculated invoice line total is returned without waiting to update in storage ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_OUTDATED_TOTAL_PATH).mapTo(InvoiceLine.class);
    addMockEntry(INVOICE_LINES, invoiceLine.withId(ID_FOR_INTERNAL_SERVER_ERROR_PUT));

    // Check that invoice line update called which is expected to fail so invoice update is not triggered
    final InvoiceLine resp = verifySuccessGetById(invoiceLine.getId(), true, false);

    Double expectedTotal = 4.62;
    assertThat(resp.getTotal(), equalTo(expectedTotal));
  }

  @Test
  public void getInvoicingInvoiceLinesByIdNotFoundTest() {
    logger.info("=== Test Get Invoice line by Id - 404 Not found ===");

    final Response resp = verifyGet(INVOICE_LINES_PATH + "/" + ID_DOES_NOT_EXIST, APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(ID_DOES_NOT_EXIST, actual);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() {
    logger.info("=== Test delete invoice line by id ===");

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(new InvoiceLine().withId(VALID_UUID).withInvoiceId(VALID_UUID)));
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), "", 204);
  }

  @Test
  public void deleteInvoiceLinesByIdWithInvalidFormatTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, ID_BAD_FORMAT), TEXT_PLAIN, 400);
  }

  @Test
  public void deleteNotExistentInvoiceLinesTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, ID_DOES_NOT_EXIST), APPLICATION_JSON, 404);
  }

  @Test
  public void deleteInvoiceLinesInternalErrorOnStorageTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), APPLICATION_JSON, 500);
  }

  @Test
  public void deleteInvoiceLinesBadLanguageTest() {
    logger.info("=== Test to verify bad request error due to incorrect lang parameter value ===");
    String endpoint = String.format(INVOICE_LINE_ID_PATH, VALID_UUID) + String.format("?%s=%s", LANG_PARAM, INVALID_LANG) ;

    verifyDeleteResponse(endpoint, TEXT_PLAIN, 400);
  }

  @Test
  public void postInvoicingInvoiceLinesTest() {
    logger.info("=== Test create invoice line - 201 successfully created ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(OPEN_INVOICE_ID);
    Double actualTotal = 2.00d;
    assertThat(actualTotal, equalTo(reqData.getTotal()));

    String body = JsonObject.mapFrom(reqData).encodePrettily();
    final InvoiceLine respData = verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201)
      .as(InvoiceLine.class);

    String invoiceId = respData.getId();
    String InvoiceLineNo = respData.getInvoiceLineNumber();

    // MODINVOICE-86 Verify total is calculated upon invoice-line creation
    Double expectedTotal = 2.42d;
    assertThat(respData.getTotal(), equalTo(expectedTotal));

    assertThat(invoiceId, notNullValue());
    assertThat(InvoiceLineNo, notNullValue());
    assertThat(MockServer.serverRqRs.get(INVOICE_LINE_NUMBER, HttpMethod.GET), hasSize(1));

    compareRecordWithSentToStorage(respData);
  }

  @Test
  public void testAddInvoiceLineToApprovedInvoice() {
    logger.info("=== Test create invoice line for approved invoice ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(APPROVED_INVOICE_ID);
    String body = JsonObject.mapFrom(reqData).encodePrettily();
    final Errors errors = verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 500)
      .as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), is(PROHIBITED_INVOICE_LINE_CREATION.getDescription()));
    assertThat(error.getCode(), is(PROHIBITED_INVOICE_LINE_CREATION.getCode()));

  }

  @Test
  public void testPostInvoicingInvoiceLinesWithInvoiceLineNumberGenerationFail() throws IOException {
    logger.info("=== Test create invoice with error from storage on invoiceLineNo generation  ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(REVIEWED_INVOICE_ID);
    String body = JsonObject.mapFrom(reqData).encodePrettily();
    verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);
  }

  @Test
  public void testPostInvoiceLinesByIdLineWithoutId() throws IOException {
    logger.info("=== Test Post Invoice Lines By Id (empty id in body) ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(null);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Errors resp = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(MockServer.NON_EXIST_CONFIG_X_OKAPI_TENANT),
        APPLICATION_JSON, 422).as(Errors.class);

    assertEquals(1, resp.getErrors().size());
    assertEquals(INVOICE_ID, resp.getErrors().get(0).getParameters().get(0).getKey());
    assertEquals(NULL, resp.getErrors().get(0).getParameters().get(0).getValue());
  }

  @Test
  public void testPutInvoicingInvoiceLinesByIdTest() throws Exception {
    String reqData = getMockData(INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID + ".json");

    verifyPut(String.format(INVOICE_LINE_ID_PATH, INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID), reqData, "", 204);

    // No any total changed
    verifyInvoiceSummaryUpdateEvent(0);
  }

  @Test
  public void testPutInvoiceLineUpdateTotalAndInvoice() {
    logger.info("=== Test case when invoice line updates triggers also invoice update ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_OUTDATED_TOTAL_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setSubTotal(100.500d);

    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine), "", 204);

    MatcherAssert.assertThat(getInvoiceLineRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceLineUpdates(), hasSize(1));
    verifyInvoiceSummaryUpdateEvent(1);
  }

  @Test
  public void testPutInvoicingInvoiceLinesByIdTestWithInternalErrorOnInvoiceGet() throws Exception {
    String reqData = getMockData(INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_INTERNAL_ERROR_ON_GET_INVOICE + ".json");

    verifyPut(String.format(INVOICE_LINE_ID_PATH, INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID), reqData, APPLICATION_JSON, 500);

    verifyInvoiceSummaryUpdateEvent(0);
  }

  @Test
  public void testPutInvoicingInvoiceLinesByNonExistentId() throws Exception {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_DOES_NOT_EXIST);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, ID_DOES_NOT_EXIST), jsonBody, APPLICATION_JSON, 404);

    verifyInvoiceSummaryUpdateEvent(0);
  }

  @Test
  public void testPutInvoicingInvoiceLinesWithError() throws Exception {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_FOR_INTERNAL_SERVER_ERROR);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), jsonBody, APPLICATION_JSON, 500);

    verifyInvoiceSummaryUpdateEvent(0);
  }

  @Test
  public void testPutInvoicingInvoiceLinesInvalidIdFormat() throws Exception {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_BAD_FORMAT);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, ID_BAD_FORMAT), jsonBody, APPLICATION_JSON, 422);

    verifyInvoiceSummaryUpdateEvent(0);
  }

  @Test
  public void testPutInvoicingInvoiceLinesInvalidLang() throws Exception {
    String reqData = getMockData(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH);
    String endpoint = String.format(INVOICE_LINE_ID_PATH, VALID_UUID)
        + String.format("?%s=%s", LANG_PARAM, INVALID_LANG);

    verifyPut(endpoint, reqData, TEXT_PLAIN, 400);

    verifyInvoiceSummaryUpdateEvent(0);
  }

  @Test
  public void testPostInvoiceLinesWithAdjustments() throws IOException {
    logger.info("=== Test Post Invoice Lines with adjustments calculated ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT),
        APPLICATION_JSON, 201).as(InvoiceLine.class);
    double expectedAdjustmentsTotal = 7.022d;
    double expectedTotal = expectedAdjustmentsTotal+reqData.getSubTotal();

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoiceLinesWithNegativeAdjustments() throws IOException {
    logger.info("=== Test Post Invoice Lines with negative adjustment value ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    // set adjustment amount to a negative value
    reqData.getAdjustments()
      .get(1)
      .setValue(-5d);
    String jsonBody = JsonObject.mapFrom(reqData)
      .encode();
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);

    double expectedAdjustmentsTotal = -2.978d;
    double expectedTotal = 17.042d;

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoiceLinesWithNegativeTotal() throws IOException {
    logger.info("=== Test Post Invoice Lines with negative adjustment value ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setSubTotal(-20.234d);
    JsonObject jsonBody = JsonObject.mapFrom(reqData);
    // delete adjustments
    jsonBody.remove("adjustments");
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody.encode(), prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);

    double expectedAdjustmentsTotal = 0d;
    double expectedTotal = -20.234d;

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }


  @Test
  public void testPostInvoiceLinesWithNoAdjustments() throws IOException {
    logger.info("=== Test Post Invoice Lines with no adjustment value ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    JsonObject jsonBody = JsonObject.mapFrom(reqData);
    // delete adjustments
    jsonBody.remove("adjustments");
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody.encode(), prepareHeaders(X_OKAPI_TENANT),
        APPLICATION_JSON, 201).as(InvoiceLine.class);

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(0d));
    assertThat(invoiceLine.getTotal(), equalTo(reqData.getSubTotal()));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoicingInvoiceLinesWithIncorrectAdjustmentTotals() throws Exception {
    logger.info("=== Test Post Invoice Lines to ignore incorrect totals from request ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setAdjustmentsTotal(100d);
    reqData.setTotal(200d);

    String jsonBody = JsonObject.mapFrom(reqData).encode();

    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);
    double expectedAdjustmentsTotal = 7.022d;
    double expectedTotal = expectedAdjustmentsTotal + reqData.getSubTotal();

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoicingInvoiceLinesWithCurrencyScale() throws Exception {
    logger.info("=== Test Post Invoice Lines to use currency scale ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);
    //checking scale of currency, with currency as "BHD", which has a scale of 3.
    //In the test adjustment 10.1% of 20.02 = 2.02202,but check if it utilizes the scale of the currency.
    double expectedAdjustmentsTotal = 7.022d;
    double expectedTotal = expectedAdjustmentsTotal + reqData.getSubTotal();

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoicingInvoiceLinesWithRelationshipTotal() {
    logger.info("=== Test Post Invoice Lines to use only In addition To RelationToTotal ===");


    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    // set adjustment realtion to Included In
    reqData.getAdjustments()
      .get(0)
      .setRelationToTotal(Adjustment.RelationToTotal.INCLUDED_IN);
    String jsonBody = JsonObject.mapFrom(reqData)
      .encode();
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);

    double expectedAdjustmentsTotal = 5d;
    double expectedTotal = 25.02d;

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }


  @Test
  public void testGetInvoiceLinesByIdValidAdjustments() throws Exception {
    logger.info("=== Test Get Invoice line By Id, adjustments are re calculated ===");

    JsonObject invoiceLinesList = new JsonObject(getMockData(INVOICE_LINES_LIST_PATH));
    JsonObject invoiceLine = invoiceLinesList.getJsonArray("invoiceLines").getJsonObject(4);
    String id = invoiceLine.getString(ID);
    double incorrectAdjustmentTotal = invoiceLine.getDouble("adjustmentsTotal");
    logger.info(String.format("using mock datafile: %s%s.json", INVOICE_LINES_LIST_PATH, id));

    final InvoiceLine resp = verifySuccessGetById(id, true, true);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertEquals(id, resp.getId());
    assertThat(resp.getAdjustmentsTotal(), not(incorrectAdjustmentTotal));
  }

  @Test
  public void testNumberOfRequests() {
    logger.info("=== Test number of requests on invoice line PUT ===");

    // InvoiceLine with corresponding Invoice with status APPROVED
    checkNumberOfRequests(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);

    // InvoiceLine with corresponding Invoice with status OPEN
    checkNumberOfRequests(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID);
  }

  private void checkNumberOfRequests(String invoiceLineId) {
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setId(invoiceLineId);
    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLineId), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NO_CONTENT);

    MatcherAssert.assertThat(getInvoiceLineRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceLineUpdates(), hasSize(1));

    // All totals are unchanged
    verifyInvoiceSummaryUpdateEvent(0);
    clearServiceInteractions();
  }

  @Test
  public void testPutInvoicingInvoiceLinesWithProtectedFields() throws Exception {
    logger.info("=== Test update invoice line by id with protected fields (all fields set) ===");
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    // Invoice line updated (invoice status = APPROVED) - protected field not modified
    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NO_CONTENT);

    verifyInvoiceSummaryUpdateEvent(0);
    clearServiceInteractions();

    // Invoice line updated (invoice status = OPEN) - protected field not modified
    invoiceLine.setId(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID);
    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NO_CONTENT);

    verifyInvoiceSummaryUpdateEvent(0);
    clearServiceInteractions();

    // Invoice line updated (invoice status = APPROVED) - all protected fields modified

    InvoiceLine allProtectedFieldsModifiedInvoiceLine
      = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);
    Map<InvoiceLineProtectedFields, Object> allProtectedFieldsModification = new HashMap<>();

    // nested object verification
    // - field of nested object modified
    List<Adjustment> adjustments = invoiceLine.getAdjustments();
    adjustments.get(0).setValue(12345.54321);
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.ADJUSTMENTS, adjustments);

    checkPreventInvoiceLineModificationRule(allProtectedFieldsModifiedInvoiceLine, allProtectedFieldsModification);

    // - total nested object replaced
    adjustments = new ArrayList<>();
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.ADJUSTMENTS, adjustments);
    checkPreventInvoiceLineModificationRule(allProtectedFieldsModifiedInvoiceLine, allProtectedFieldsModification);

    // all other fields
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.INVOICE_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.INVOICE_LINE_NUMBER, "123456789");
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PO_LINE_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PRODUCT_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PRODUCT_ID_TYPE, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.QUANTITY, 10);
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.SUBSCRIPTION_INFO, "Tested subscription info");
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.SUBSCRIPTION_START, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.SUBSCRIPTION_END, new Date(System.currentTimeMillis()));

    checkPreventInvoiceLineModificationRule(allProtectedFieldsModifiedInvoiceLine, allProtectedFieldsModification);

  }

  private void checkPreventInvoiceLineModificationRule(InvoiceLine invoiceLine, Map<InvoiceLineProtectedFields, Object> updatedFields) throws IllegalAccessException {
    for (Map.Entry<InvoiceLineProtectedFields, Object> m : updatedFields.entrySet()) {
      FieldUtils.writeDeclaredField(invoiceLine, m.getKey().getFieldName(), m.getValue(), true);
    }
    String body = JsonObject.mapFrom(invoiceLine).encode();
    Errors errors = verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), body, "", HttpStatus.SC_BAD_REQUEST).as(Errors.class);
    Object[] failedFieldNames = ((List<String>) errors.getErrors().get(0).getAdditionalProperties().get(PROTECTED_AND_MODIFIED_FIELDS)).toArray();
    Object[] expected = updatedFields.keySet().stream().map(InvoiceLineProtectedFields::getFieldName).toArray();
    MatcherAssert.assertThat(failedFieldNames.length, is(expected.length));
    MatcherAssert.assertThat(expected, Matchers.arrayContainingInAnyOrder(failedFieldNames));

    verifyInvoiceSummaryUpdateEvent(0);
    clearServiceInteractions();
  }

  private void verifyInvoiceLineUpdateCalls(int msgQty) {
    logger.debug("Verifying calls to update invoice line");
    // Wait until message is registered
    await().atLeast(50, MILLISECONDS)
      .atMost(1, SECONDS)
      .until(MockServer::getInvoiceLineUpdates, hasSize(msgQty));
  }

  private void compareRecordWithSentToStorage(InvoiceLine invoiceLine) {
    // Verify that invoice line sent to storage is the same as in response
    assertThat(getInvoiceLineCreations(), hasSize(1));
    InvoiceLine invoiceLineToStorage = getInvoiceLineCreations().get(0).mapTo(InvoiceLine.class);
    assertThat(invoiceLine, equalTo(invoiceLineToStorage));
  }

  private InvoiceLine verifySuccessGetById(String id, boolean asyncLineUpdate, boolean asyncInvoiceUpdate) {
    InvoiceLine invoiceLine = verifySuccessGet(String.format(INVOICE_LINE_ID_PATH, id), InvoiceLine.class);

    // MODINVOICE-86 calculate the totals and if different from what was retrieved, write it back to storage
    verifyInvoiceLineUpdateCalls(asyncLineUpdate ? 1 : 0);
    verifyInvoiceSummaryUpdateEvent(asyncInvoiceUpdate ? 1 : 0);

    return invoiceLine;
  }
}
