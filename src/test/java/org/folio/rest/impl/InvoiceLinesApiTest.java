package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.rest.impl.MockServer.INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT;


public class InvoiceLinesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceLinesApiTest.class);

  private static final String INVOICE_LINE_ID_PATH = "/invoice/invoice-lines/%s";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_LINE_SAMPLE_PATH = "mockdata/invoiceLines/invoice_line.json";
  private static final String INVOICE_LINE_MISSING_ID_PATH = "mockdata/invoiceLines/invoice_line_missing_invoice_id.json";
  private static final String BAD_INVOICE_LINE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String INVOICE_ID = "invoiceId";
  private static final String NULL = "null";
  static final Header NON_EXIST_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoicetest");
  static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoiceLines/";
  private static final String INVOICE_LINES_LIST_PATH = INVOICE_LINES_MOCK_DATA_PATH + "invoice_lines.json";



  @Test
  public void getInvoicingInvoiceLinesTest() {
    verifyGet(INVOICE_LINES_PATH, TEXT_PLAIN, 500);
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
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, UUID), TEXT_PLAIN, 500);
  }

  @Test
  public void postInvoicingInvoiceLinesTest() throws Exception {
    logger.info("=== Test create invoice line - 201 successfully created ===");
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setId(null);
    reqData.setInvoiceLineNumber(null);
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

    Errors resp = verifyPostResponse(INVOICE_LINES_PATH, getMockData(INVOICE_LINE_MISSING_ID_PATH),
      prepareHeaders(NON_EXIST_CONFIG_X_OKAPI_TENANT), APPLICATION_JSON, 422).as(Errors.class);

    assertEquals(1, resp.getErrors().size());
    assertEquals(INVOICE_ID, resp.getErrors().get(0).getParameters().get(0).getKey());
    assertEquals(NULL, resp.getErrors().get(0).getParameters().get(0).getValue());
  }
  
  @Test
  public void testPutInvoicingInvoiceLinesByIdTest() throws Exception {
    String reqData = getMockData(INVOICE_LINE_SAMPLE_PATH);

    verifyPut(String.format(INVOICE_LINE_ID_PATH, UUID), reqData, "" , 204);
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
    String endpoint = String.format(INVOICE_LINE_ID_PATH, UUID)
        + String.format("?%s=%s", LANG_PARAM, INVALID_LANG);

    verifyPut(endpoint, reqData, TEXT_PLAIN, 400);

  }
}
