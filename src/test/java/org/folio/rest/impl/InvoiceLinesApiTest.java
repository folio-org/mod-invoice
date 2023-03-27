package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_EXPENSE_CLASS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_INVOICE_LINE;
import static org.folio.invoices.utils.ErrorCodes.INCORRECT_FUND_DISTRIBUTION_TOTAL;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_INVOICE_LINE_CREATION;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.getNoAcqUnitCQL;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_ID;
import static org.folio.rest.impl.InvoicesApiTest.REVIEWED_INVOICE_ID;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;
import static org.folio.rest.impl.MockServer.INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.SEARCH_INVOICE_BY_LINE_ID_NOT_FOUND;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getAcqMembershipsSearches;
import static org.folio.rest.impl.MockServer.getAcqUnitsSearches;
import static org.folio.rest.impl.MockServer.getInvoiceLineCreations;
import static org.folio.rest.impl.MockServer.getInvoiceLineRetrievals;
import static org.folio.rest.impl.MockServer.getInvoiceLineUpdates;
import static org.folio.rest.impl.MockServer.getInvoiceRetrievals;
import static org.folio.rest.impl.MockServer.getInvoiceUpdates;
import static org.folio.rest.impl.MockServer.getQueryParams;
import static org.folio.rest.impl.MockServer.serverRqRs;
import static org.folio.rest.impl.ProtectionHelper.ACQUISITIONS_UNIT_IDS;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.utils.InvoiceLineProtectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.ValidateFundDistributionsRequest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class InvoiceLinesApiTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(InvoiceLinesApiTest.class);

  static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoiceLines/";
  static final String FUND_VALIDATOR_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "fundsValidator/validateFundDistributionsRequest.json";
  static final String FUND_VALIDATOR_MOCK_DATA_PATH_DUP_ADJ_ID = BASE_MOCK_DATA_PATH + "fundsValidator/validateFundDistributionsRequest_duplicate_adjId.json";
  static final String FUND_VALIDATOR_MOCK_DATA_WITH_ZERO_PRICE = BASE_MOCK_DATA_PATH + "fundsValidator/validateFundDistributionsRequest_mixed_zero_price.json";
  static final String FUND_VALIDATOR_MOCK_DATA_WITH_REMAINING_AMOUNT = BASE_MOCK_DATA_PATH + "fundsValidator/validateFundDistributionsRequest_with_remaining_amount.json";
  static final String INVOICE_LINES_LIST_PATH = INVOICE_LINES_MOCK_DATA_PATH + "invoice_lines.json";
  public static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  public static final String INVOICE_LINE_ID_PATH = INVOICE_LINES_PATH + "/%s";
  public static final String INVOICE_LINE_FUNDS_VALIDATOR_ID_PATH = "fund-distributions/validate";

  private static final String INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH = INVOICE_LINES_MOCK_DATA_PATH + "29846620-8fb6-4433-b84e-0b6051eb76ec.json";

  private static final String NULL = "null";

  private static final String INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID = "e0d08448-343b-118a-8c2f-4fb50248d672";
  private static final String APPROVED_INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID = "56874e66-be4e-4e95-a720-06b34584d5f8";
  private static final String INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID = "5cb6d270-a54c-4c38-b645-3ae7f249c606";
  private static final String INVOICE_LINE_WITH_INTERNAL_ERROR_ON_GET_INVOICE = "4051b42d-c6cf-4306-a331-209514af9877";
  private static final String INVOICE_LINE_OUTDATED_TOTAL = "55e4b6f5-f974-42da-9a77-24d4e8ef0e70";
  private static final String INVOICE_LINE_WITH_PO_NUMBER = "db49086e-df56-11eb-ba80-0242ac130004";
  private static final String PO_LINE_WITH_NO_PO_ID = "0009662b-8b80-4001-b704-ca10971f175d";
  private static final String INVOICE_LINE_OUTDATED_TOTAL_PATH = INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_OUTDATED_TOTAL + ".json";
  static final String INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH = INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID + ".json";
  static final String APPROVED_INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH = INVOICE_LINES_MOCK_DATA_PATH + APPROVED_INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID + ".json";
  private static final String INVOICE_LINE_WITH_PO_NUMBER_PATH = INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_PO_NUMBER + ".json";

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
    logger.info("=== Test 500 when update in storage fails ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_OUTDATED_TOTAL_PATH).mapTo(InvoiceLine.class);
    addMockEntry(INVOICE_LINES, invoiceLine.withId(ID_FOR_INTERNAL_SERVER_ERROR_PUT));

    // Check that invoice line update called which is expected to fail so invoice update is not triggered
    String endpointQuery = String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId());
    verifyGet(endpointQuery, APPLICATION_JSON, 500);
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
  }

  @Test
  public void getInvoicingInvoiceLinesByIdNotFoundTest() {
    logger.info("=== Test Get Invoice line by Id - 404 Not found ===");

    final Response resp = verifyGet(INVOICE_LINES_PATH + "/" + ID_DOES_NOT_EXIST, APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getParameters().get(0).getValue();
    logger.info("Id not found: " + actual);

    Assertions.assertEquals(ID_DOES_NOT_EXIST, actual);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() {
    logger.info("=== Test delete invoice line by id ===");

    addMockEntry(INVOICE_LINES, getMinimalContentInvoiceLine().withId(VALID_UUID).withInvoiceId(APPROVED_INVOICE_ID));
    addMockEntry(INVOICES, getMinimalContentInvoice());

    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), "", 204);
  }

  @Test
  public void shouldRemovePolNumbersFromInvoiceInInvoiceLineDeletingTimeTest() {
    InvoiceLine invoiceLineForDelete = getMinimalContentInvoiceLine().withId(VALID_UUID).withInvoiceId(APPROVED_INVOICE_ID);
    invoiceLineForDelete.setPoLineId("0000edd1-b463-41ba-bf64-1b1d9f9d0001");
    addMockEntry(INVOICE_LINES, invoiceLineForDelete);
    Invoice invoice = getMinimalContentInvoice();
    invoice.setPoNumbers(List.of("228D126"));
    addMockEntry(INVOICES, invoice);

    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), "", 204);
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(1));
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdWithInvoiceUpdate() {
    logger.info("=== Test delete invoice line triggering an invoice update ===");

    addMockEntry(INVOICE_LINES, getMinimalContentInvoiceLine().withId(VALID_UUID).withInvoiceId(APPROVED_INVOICE_ID));
    addMockEntry(INVOICES, getMinimalContentInvoice().withAdjustmentsTotal(1.0d));

    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), "", 204);
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(1));
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdForApprovedInvoiceTest() {
    logger.info("=== Test delete invoice line by id for approved invoice: Forbidden ===");

    addMockEntry(INVOICE_LINES, getMinimalContentInvoiceLine().withId(VALID_UUID).withInvoiceId(APPROVED_INVOICE_ID));
    addMockEntry(INVOICES, getMinimalContentInvoice(Invoice.Status.APPROVED));

    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), APPLICATION_JSON, 403);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdForPaidInvoiceTest() {
    logger.info("=== Test delete invoice line by id for paid invoice: Forbidden ===");

    addMockEntry(INVOICE_LINES, getMinimalContentInvoiceLine().withId(VALID_UUID).withInvoiceId(APPROVED_INVOICE_ID));
    addMockEntry(INVOICES, getMinimalContentInvoice(Invoice.Status.PAID));

    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), APPLICATION_JSON, 403);
  }

  @Test
  @Disabled
  public void deleteInvoiceLinesByIdWithInvalidFormatTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, ID_BAD_FORMAT), TEXT_PLAIN, 400);
  }

  @Test
  public void deleteNotExistentInvoiceLinesTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, ID_DOES_NOT_EXIST), APPLICATION_JSON, 404);
  }

  @Test
  public void deleteNotExistentInvoiceLinesNoInvoicesFoundTest() {
    logger.info(
        "=== Test to verify that on searching invoices by invoice-line id field, invoice-line does not exists or an empty invoices collection is returned, as a result throw an http exception with 404 status code ===");
    Errors errors = verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, SEARCH_INVOICE_BY_LINE_ID_NOT_FOUND), APPLICATION_JSON, 404)
      .then()
      .extract()
      .body()
      .as(Errors.class);
    assertThat(MockServer.serverRqRs.get(INVOICE_LINES, HttpMethod.DELETE), nullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(CANNOT_DELETE_INVOICE_LINE.getCode()));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getValue(), equalTo(SEARCH_INVOICE_BY_LINE_ID_NOT_FOUND));
  }

  @Test
  public void deleteInvoiceLinesInternalErrorOnStorageTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), APPLICATION_JSON, 500);
  }

  @Test
  public void fundValidationTest() {
    ValidateFundDistributionsRequest reqData = getMockAsJson(FUND_VALIDATOR_MOCK_DATA_PATH).mapTo(ValidateFundDistributionsRequest.class);
    verifyPut(INVOICE_LINE_FUNDS_VALIDATOR_ID_PATH, reqData, "", 204);
  }

  @Test
  public void fundValidationDupAdjIdTest() {
    ValidateFundDistributionsRequest reqData = getMockAsJson(FUND_VALIDATOR_MOCK_DATA_PATH_DUP_ADJ_ID).mapTo(ValidateFundDistributionsRequest.class);
    verifyPut(INVOICE_LINE_FUNDS_VALIDATOR_ID_PATH, reqData, "", 400);
  }

  @Test
  public void fundValidationWithZeroSubtotalTest() {
    ValidateFundDistributionsRequest reqData = getMockAsJson(FUND_VALIDATOR_MOCK_DATA_WITH_ZERO_PRICE).mapTo(ValidateFundDistributionsRequest.class);
    verifyPut(INVOICE_LINE_FUNDS_VALIDATOR_ID_PATH, reqData, "", 422);
  }

  @Test
  public void fundValidationWithRemainingAmountTest() {
    ValidateFundDistributionsRequest reqData = getMockAsJson(FUND_VALIDATOR_MOCK_DATA_WITH_REMAINING_AMOUNT).mapTo(ValidateFundDistributionsRequest.class);
    Errors resp = verifyPut(INVOICE_LINE_FUNDS_VALIDATOR_ID_PATH, reqData, "", 422).as(Errors.class);
    Assertions.assertEquals(1, resp.getErrors().size());
    Assertions.assertEquals(INCORRECT_FUND_DISTRIBUTION_TOTAL.getCode(), resp.getErrors().get(0).getCode());
    Assertions.assertEquals("remainingAmount", resp.getErrors().get(0).getParameters().get(0).getKey());
    Assertions.assertEquals("10.00", resp.getErrors().get(0).getParameters().get(0).getValue().stripLeading());
  }

  @Test
  public void fundValidationWithRemainingTest() {
    ValidateFundDistributionsRequest reqData = getMockAsJson(FUND_VALIDATOR_MOCK_DATA_WITH_ZERO_PRICE).mapTo(ValidateFundDistributionsRequest.class);
    verifyPut(INVOICE_LINE_FUNDS_VALIDATOR_ID_PATH, reqData, "", 422);
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
    String invoiceLineNo = respData.getInvoiceLineNumber();

    // MODINVOICE-86 Verify total is calculated upon invoice-line creation
    Double expectedTotal = 2.42d;
    assertThat(respData.getTotal(), equalTo(expectedTotal));

    assertThat(invoiceId, notNullValue());
    assertThat(invoiceLineNo, is("1"));
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
  public void testPostInvoicingInvoiceLinesWithInvoiceLineNumberGenerationFail() {
    logger.info("=== Test create invoice with error from storage on invoiceLineNo generation  ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(REVIEWED_INVOICE_ID);
    String body = JsonObject.mapFrom(reqData).encodePrettily();
    verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);
  }

  @Test
  public void testPostInvoiceLinesByIdLineWithoutId() {
    logger.info("=== Test Post Invoice Lines By Id (empty id in body) ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(null);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Errors resp = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(MockServer.NON_EXIST_CONFIG_X_OKAPI_TENANT),
        APPLICATION_JSON, 422).as(Errors.class);

    Assertions.assertEquals(1, resp.getErrors().size());
    Assertions.assertEquals(INVOICE_ID, resp.getErrors().get(0).getParameters().get(0).getKey());
    Assertions.assertEquals(NULL, resp.getErrors().get(0).getParameters().get(0).getValue());
  }

  @Test
  public void testAddInvoiceLineWithWrongExpenseClasses() {
    logger.info("=== Test create invoice line (expense class doesn't exist in budget) ===");
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(OPEN_INVOICE_ID);
    List<FundDistribution> fundDistrList = new ArrayList<>();
    fundDistrList.add(new FundDistribution()
        .withDistributionType(PERCENTAGE)
        .withValue(50d)
        .withFundId("1d1574f1-9196-4a57-8d1f-3b2e4309eb81")
        .withExpenseClassId("198bcc9a-3f87-43d7-9313-7adddf98f284"));

    invoiceLine.setFundDistributions(fundDistrList);
    String jsonBody = JsonObject.mapFrom(invoiceLine).encodePrettily();

    Errors resp = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT),
        APPLICATION_JSON, 400).as(Errors.class);

    Assertions.assertEquals(1, resp.getErrors().size());
    Assertions.assertEquals(BUDGET_EXPENSE_CLASS_NOT_FOUND.getCode(), resp.getErrors().get(0).getCode());
  }

  @Test
  public void testPutInvoicingInvoiceLinesByIdTest() throws Exception {
    String reqData = getMockData(INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID + ".json");

    verifyPut(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID, reqData, "", 204);

    // No any total changed
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
  }

  @Test
  public void testPutInvoiceLineUpdateTotalAndInvoice() {
    logger.info("=== Test case when invoice line updates triggers also invoice update ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_OUTDATED_TOTAL_PATH).mapTo(InvoiceLine.class);
    addMockEntry(INVOICE_LINES, invoiceLine);
    invoiceLine.setSubTotal(100.500d);

    verifyPut(invoiceLine.getId(), JsonObject.mapFrom(invoiceLine), "", 204);

    MatcherAssert.assertThat(getInvoiceLineRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceLineUpdates(), hasSize(1));
    InvoiceLine updatedInvoiceLine = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
    Assertions.assertEquals(100.5d, updatedInvoiceLine.getSubTotal());
    Assertions.assertEquals(10.15d, updatedInvoiceLine.getAdjustmentsTotal());
    Assertions.assertEquals(110.65d, updatedInvoiceLine.getTotal());
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(1));
    Invoice updatedInvoice = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    // invoice totals are using the line totals pre-update because the mock server is not actually modifying the line,
    // but we can check the total was correctly updated
    Assertions.assertEquals(4.2d, updatedInvoice.getSubTotal());
    Assertions.assertEquals(0.42d, updatedInvoice.getAdjustmentsTotal());
    Assertions.assertEquals(4.62d, updatedInvoice.getTotal());
  }

  @Test
  public void testPutInvoicingInvoiceLinesByIdTestWithInternalErrorOnInvoiceGet() throws Exception {
    String reqData = getMockData(INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_INTERNAL_ERROR_ON_GET_INVOICE + ".json");

    verifyPut(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID, reqData, APPLICATION_JSON, 500);
  }

  @Test
  public void testPutInvoicingInvoiceLinesByNonExistentId() {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_DOES_NOT_EXIST);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(ID_DOES_NOT_EXIST, jsonBody, APPLICATION_JSON, 404);
  }

  @Test
  public void testPutInvoicingInvoiceLinesWithError() {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_FOR_INTERNAL_SERVER_ERROR);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(ID_FOR_INTERNAL_SERVER_ERROR, jsonBody, APPLICATION_JSON, 500);
  }

  @Test
  public void testPutInvoicingInvoiceLinesInvalidIdFormat() {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_BAD_FORMAT);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(ID_BAD_FORMAT, jsonBody, APPLICATION_JSON, 422);
  }

  @Test
  public void testPutInvoiceLineWithWrongExpenseClasses() {
    logger.info("=== Test put invoice line (expense class doesn't exist in budget) ===");
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(OPEN_INVOICE_ID);

    List<FundDistribution> fundDistrList = new ArrayList<>();
    fundDistrList.add(new FundDistribution()
        .withDistributionType(PERCENTAGE)
        .withValue(50d)
        .withFundId("1d1574f1-9196-4a57-8d1f-3b2e4309eb81")
        .withExpenseClassId("198bcc9a-3f87-43d7-9313-7adddf98f284"));

    invoiceLine.setFundDistributions(fundDistrList);
    String jsonBody = JsonObject.mapFrom(invoiceLine).encodePrettily();

    Errors resp = verifyPut(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID, jsonBody, "", 400)
        .as(Errors.class);

    Assertions.assertEquals(1, resp.getErrors().size());
    Assertions.assertEquals(BUDGET_EXPENSE_CLASS_NOT_FOUND.getCode(), resp.getErrors().get(0).getCode());
  }

  @Test
  public void testPostInvoiceLinesWithAdjustments() {
    logger.info("=== Test Post Invoice Lines with adjustments calculated ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT),
        APPLICATION_JSON, 201).as(InvoiceLine.class);
    double expectedAdjustmentsTotal = 7.02d;
    double expectedTotal = expectedAdjustmentsTotal+reqData.getSubTotal();

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoiceLinesWithNegativeAdjustments() {
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

    double expectedAdjustmentsTotal = -2.98d;
    double expectedTotal = 17.04d;

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoiceLinesWithNegativeTotal() {
    logger.info("=== Test Post Invoice Lines with negative adjustment value ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setSubTotal(-20.23d);
    JsonObject jsonBody = JsonObject.mapFrom(reqData);
    // delete adjustments
    jsonBody.remove("adjustments");
    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody.encode(), prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);

    double expectedAdjustmentsTotal = 0d;
    double expectedTotal = -20.23d;

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }


  @Test
  public void testPostInvoiceLinesWithNoAdjustments() {
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
  public void testPostInvoicingInvoiceLinesWithIncorrectAdjustmentTotals() {
    logger.info("=== Test Post Invoice Lines to ignore incorrect totals from request ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setAdjustmentsTotal(100d);
    reqData.setTotal(200d);

    String jsonBody = JsonObject.mapFrom(reqData).encode();

    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);
    double expectedAdjustmentsTotal = 7.02d;
    double expectedTotal = expectedAdjustmentsTotal + reqData.getSubTotal();

    assertThat(invoiceLine.getAdjustmentsTotal(), equalTo(expectedAdjustmentsTotal));
    assertThat(invoiceLine.getTotal(), equalTo(expectedTotal));

    compareRecordWithSentToStorage(invoiceLine);
  }

  @Test
  public void testPostInvoicingInvoiceLinesWithCurrencyScale() {
    logger.info("=== Test Post Invoice Lines to use currency scale ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_ADJUSTMENTS_SAMPLE_PATH).mapTo(InvoiceLine.class);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    InvoiceLine invoiceLine = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,
        201).as(InvoiceLine.class);

    double expectedAdjustmentsTotal = 7.02d;
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
  public void testNumberOfRequests() {
    logger.info("=== Test number of requests on invoice line PUT ===");
    clearServiceInteractions();
    // InvoiceLine with corresponding Invoice with status APPROVED
    checkNumberOfRequests(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);
    clearServiceInteractions();

    // InvoiceLine with corresponding Invoice with status OPEN
    checkNumberOfRequests(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID);
  }

  private void checkNumberOfRequests(String invoiceLineId) {
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINES_MOCK_DATA_PATH + invoiceLineId + ".json").mapTo(InvoiceLine.class);
    invoiceLine.setId(invoiceLineId);
    verifyPut(invoiceLineId, invoiceLine, "", HttpStatus.SC_NO_CONTENT);

    MatcherAssert.assertThat(getInvoiceLineRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceRetrievals(), hasSize(1));
    MatcherAssert.assertThat(getInvoiceLineUpdates(), hasSize(1));

    // All totals are unchanged
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
    clearServiceInteractions();
  }

  @Test
  public void testPutInvoicingInvoiceLinesWithProtectedFields() throws Exception {
    logger.info("=== Test update invoice line by id with protected fields (all fields set) ===");
    InvoiceLine invoiceLineApproved = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    // Invoice line updated (invoice status = APPROVED) - protected field not modified
    verifyPut(invoiceLineApproved.getId(), invoiceLineApproved, "", HttpStatus.SC_NO_CONTENT);

    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
    clearServiceInteractions();

    // Invoice line updated (invoice status = OPEN) - protected field not modified
    var invoiceLineOpen = getMockAsJson(INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID + ".json")
      .mapTo(InvoiceLine.class);
    verifyPut(invoiceLineOpen.getId(), invoiceLineOpen, "", HttpStatus.SC_NO_CONTENT);

    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
    clearServiceInteractions();

    // Invoice line updated (invoice status = APPROVED) - all protected fields modified

    InvoiceLine allProtectedFieldsModifiedInvoiceLine
      = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLineApproved.setInvoiceId(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);
    Map<InvoiceLineProtectedFields, Object> allProtectedFieldsModification = new HashMap<>();

    // nested object verification
    // - field of nested object modified
    List<Adjustment> adjustments = invoiceLineApproved.getAdjustments();
    adjustments.get(0).setValue(12345.54321);
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.ADJUSTMENTS, adjustments);

    checkPreventInvoiceLineModificationRule(allProtectedFieldsModifiedInvoiceLine, allProtectedFieldsModification);

    // - total nested object replaced
    adjustments = new ArrayList<>();
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.ADJUSTMENTS, adjustments);
    checkPreventInvoiceLineModificationRule(allProtectedFieldsModifiedInvoiceLine, allProtectedFieldsModification);

    // all other fields
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.INVOICE_LINE_NUMBER, "123456789");
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PO_LINE_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PRODUCT_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PRODUCT_ID_TYPE, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.QUANTITY, 10);

    checkPreventInvoiceLineModificationRule(allProtectedFieldsModifiedInvoiceLine, allProtectedFieldsModification);

  }

  @Test
  public void testPutDeletePoLineRef() {
    logger.info("=== Test update invoice line by id with protected fields (all fields set) ===");
    InvoiceLine invoiceLine = getMockAsJson( INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID + ".json").mapTo(InvoiceLine.class);
    invoiceLine.setPoLineId(null);
    // Invoice line updated (invoice status = APPROVED) - protected field not modified
    verifyPut(invoiceLine.getId(), invoiceLine, "", HttpStatus.SC_NO_CONTENT);
    // a poNumbers update is needed for the invoice
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(1));
  }

  @Test
  public void testPutUpdatePoLineRef() {
    logger.info("=== Test update invoice line by id with protected fields (all fields set) ===");
    InvoiceLine invoiceLine = getMockAsJson( INVOICE_LINES_MOCK_DATA_PATH + INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID + ".json").mapTo(InvoiceLine.class);
    invoiceLine.setPoLineId("0000edd1-b463-41ba-bf64-1b1d9f9d0001");
    // Invoice line updated (invoice status = APPROVED) - protected field not modified
    verifyPut(invoiceLine.getId(), invoiceLine, "", HttpStatus.SC_NO_CONTENT);

    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
  }

  @Test
  public void checkInvoiceLineCreationUpdatesInvoicePoNumbers() {
    logger.info("=== Check an invoice line creation triggers the update of the invoice's poNumbers field ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(OPEN_INVOICE_ID);

    String body = JsonObject.mapFrom(reqData).encodePrettily();
    verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201);

    List<JsonObject> objects = serverRqRs.get(INVOICES, HttpMethod.PUT);
    assertThat(objects, notNullValue());
    Invoice updatedInvoice = objects.get(objects.size() - 1).mapTo(Invoice.class);
    List<String> poNumbers = updatedInvoice.getPoNumbers();
    // that invoice already had a poNumber, it gets another one
    assertThat(poNumbers, hasSize(2));
    assertThat(poNumbers.get(1), equalTo("AB268758XYZ"));
  }

  @Test
  public void checkInvoiceLineUpdateUpdatesInvoicePoNumbers() {
    logger.info("=== Check an invoice line update triggers the update of the invoice's poNumbers field ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(OPEN_INVOICE_ID);
    addMockEntry(INVOICE_LINES, invoiceLine);

    verifyPut(INVOICE_LINE_WITH_PO_NUMBER, JsonObject.mapFrom(invoiceLine), "", 204);

    List<JsonObject> objects = serverRqRs.get(INVOICES, HttpMethod.PUT);
    assertThat(objects, notNullValue());
    Invoice updatedInvoice = objects.get(0).mapTo(Invoice.class);
    List<String> poNumbers = updatedInvoice.getPoNumbers();
    // that invoice already had a poNumber, it gets another one
    assertThat(poNumbers, hasSize(2));
    assertThat(poNumbers.get(1), equalTo("AB268758XYZ"));
  }

  @Test
  public void checkInvoiceLineUpdateDoesNotTriggerInvoiceUpdateIfPoNumberAlreadyThere() {
    logger.info("=== Check an invoice line update does not trigger the update of the invoice's poNumbers field if it already includes the new line's po number ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    addMockEntry(INVOICE_LINES, invoiceLine);

    verifyPut(INVOICE_LINE_WITH_PO_NUMBER, JsonObject.mapFrom(invoiceLine), "", 204);

    List<JsonObject> objects = serverRqRs.get(INVOICES, HttpMethod.PUT);
    assertThat(objects, nullValue());
  }

  @Test
  public void checkInvoiceLineUpdateRemovesInvoicePoNumbers() {
    logger.info("=== Check an invoice line update removing the po line link triggers the update of the invoice's poNumbers field ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setPoLineId(null);

    verifyPut(INVOICE_LINE_WITH_PO_NUMBER, JsonObject.mapFrom(invoiceLine), "", 204);

    List<JsonObject> objects = serverRqRs.get(INVOICES, HttpMethod.PUT);
    assertThat(objects, notNullValue());
    Invoice updatedInvoice = objects.get(0).mapTo(Invoice.class);
    List<String> poNumbers = updatedInvoice.getPoNumbers();
    assertThat(poNumbers, hasSize(0));
  }

  @Test
  public void checkNoPoNumbersUpdateIfAnotherLineIsLinkedToSamePO() {
    logger.info("=== Check an invoice line update removing the po line link does not triggers the update of the invoice's poNumbers field if another invoice line links to the same PO ===");

    InvoiceLine invoiceLine1 = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    invoiceLine1.setId(UUID.randomUUID().toString());
    addMockEntry(INVOICE_LINES, invoiceLine1);
    InvoiceLine invoiceLine2 = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    invoiceLine2.setPoLineId(null);

    verifyPut(INVOICE_LINE_WITH_PO_NUMBER, JsonObject.mapFrom(invoiceLine2), "", 204);

    List<JsonObject> objects = serverRqRs.get(INVOICES, HttpMethod.PUT);
    assertThat(objects, nullValue());
  }

  @Test
  public void checkInvoiceLineUpdateFailsToUpdateInvoicePoNumbers() {
    logger.info("=== Check the returned error when a poNumbers field cannot be updated ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_PO_NUMBER_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(OPEN_INVOICE_ID);
    invoiceLine.setPoLineId(PO_LINE_WITH_NO_PO_ID);
    // updating poNumbers will fail because it cannot get the PO from the PO line
    verifyPut(INVOICE_LINE_WITH_PO_NUMBER, JsonObject.mapFrom(invoiceLine), "", 500);
  }

  private void checkPreventInvoiceLineModificationRule(InvoiceLine invoiceLine, Map<InvoiceLineProtectedFields, Object> updatedFields) throws IllegalAccessException {
    for (Map.Entry<InvoiceLineProtectedFields, Object> m : updatedFields.entrySet()) {
      FieldUtils.writeDeclaredField(invoiceLine, m.getKey().getFieldName(), m.getValue(), true);
    }

    Errors errors = verifyPut(invoiceLine.getId(), invoiceLine, "", HttpStatus.SC_BAD_REQUEST).as(Errors.class);
    Object[] failedFieldNames = ((List) errors.getErrors().get(0).getAdditionalProperties().get(PROTECTED_AND_MODIFIED_FIELDS)).toArray();
    Object[] expected = updatedFields.keySet().stream().map(InvoiceLineProtectedFields::getFieldName).toArray();
    MatcherAssert.assertThat(failedFieldNames.length, is(expected.length));
    MatcherAssert.assertThat(expected, Matchers.arrayContainingInAnyOrder(failedFieldNames));
  }

  private void compareRecordWithSentToStorage(InvoiceLine invoiceLine) {
    // Verify that invoice line sent to storage is the same as in response
    assertThat(getInvoiceLineCreations(), hasSize(1));
    InvoiceLine invoiceLineToStorage = getInvoiceLineCreations().get(0).mapTo(InvoiceLine.class);
    assertThat(invoiceLine, equalTo(invoiceLineToStorage));
  }

  private InvoiceLine verifySuccessGetById(String id, boolean lineUpdate, boolean invoiceUpdate) {
    InvoiceLine invoiceLine = verifySuccessGet(String.format(INVOICE_LINE_ID_PATH, id), InvoiceLine.class);

    // MODINVOICE-86 calculate the totals and if different from what was retrieved, write it back to storage
    MatcherAssert.assertThat(getInvoiceLineUpdates(), hasSize(lineUpdate ? 1 : 0));
    MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(invoiceUpdate ? 1 : 0));

    return invoiceLine;
  }

  @Override
  Response verifyPut(String id, Object body, String expectedContentType, int expectedCode) {
    Response response = super.verifyPut(String.format(INVOICE_LINE_ID_PATH, id), body, expectedContentType, expectedCode);
    if (expectedCode != 204) {
      MatcherAssert.assertThat(getInvoiceUpdates(), hasSize(0));
    }
    return response;
  }
}
