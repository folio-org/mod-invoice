package org.folio.rest.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.Test;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesApiTest.ID_FOR_INTERNAL_SERVER_ERROR;

public class InvoiceLinesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceLinesApiTest.class);

  private static final String INVOICE_LINE_ID_PATH = "/invoice/invoice-lines/%s";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_LINE_SAMPLE_PATH = "mockdata/invoiceLines/invoice_line.json";

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
  public void getInvoicingInvoiceLinesByIdTest() {
    verifyGet(String.format(INVOICE_LINE_ID_PATH, _UUID), TEXT_PLAIN, 500);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, _UUID), TEXT_PLAIN, 500);
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
