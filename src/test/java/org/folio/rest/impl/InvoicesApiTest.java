package org.folio.rest.impl;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Invoice;
import org.junit.Test;

import java.io.IOException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.rest.impl.MockServer.ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.INVOICE_NUMBER_ERROR_X_OKAPI_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

public class InvoicesApiTest extends ApiTestBase {

	private static final Logger logger = LoggerFactory.getLogger(InvoicesApiTest.class);

	private static final String INVOICE_ID_PATH = "/invoice/invoices/%s";
	private static final String INVOICE_PATH = "/invoice/invoices";

	private static final String INVOICE_SAMPLE_PATH = "mockdata/invoices/invoice.json";

  @Test
  public void getInvoicingInvoicesTest() {

    verifyGet(INVOICE_PATH, TEXT_PLAIN, 500);
  }

  @Test
  public void getInvoicingInvoicesByIdTest() {
    logger.info("=== Test get invoice by id ===");
    verifyGet(String.format(INVOICE_ID_PATH, UUID), TEXT_PLAIN, 500);
  }

  @Test
  public void putInvoicingInvoicesByIdTest() {
    logger.info("=== Test update invoice by id ===");
  	Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
  	String jsonBody = JsonObject.mapFrom(reqData).encode();
  	verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, prepareHeaders(X_OKAPI_TENANT), TEXT_PLAIN, 500);
  }

  @Test
  public void deleteInvoicingInvoicesByIdTest() {
    logger.info("=== Test delete invoice by id ===");
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, UUID), TEXT_PLAIN, 500);
  }

  @Test
  public void postInvoicingInvoicesTest() throws Exception {
    logger.info("=== Test create invoice without id and folioInvoiceNo ===");
    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(INVOICE_SAMPLE_PATH);
    final Invoice respData = verifyPostResponse(INVOICE_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 200).as(Invoice.class);
    String poId = respData.getId();
    String folioInvoiceNo = respData.getFolioInvoiceNo();

    assertThat(poId, notNullValue());
    assertThat(folioInvoiceNo, notNullValue());

    assertThat(MockServer.serverRqRs.get(FOLIO_INVOICE_NUMBER, HttpMethod.GET), hasSize(1));
  }

  @Test
  public void postInvoicingInvoicesTestErrorFromStorage() throws Exception {
    logger.info("=== Test create invoice without with error from storage on saving invoice  ===");
    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(INVOICE_SAMPLE_PATH);
    verifyPostResponse(INVOICE_PATH, body, prepareHeaders(ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);

    assertThat(MockServer.serverRqRs.get(FOLIO_INVOICE_NUMBER, HttpMethod.GET), hasSize(1));
  }

  @Test
  public void postInvoicingInvoicesWithInvoiceNumberGenerationFail() throws IOException {
    logger.info("=== Test create invoice without with error from storage on folioInvoiceNo generation  ===");
    Invoice reqData = getMockAsJson(INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(INVOICE_SAMPLE_PATH);
    verifyPostResponse(INVOICE_PATH, body, prepareHeaders(INVOICE_NUMBER_ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);

  }

}
