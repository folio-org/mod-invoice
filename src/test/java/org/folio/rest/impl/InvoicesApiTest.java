package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.rest.acq.model.CompositePoLine;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.junit.Test;

import static java.util.stream.Collectors.groupingBy;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.INVOICE_NUMBER_ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.PO_LINES;
import static org.folio.rest.impl.MockServer.serverRqRs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

public class InvoicesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesApiTest.class);

  private static final String INVOICE_PATH = "/invoice/invoices";
	private static final String INVOICE_ID_PATH = INVOICE_PATH+ "/%s";
  private static final String INVOICE_ID_WITH_LANG_PATH = INVOICE_ID_PATH + "?lang=%s";
  private static final String INVOICE_PATH_BAD = "/invoice/bad";
  private static final String INVOICE_NUMBER_PATH = "/invoice/invoice-number";
  static final String INVOICE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoices/";
  private static final String PO_LINE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  private static final String INVOICES_LIST_PATH = INVOICE_MOCK_DATA_PATH + "invoices.json";
  private static final String INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + "invoice.json";

  static final String BAD_QUERY = "unprocessableQuery";
  private static final String VENDOR_INVOICE_NUMBER_FIELD = "vendorInvoiceNo";
  static final String EXISTING_VENDOR_INV_NO = "existingVendorInvoiceNo";
  private static final String BAD_INVOICE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String EXISTENT_PO_LINE_ID = "c2755a78-2f8d-47d0-a218-059a9b7391b4";
  private static final String STATUS = "status";


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
  public void testUpdateValidInvoiceTransitionToPaidWithMissingPoLine() {
    logger.info("=== Test transition invoice to paid with deleted associated poLine ===");

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setStatus(Invoice.Status.PAID);
    String id = reqData.getId();
    invoiceLine.setInvoiceId(id);
    invoiceLine.setPoLineId(ID_DOES_NOT_EXIST);
    serverRqRs.put(INVOICE_LINES, HttpMethod.POST, Collections.singletonList(JsonObject.mapFrom(invoiceLine)));
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, APPLICATION_JSON, 500).then().extract().body().as(Errors.class);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT), nullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getMessage(), containsString(ID_DOES_NOT_EXIST));
  }

  @Test
  public void testUpdateValidInvoiceTransitionToPaidWitErrorOnPoLineUpdate() {
    logger.info("=== Test transition invoice to paid with server error poLine update ===");

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setStatus(Invoice.Status.PAID);
    String id = reqData.getId();
    invoiceLine.setInvoiceId(id);
    invoiceLine.setPoLineId(ID_FOR_INTERNAL_SERVER_ERROR_PUT);
    serverRqRs.put(INVOICE_LINES, HttpMethod.POST, Collections.singletonList(JsonObject.mapFrom(invoiceLine)));
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, APPLICATION_JSON, 500);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT), nullValue());
  }

  @Test
  public void testUpdateValidInvoiceTransitionToPaid() {
    logger.info("=== Test transition invoice to paid and mixed releaseEncumbrance ===");

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.PAID);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, "", 204);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    validatePoLinesPaymentStatus();
  }

  private void validatePoLinesPaymentStatus() {
    final List<CompositePoLine> updatedPoLines = serverRqRs.get(PO_LINES, HttpMethod.PUT).stream().map(poLine -> poLine.mapTo(CompositePoLine.class)).collect(Collectors.toList());
    Map<String, List<InvoiceLine>> invoiceLines = serverRqRs.get(INVOICE_LINES, HttpMethod.GET).get(0).mapTo(InvoiceLineCollection.class).getInvoiceLines().stream().collect(groupingBy(InvoiceLine::getPoLineId));
    assertThat(invoiceLines.size(), equalTo(updatedPoLines.size()));

    for (Map.Entry<String, List<InvoiceLine>> poLineIdWithInvoiceLines : invoiceLines.entrySet()) {
      CompositePoLine poLine = updatedPoLines.stream()
        .filter(compositePoLine -> compositePoLine.getId().equals(poLineIdWithInvoiceLines.getKey()))
        .findFirst()
        .orElseThrow(NullPointerException::new);
      CompositePoLine.PaymentStatus expectedStatus = poLineIdWithInvoiceLines.getValue().stream()
        .anyMatch(InvoiceLine::getReleaseEncumbrance) ? CompositePoLine.PaymentStatus.FULLY_PAID : CompositePoLine.PaymentStatus.PARTIALLY_PAID;
      assertThat(expectedStatus, is(poLine.getPaymentStatus()));
    }
  }

  @Test
  public void testUpdateValidInvoiceTransitionToPaidReleaseEncumbranceFalse() {
    logger.info("=== Test transition invoice to paid and releaseEncumbrance false for all invoice lines ===");
    List<InvoiceLine> invoiceLines = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      invoiceLines.add(getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class));
    }

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    List<JsonObject> preparedInvoiceLines = invoiceLines.stream()
      .peek(invoiceLine -> {
        invoiceLine.setId(UUID.randomUUID().toString());
        invoiceLine.setInvoiceId(reqData.getId());
        invoiceLine.setPoLineId(EXISTENT_PO_LINE_ID);
        invoiceLine.setReleaseEncumbrance(false);
      })
      .map(JsonObject::mapFrom)
      .collect(Collectors.toList());

    serverRqRs.put(INVOICE_LINES, HttpMethod.POST, preparedInvoiceLines);

    reqData.setStatus(Invoice.Status.PAID);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, "", 204);

    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET), notNullValue());
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET).get(0).mapTo(InvoiceLineCollection.class).getTotalRecords(), equalTo(3));
    assertThat(serverRqRs.get(PO_LINES, HttpMethod.PUT), notNullValue());
    assertThat(serverRqRs.get(PO_LINES, HttpMethod.PUT), hasSize(1));
    assertThat(serverRqRs.get(PO_LINES, HttpMethod.PUT).get(0).mapTo(CompositePoLine.class).getPaymentStatus(), equalTo(CompositePoLine.PaymentStatus.PARTIALLY_PAID));
  }

  @Test
  public void testUpdateValidInvoiceTransitionToPaidReleaseEncumbranceFalseNoPoLineUpdate() {
    logger.info("=== Test transition invoice to paid and releaseEncumbrance false for invoice line without poLine update ===");
    List<InvoiceLine> invoiceLines = new ArrayList<>();
    List<CompositePoLine> poLines = new ArrayList<>();

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLines.add(invoiceLine);
    CompositePoLine poLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTENT_PO_LINE_ID)).mapTo(CompositePoLine.class);
    poLines.add(poLine);

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    invoiceLine.setPoLineId(EXISTENT_PO_LINE_ID);
    invoiceLine.setReleaseEncumbrance(false);

    poLine.setId(EXISTENT_PO_LINE_ID);
    poLine.setPaymentStatus(CompositePoLine.PaymentStatus.PARTIALLY_PAID);

    List<JsonObject> preparedInvoiceLines = invoiceLines.stream()
      .map(JsonObject::mapFrom)
      .collect(Collectors.toList());

    List<JsonObject> preparedPoLines = poLines.stream()
      .map(JsonObject::mapFrom)
      .collect(Collectors.toList());

    serverRqRs.put(INVOICE_LINES, HttpMethod.POST, preparedInvoiceLines);
    serverRqRs.put(PO_LINES, HttpMethod.POST, preparedPoLines);

    reqData.setStatus(Invoice.Status.PAID);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, "", 204);

    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET), notNullValue());
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET).get(0).mapTo(InvoiceLineCollection.class).getTotalRecords(), equalTo(1));
    assertThat(serverRqRs.get(PO_LINES, HttpMethod.PUT), nullValue());
  }

  @Test
  public void testUpdateValidInvoiceTransitionToPaidReleaseEncumbranceTrue() {
    logger.info("=== Test transition invoice to paid and releaseEncumbrance true for all invoice lines ===");
    List<InvoiceLine> invoiceLines = new ArrayList<>();
    List<CompositePoLine> poLines = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      invoiceLines.add(getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class));
      poLines.add(getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTENT_PO_LINE_ID)).mapTo(CompositePoLine.class));
    }

    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    for (int i = 0; i < 3; i++) {
      invoiceLines.get(i).setId(UUID.randomUUID().toString());
      invoiceLines.get(i).setInvoiceId(reqData.getId());
      String poLineId = UUID.randomUUID().toString();
      invoiceLines.get(i).setPoLineId(poLineId);
      poLines.get(i).setId(poLineId);
    }

    List<JsonObject> preparedInvoiceLines = invoiceLines.stream()
      .map(JsonObject::mapFrom)
      .collect(Collectors.toList());
    List<JsonObject> preparedPoLines = poLines.stream()
      .map(JsonObject::mapFrom)
      .collect(Collectors.toList());

    serverRqRs.put(INVOICE_LINES, HttpMethod.POST, preparedInvoiceLines);
    serverRqRs.put(PO_LINES, HttpMethod.POST, preparedPoLines);

    reqData.setStatus(Invoice.Status.PAID);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, "", 204);

    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET), notNullValue());
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET).get(0).mapTo(InvoiceLineCollection.class).getTotalRecords(), equalTo(3));
    assertThat(serverRqRs.get(PO_LINES, HttpMethod.PUT), notNullValue());
    assertThat(serverRqRs.get(PO_LINES, HttpMethod.PUT), hasSize(3));
    serverRqRs.get(PO_LINES, HttpMethod.PUT).stream()
      .map(entries -> entries.mapTo(CompositePoLine.class))
      .forEach(compositePoLine -> assertThat(compositePoLine.getPaymentStatus(), equalTo(CompositePoLine.PaymentStatus.FULLY_PAID)));

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
}
