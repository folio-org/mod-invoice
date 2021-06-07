package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class VoucherLinesApiTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(VoucherLinesApiTest.class);

  static final String VOUCHER_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "voucherLines/";
  static final String VALID_VOUCHER_LINE_UUID = "6d615cfd-38b6-4182-8178-8e6ffee7c01e";
  private static final String VOUCHER_LINES_LIST_PATH = VOUCHER_LINES_MOCK_DATA_PATH + "voucher_lines.json";
  private static final String VOUCHER_LINE_PATH = VOUCHER_LINES_MOCK_DATA_PATH + "voucher_line.json";
  private static final String VOUCHER_LINES_PATH = "/voucher/voucher-lines";
  private static final String VOUCHER_LINES_ID_PATH = "/voucher/voucher-lines" + "/%s";
  private static final String NOT_FOUND_VOUCHER_LINE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String INVALID_VOUCHER_LINE_ID = "invalidVoucherLineId";

  @Test
  public void getInvoicingInvoiceLinesTest() {
    verifyGet(VOUCHER_LINES_PATH, APPLICATION_JSON, 200);
  }

  @Test
  public void testGetInvoiceLinesInternalServerError() {
    logger.info("=== Test Get Voucher Lines by query - emulating 500 from storage ===");

    String endpointQuery = String.format("%s?query=%s", VOUCHER_LINES_PATH, ID_FOR_INTERNAL_SERVER_ERROR);

    verifyGet(endpointQuery, APPLICATION_JSON, 500);
  }

  @Test
  public void testGetInvoiceLinesBadQuery() {
    logger.info("=== Test Get Voucher Lines by query - unprocessable query to emulate 400 from storage ===");

    String endpointQuery = String.format("%s?query=%s", VOUCHER_LINES_PATH, BAD_QUERY);

    verifyGet(endpointQuery, APPLICATION_JSON, 400);

  }

  @Test
  public void testGetVouchersVoucherLinesById() throws Exception {
    logger.info("=== Test Get Voucher line By Id ===");

    JsonObject voucherLinesList = new JsonObject(getMockData(VOUCHER_LINES_LIST_PATH));
    String id = voucherLinesList.getJsonArray("voucherLines").getJsonObject(0).getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHER_LINES_LIST_PATH, id));

    final VoucherLine resp = verifySuccessGet(String.format(VOUCHER_LINES_ID_PATH, id), VoucherLine.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    Assertions.assertEquals(id, resp.getId());
  }

  @Test
  public void testGetVouchersVoucherLinesByIdNotFoundTest() {
    logger.info("=== Test Get Voucher line by Id - 404 Not found ===");

    final Response resp = verifyGet(String.format(VOUCHER_LINES_ID_PATH, NOT_FOUND_VOUCHER_LINE_ID), APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    Assertions.assertEquals(NOT_FOUND_VOUCHER_LINE_ID, actual);
  }

  @Test
  public void testGetVouchersVoucherLineByIdInvalidFormat() {
    logger.info("=== Test Get Voucher line by Id - 400 Bad request ===");
    final Response resp = verifyGet(String.format(VOUCHER_LINES_ID_PATH, INVALID_VOUCHER_LINE_ID), TEXT_PLAIN, 400);
    String actual = resp.getBody().asString();
    logger.info(actual);

    Assertions.assertNotNull(actual);
    Assertions.assertTrue(actual.contains(INVALID_VOUCHER_LINE_ID));
  }

  @Test
  public void testPutVouchersVoucherLinesByIdTest() throws Exception {
    String reqData = getMockData(VOUCHER_LINE_PATH);

    verifyPut(String.format(VOUCHER_LINES_ID_PATH, VALID_VOUCHER_LINE_UUID), reqData, "", 204);
  }

  @Test
  public void testPutVouchersVoucherLinesByNonExistentId() {
    VoucherLine reqData = getMockAsJson(VOUCHER_LINE_PATH).mapTo(VoucherLine.class);
    reqData.setId(ID_DOES_NOT_EXIST);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(VOUCHER_LINES_ID_PATH, ID_DOES_NOT_EXIST), jsonBody, APPLICATION_JSON, 404);
  }

  @Test
  public void testPutVouchersVoucherLinesInvalidIdFormat() {
    VoucherLine reqData = getMockAsJson(VOUCHER_LINE_PATH).mapTo(VoucherLine.class);
    reqData.setId(ID_BAD_FORMAT);
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(VOUCHER_LINES_ID_PATH, ID_BAD_FORMAT), jsonBody, APPLICATION_JSON, 422);
  }
}
