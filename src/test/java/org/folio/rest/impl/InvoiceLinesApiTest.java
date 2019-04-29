package org.folio.rest.impl;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.Test;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

public class InvoiceLinesApiTest extends ApiTestBase {

  private static final String INVOICE_LINE_ID_PATH = "/invoice/invoice-lines/%s";
  private static final String INVOICE_LINES_PATH = "/invoice/invoice-lines";
  private static final String INVOICE_LINE_SAMPLE_PATH = "mockdata/invoiceLines/invoice_line.json";


  @Test
  public void getInvoicingInvoiceLinesTest() {
    verifyGet(INVOICE_LINES_PATH, TEXT_PLAIN, 500);
  }

  @Test
  public void getInvoicingInvoiceLinesByIdTest() {
    verifyGet(String.format(INVOICE_LINE_ID_PATH, UUID), TEXT_PLAIN, 500);
  }

  @Test
  public void deleteInvoicingInvoiceLinesByIdTest() {
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, UUID), TEXT_PLAIN, 500);
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
