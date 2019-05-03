package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.Test;

import java.net.MalformedURLException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesApiTest.ID_FOR_INTERNAL_SERVER_ERROR;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.junit.Assert.assertEquals;

public class InvoiceLinesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceLinesApiTest.class);

  private static final String INVOICE_LINE_ID_PATH = "/invoice/invoice-lines/%s";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_LINE_SAMPLE_PATH = "mockdata/invoiceLines/invoice_line.json";

  static final String INVOICE_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoiceLines/";
  private static final String INVOICE_LINES_LIST_PATH = INVOICE_LINES_MOCK_DATA_PATH + "invoice_lines.json";

  private static final String BAD_INVOICE_LINE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";

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
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, VALID_UUID), TEXT_PLAIN, 500);
  }

  @Test
  public void putInvoicingInvoiceLinesByIdTest() {
    InvoiceLine reqData = getMockAsJson(INVOICE_LINE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_LINE_ID_PATH, id), jsonBody, prepareHeaders(X_OKAPI_TENANT), TEXT_PLAIN, 500);
  }

  @Test
  public void postInvoicingInvoiceLinesTest() throws Exception {
    String jsonBody = getMockData(INVOICE_LINE_SAMPLE_PATH);

    verifyPostResponse(INVOICE_LINES_PATH, jsonBody, prepareHeaders(X_OKAPI_TENANT), TEXT_PLAIN, 500);
  }
}
