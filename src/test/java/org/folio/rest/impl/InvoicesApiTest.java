package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpStatus;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;
import static org.folio.rest.impl.MockServer.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;

public class InvoicesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesApiTest.class);

  private static final String INVOICE_PATH = "/invoice/invoices";
	private static final String INVOICE_ID_PATH = INVOICE_PATH+ "/%s";
  private static final String INVOICE_ID_WITH_LANG_PATH = INVOICE_ID_PATH + "?lang=%s";
  private static final String INVOICE_PATH_BAD = "/invoice/bad";
  private static final String INVOICE_NUMBER_PATH = "/invoice/invoice-number";
  static final String INVOICE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoices/";
  private static final String INVOICES_LIST_PATH = INVOICE_MOCK_DATA_PATH + "invoices.json";
  private static final String INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + "invoice.json";

  static final String BAD_QUERY = "unprocessableQuery";
  private static final String VENDOR_INVOICE_NUMBER_FIELD = "vendorInvoiceNo";
  static final String EXISTING_VENDOR_INV_NO = "existingVendorInvoiceNo";
  static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  private static final String BAD_INVOICE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";

  @Test
  public void testGetInvoicingInvoices() {
    logger.info("=== Test Get Invoices by without query - get 200 by successful retrieval of invoices ===");

    final InvoiceCollection resp = verifySuccessGet(INVOICE_PATH, InvoiceCollection.class);

    assertEquals(3, resp.getTotalRecords().intValue());
  }

  @Test
  public void testGetInvoicingInvoicesWithQueryParam() {
    logger.info("=== Test Get Invoices with query - get 200 by successful retrieval of invoices by query ===");

    String endpointQuery = String.format("%s?query=%s==%s", INVOICE_PATH,  VENDOR_INVOICE_NUMBER_FIELD, EXISTING_VENDOR_INV_NO);

    final InvoiceCollection resp = verifySuccessGet(endpointQuery, InvoiceCollection.class);

    assertEquals(1, resp.getTotalRecords().intValue());
  }

  @Test
  public void testGetInvoicesBadQuery() {
    logger.info("=== Test Get Invoices by query - unprocessable query to emulate 400 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_PATH,  BAD_QUERY);

    verifyGet(endpointQuery, APPLICATION_JSON, 400);
  }

  @Test
  public void testGetInvoicesInternalServerError() {
    logger.info("=== Test Get Invoices by query - emulating 500 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_PATH,  ID_FOR_INTERNAL_SERVER_ERROR);

    verifyGet(endpointQuery, APPLICATION_JSON, 500);

  }

  @Test
  public void testGetInvoicingInvoicesBadRequestUrl() {
    logger.info("=== Test Get Invoices by query - emulating 400 by sending bad request Url ===");

    verifyGet(INVOICE_PATH_BAD, TEXT_PLAIN, 400);
  }

  @Test
  public void testGetInvoicingInvoicesById() throws IOException {
    logger.info("=== Test Get Invoice By Id ===");

    JsonObject invoicesList = new JsonObject(getMockData(INVOICES_LIST_PATH));
    String id = invoicesList.getJsonArray("invoices").getJsonObject(0).getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", INVOICES_LIST_PATH, id));

    final Invoice resp = verifySuccessGet(String.format(INVOICE_ID_PATH, id), Invoice.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void testGetInvoicingInvoicesByIdNotFound() {
    logger.info("=== Test Get Invoices by Id - 404 Not found ===");

    final Response resp = verifyGet(String.format(INVOICE_ID_PATH, BAD_INVOICE_ID), APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_INVOICE_ID, actual);
  }

  @Test
  public void testUpdateValidInvoice() {
    logger.info("=== Test update invoice by id ===");

     String newInvoiceNumber = "testFolioInvoiceNumber";

  	Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
  	reqData.setFolioInvoiceNo(newInvoiceNumber);

    String id = reqData.getId();
  	String jsonBody = JsonObject.mapFrom(reqData).encode();

  	verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, "", 204);
  	assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(FOLIO_INVOICE_NUMBER), not(newInvoiceNumber));
  }

  @Test
  public void testUpdateNotExistentInvoice() throws IOException {
    logger.info("=== Test update non existent invoice===");

    String jsonBody  = getMockData(INVOICE_SAMPLE_PATH);

    verifyPut(String.format(INVOICE_ID_PATH, ID_DOES_NOT_EXIST), jsonBody, APPLICATION_JSON, 404);
  }

  @Test
  public void testUpdateInvoiceInternalErrorOnStorage() throws IOException {
    logger.info("=== Test update invoice by id with internal server error from storage ===");

    String jsonBody  = getMockData(INVOICE_SAMPLE_PATH);

    verifyPut(String.format(INVOICE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), jsonBody, APPLICATION_JSON, 500);
  }

  @Test
  public void testUpdateInvoiceByIdWithInvalidFormat() throws IOException {

    String jsonBody  = getMockData(INVOICE_SAMPLE_PATH);

    verifyPut(String.format(INVOICE_ID_PATH, ID_BAD_FORMAT), jsonBody, TEXT_PLAIN, 400);
  }

  @Test
  public void testUpdateInvoiceBadLanguage() throws IOException {
    String jsonBody  = getMockData(INVOICE_SAMPLE_PATH);
    String endpoint = String.format(INVOICE_ID_WITH_LANG_PATH, VALID_UUID, INVALID_LANG) ;

    verifyPut(endpoint, jsonBody, TEXT_PLAIN, 400);
  }

  @Test
  public void testPostInvoicingInvoices() throws Exception {
    logger.info("=== Test create invoice without id and folioInvoiceNo ===");

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(INVOICE_SAMPLE_PATH);

    final Invoice respData = verifyPostResponse(INVOICE_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Invoice.class);

    String poId = respData.getId();
    String folioInvoiceNo = respData.getFolioInvoiceNo();

    assertThat(poId, notNullValue());
    assertThat(folioInvoiceNo, notNullValue());
    assertThat(MockServer.serverRqRs.get(FOLIO_INVOICE_NUMBER, HttpMethod.GET), hasSize(1));
  }

  @Test
  public void testPostInvoicingInvoicesErrorFromStorage() throws Exception {
    logger.info("=== Test create invoice without with error from storage on saving invoice  ===");

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(INVOICE_SAMPLE_PATH);

    verifyPostResponse(INVOICE_PATH, body, prepareHeaders(ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);

    assertThat(MockServer.serverRqRs.get(FOLIO_INVOICE_NUMBER, HttpMethod.GET), hasSize(1));
  }

  @Test
  public void testPostInvoicingInvoicesWithInvoiceNumberGenerationFail() throws IOException {
    logger.info("=== Test create invoice without error from storage on folioInvoiceNo generation  ===");

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(INVOICE_SAMPLE_PATH);

    verifyPostResponse(INVOICE_PATH, body, prepareHeaders(INVOICE_NUMBER_ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);

  }

  @Test
  public void testGetInvoiceNumber() {
    logger.info("=== Test Get Invoice number - not implemented ===");

    verifyGet(INVOICE_NUMBER_PATH, TEXT_PLAIN, 500);
  }

  @Test
  public void testDeleteInvoiceByValidId() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, VALID_UUID), "", 204);
  }

  @Test
  public void testDeleteInvoiceByIdWithInvalidFormat() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, ID_BAD_FORMAT), TEXT_PLAIN, 400);
  }

  @Test
  public void testDeleteNotExistentInvoice() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, ID_DOES_NOT_EXIST), APPLICATION_JSON, 404);
  }

  @Test
  public void testDeleteInvoiceInternalErrorOnStorage() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), APPLICATION_JSON, 500);
  }

  @Test
  public void testDeleteInvoiceBadLanguage() {

    String endpoint = String.format(INVOICE_ID_WITH_LANG_PATH, VALID_UUID, INVALID_LANG) ;

    verifyDeleteResponse(endpoint, TEXT_PLAIN, 400);
  }

  @Test
  public void testNumberOfRequests() {
    logger.info("=== Test nuber of requests on invoice PUT ===");

    // Invoice status APPROVED, PAID, CANCELLED - expect invoice updating with GET invoice rq + PUT invoice rq by statuses processable flow
    Invoice.Status[] processableStatuses = {Invoice.Status.APPROVED, Invoice.Status.PAID, Invoice.Status.CANCELLED};
    checkNumberOfRequests(processableStatuses);

    // Invoice status APPROVED, PAID, CANCELLED - expect invoice updating with GET invoice rq + PUT invoice rq without statuses processable flow
    Invoice.Status[] nonProcessableStatuses = {Invoice.Status.OPEN, Invoice.Status.REVIEWED};
    checkNumberOfRequests(nonProcessableStatuses);
  }

  private void checkNumberOfRequests(Invoice.Status[] statuses) {
    // Invoice status open - expect no GET invoice rq + PUT invoice rq
    for(Invoice.Status status : statuses) {
      Invoice invoice = getMockAsJson(INVOICE_MOCK_DATA_PATH + "c0d08448-347b-418a-8c2f-5fb50248d67e.json").mapTo(Invoice.class);
      invoice.setStatus(status);
      verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), JsonObject.mapFrom(invoice).encode(), "", HttpStatus.SC_NO_CONTENT);
      assertThat(serverRqRs.row("invoices").get(HttpMethod.GET), hasSize(1));
      assertThat(serverRqRs.row("invoices").get(HttpMethod.PUT), hasSize(1));
      serverRqRs.clear();
    }
  }

  @Test
  public void testUpdateInvoiceWithProtectedFields() throws IllegalAccessException {
    logger.info("=== Test update invoice by id with protected fields (all fields set) ===");

    Invoice invoice = getMockAsJson(INVOICE_MOCK_DATA_PATH + "c0d08448-347b-418a-8c2f-5fb50248d67e.json").mapTo(Invoice.class);

    Map<InvoiceProtectedFields, Object> allProtectedFieldsModification = new HashMap<>();

    List<Adjustment> adjustments = invoice.getAdjustments();
    adjustments.get(0).setValue(12345.54321);
    allProtectedFieldsModification.put(InvoiceProtectedFields.ADJUSTMENTS, adjustments);

    allProtectedFieldsModification.put(InvoiceProtectedFields.APPROVED_BY, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceProtectedFields.APPROVAL_DATE, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceProtectedFields.CHK_SUBSCRIPTION_OVERLAP, true);
    allProtectedFieldsModification.put(InvoiceProtectedFields.CURRENCY, "TUGRIK");
    allProtectedFieldsModification.put(InvoiceProtectedFields.FOLIO_INVOICE_NO, "some_folio_inv_num");
    allProtectedFieldsModification.put(InvoiceProtectedFields.INVOICE_DATE, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceProtectedFields.LOCK_TOTAL, true);
    allProtectedFieldsModification.put(InvoiceProtectedFields.PAYMENT_TERMS, "Payment now");
    allProtectedFieldsModification.put(InvoiceProtectedFields.SOURCE, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceProtectedFields.TOTAL, 123.123);
    allProtectedFieldsModification.put(InvoiceProtectedFields.VOUCHER_NUMBER, "some_voucher_number");
    allProtectedFieldsModification.put(InvoiceProtectedFields.PAYMENT_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceProtectedFields.VENDOR_ID, UUID.randomUUID().toString());

    List<String> poNumbers = invoice.getPoNumbers();
    poNumbers.add(0, "AB267798XYZ");
    allProtectedFieldsModification.put(InvoiceProtectedFields.PO_NUMBERS, poNumbers);

    checkPreventInvoiceModificationRule(invoice, allProtectedFieldsModification);

    // Check number of requests
    assertThat(serverRqRs.row("invoices").get(HttpMethod.GET), hasSize(1));
    // PUT request wasn't processed
    assertThat(serverRqRs.row("invoices").get(HttpMethod.PUT), nullValue());
  }

  private void checkPreventInvoiceModificationRule(Invoice invoice, Map<InvoiceProtectedFields, Object> updatedFields) throws IllegalAccessException {
    invoice.setStatus(Invoice.Status.APPROVED);
    for (Map.Entry<InvoiceProtectedFields, Object> m : updatedFields.entrySet()) {
      FieldUtils.writeDeclaredField(invoice, m.getKey().getFieldName(), m.getValue(), true);
    }
    String body = JsonObject.mapFrom(invoice).encode();
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), body, "", HttpStatus.SC_BAD_REQUEST).as(Errors.class);
    Object[] failedFieldNames = ((List<String>) errors.getErrors().get(0).getAdditionalProperties().get(PROTECTED_AND_MODIFIED_FIELDS)).toArray();
    Object[] expected = updatedFields.keySet().stream().map(InvoiceProtectedFields::getFieldName).toArray();
    assertThat(failedFieldNames.length, is(expected.length));
    assertThat(expected, Matchers.arrayContainingInAnyOrder(failedFieldNames));
  }
}
