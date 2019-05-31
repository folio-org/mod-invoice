package org.folio.rest.impl;

import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpStatus;
import org.folio.invoices.utils.InvoiceLineProtectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;
import static org.folio.rest.impl.MockServer.INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.serverRqRs;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;


public class InvoiceLinesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceLinesApiTest.class);

  static final Header NON_EXIST_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoicetest");
  static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoiceLines/";
  private static final String INVOICE_LINES_LIST_PATH = INVOICE_LINES_MOCK_DATA_PATH + "invoice_lines.json";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_LINE_ID_PATH = INVOICE_LINES_PATH + "/%s";
  private static final String INVOICE_LINE_SAMPLE_PATH = "mockdata/invoiceLines/invoice_line.json";
  private static final String INVOICE_LINE_SAMPLE_FOR_PROTECTED_FIELDS_PATH = "mockdata/invoiceLines/e0d08448-343b-118a-8c2f-4fb50248d672.json";
  private static final String BAD_INVOICE_LINE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String INVOICE_ID = "invoiceId";
  private static final String NULL = "null";

  private static final String VALID_INVOICE_LINE_ID = "e0d08448-343b-118a-8c2f-4fb50248d672";
  private static final String INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID = "e0d08448-343b-118a-8c2f-4fb50248d672";
  private static final String INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID = "5cb6d270-a54c-4c38-b645-3ae7f249c606";
  private static final String INVOICE_LINE_WITH_NOT_EXISTED_INVOICE_ID = "ebd42944-20fc-4448-86ad-60ec9b73a6d7";
  static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";


  @Test
  public void getInvoicingInvoiceLinesTest() {
    verifyGet(INVOICE_LINES_PATH, APPLICATION_JSON, 200);
  }

  @Test
  public void testGetOrderLinesInternalServerError() {
    logger.info("=== Test Get Order Lines by query - emulating 500 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_LINES_PATH, ID_FOR_INTERNAL_SERVER_ERROR);

    verifyGet(endpointQuery, APPLICATION_JSON, 500);
  }

  @Test
  public void testGetOrderLinesBadQuery() {
    logger.info("=== Test Get Order Lines by query - unprocessable query to emulate 400 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_LINES_PATH, BAD_QUERY);

    verifyGet(endpointQuery, APPLICATION_JSON, 400);

  }

  @Test
  public void getInvoicingInvoiceLinesByIdTest() throws Exception {
    logger.info("=== Test Get Invoice line By Id ===");

    JsonObject invoiceLinesList = new JsonObject(getMockData(INVOICE_LINES_LIST_PATH));
    String id = invoiceLinesList.getJsonArray("invoiceLines").getJsonObject(0).getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", INVOICE_LINES_LIST_PATH, id));

    final InvoiceLine resp = verifySuccessGet(INVOICE_LINES_PATH + "/" + id, InvoiceLine.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void getInvoicingInvoiceLinesByIdNotFoundTest() throws MalformedURLException {
    logger.info("=== Test Get Invoice line by Id - 404 Not found ===");

    final Response resp = verifyGet(INVOICE_LINES_PATH + "/" + BAD_INVOICE_LINE_ID, APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_INVOICE_LINE_ID, actual);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() {
    logger.info("=== Test delete invoice line by id ===");

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
    String endpoint = String.format(INVOICE_LINE_ID_PATH, VALID_UUID) + String.format("?%s=%s", LANG_PARAM, INVALID_LANG) ;

    verifyDeleteResponse(endpoint, TEXT_PLAIN, 400);
  }

  @Test
  public void postInvoicingInvoiceLinesTest() throws Exception {
    logger.info("=== Test create invoice line - 201 successfully created ===");

    String body = getMockData(INVOICE_LINE_SAMPLE_PATH);

    final InvoiceLine respData = verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(InvoiceLine.class);

    String invoiceId = respData.getId();
    String InvoiceLineNo = respData.getInvoiceLineNumber();

    assertThat(invoiceId, notNullValue());
    assertThat(InvoiceLineNo, notNullValue());
    assertThat(MockServer.serverRqRs.get(INVOICE_LINE_NUMBER, HttpMethod.GET), hasSize(1));
  }

  @Test
  public void testPostInvoicingInvoiceLinesWithInvoiceLineNumberGenerationFail() throws IOException {
    logger.info("=== Test create invoice with error from storage on invoiceLineNo generation  ===");

    String body = getMockData(INVOICE_LINE_SAMPLE_PATH);
    verifyPostResponse(INVOICE_LINES_PATH, body, prepareHeaders(INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);
  }

  @Test
  public void testPostInvoiceLinesByIdLineWithoutId() throws IOException {
    logger.info("=== Test Post Invoice Lines By Id (empty id in body) ===");

    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setInvoiceId(null);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Errors resp = verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(NON_EXIST_CONFIG_X_OKAPI_TENANT),
        APPLICATION_JSON, 422).as(Errors.class);

    assertEquals(1, resp.getErrors().size());
    assertEquals(INVOICE_ID, resp.getErrors().get(0).getParameters().get(0).getKey());
    assertEquals(NULL, resp.getErrors().get(0).getParameters().get(0).getValue());
  }

  @Test
  public void testPutInvoicingInvoiceLinesByIdTest() throws Exception {
    String reqData = getMockData(INVOICE_LINES_MOCK_DATA_PATH + VALID_INVOICE_LINE_ID + ".json");

    verifyPut(String.format(INVOICE_LINE_ID_PATH, VALID_INVOICE_LINE_ID), reqData, "", 204);
  }

  @Test
  public void testPutInvoicingInvoiceLinesByNonExistentId() throws Exception {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_DOES_NOT_EXIST);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, ID_DOES_NOT_EXIST), jsonBody,
        APPLICATION_JSON, 404);
  }

  @Test
  public void testPutInvoicingInvoiceLinesWithError() throws Exception {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_FOR_INTERNAL_SERVER_ERROR);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), jsonBody, APPLICATION_JSON, 500);
  }

  @Test
  public void testPutInvoicingInvoiceLinesInvalidIdFormat() throws Exception {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(ID_BAD_FORMAT);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, ID_BAD_FORMAT), jsonBody, APPLICATION_JSON, 422);
  }

  @Test
  public void testPutInvoicingInvoiceLinesInvalidLang() throws Exception {
    String reqData = getMockData(INVOICE_LINE_SAMPLE_PATH);
    String endpoint = String.format(INVOICE_LINE_ID_PATH, VALID_UUID)
        + String.format("?%s=%s", LANG_PARAM, INVALID_LANG);

    verifyPut(endpoint, reqData, TEXT_PLAIN, 400);

  }

  @Test
  public void testNumberOfRequests() {
    logger.info("=== Test nuber of requests on invoice line PUT ===");

    // InvoiceLine with corresponding Invoice with status APPROVED
    checkNumberOfRequests(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);

    // InvoiceLine with corresponding Invoice with status APPROVED
    checkNumberOfRequests(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID);
  }

  private void checkNumberOfRequests(String invoiceLineId) {
      InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_SAMPLE_FOR_PROTECTED_FIELDS_PATH).mapTo(InvoiceLine.class);
      invoiceLine.setId(invoiceLineId);
      verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLineId), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NO_CONTENT);
      MatcherAssert.assertThat(serverRqRs.row("invoiceLines").get(HttpMethod.GET), hasSize(1));
      MatcherAssert.assertThat(serverRqRs.row("invoices").get(HttpMethod.GET), hasSize(1));
      MatcherAssert.assertThat(serverRqRs.row("invoiceLines").get(HttpMethod.PUT), hasSize(1));
      serverRqRs.clear();

  }

  @Test
  public void testPutInvoicingInvoiceLinesWithProtectedFields() throws Exception {
    logger.info("=== Test update invoice line by id with protected fields (all fields set) ===");
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_SAMPLE_FOR_PROTECTED_FIELDS_PATH).mapTo(InvoiceLine.class);

    // Invoice line updated (invoice status = APPROVED) - protected field not modified
    invoiceLine.setId(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);
    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NO_CONTENT);

    // Invoice line updated (invoice status = OPEN) - protected field not modified
    invoiceLine.setId(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID);
    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NO_CONTENT);

    // Invoice line updated (invoice not founded)
    invoiceLine.setId(INVOICE_LINE_WITH_NOT_EXISTED_INVOICE_ID);
    verifyPut(String.format(INVOICE_LINE_ID_PATH, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine).encode(), "", HttpStatus.SC_NOT_FOUND);

    // Invoice line updated (invoice status = APPROVED) - all protected fields modified

    InvoiceLine allProtectedFieldsModificatedInvoiceLine
      = getMockAsJson(INVOICE_LINE_SAMPLE_FOR_PROTECTED_FIELDS_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(INVOICE_LINE_WITH_APPROVED_EXISTED_INVOICE_ID);
    Map<InvoiceLineProtectedFields, Object> allProtectedFieldsModification = new HashMap<>();

    // nested object verification
    // - field of nested object modified
    List<Adjustment> adjustments = invoiceLine.getAdjustments();
    adjustments.get(0).setValue(12345.54321);
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.ADJUSTMENTS, adjustments);

    checkPreventInvoiceLineModificationRule(allProtectedFieldsModificatedInvoiceLine, allProtectedFieldsModification);

    // - total nested object replaced
    adjustments = new ArrayList<>();
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.ADJUSTMENTS, adjustments);
    checkPreventInvoiceLineModificationRule(allProtectedFieldsModificatedInvoiceLine, allProtectedFieldsModification);

    // all other fields
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.INVOICE_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.INVOICE_LINE_NUMBER, "123456789");
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PO_LINE_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PRODUCT_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.PRODUCT_ID_TYPE, InvoiceLine.ProductIdType.VENDOR_ITEM_NUMBER);
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.QUANTITY, 10);
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.SUBSCRIPTION_INFO, "Tested subscription info");
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.SUBSCRIPTION_START, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.SUBSCRIPTION_END, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceLineProtectedFields.TOTAL, 123.123);

    checkPreventInvoiceLineModificationRule(allProtectedFieldsModificatedInvoiceLine, allProtectedFieldsModification);

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
  }
}
