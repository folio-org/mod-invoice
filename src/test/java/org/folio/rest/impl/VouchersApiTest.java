package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.rest.impl.MockServer.ERROR_X_OKAPI_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class VouchersApiTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(VouchersApiTest.class);

  static final String VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "vouchers/";

  private static final String VOUCHER_PATH = "/voucher/vouchers";
  private static final String VOUCHER_ID_PATH = VOUCHER_PATH + "/%s";
  static final String VOUCHERS_LIST_PATH = VOUCHER_MOCK_DATA_PATH + "vouchers.json";
  private static final String BAD_VOUCHER_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String INVALID_VOUCHER_ID = "invalidVoucherId";
  private static final String VALID_START_VALUE = "101";
  private static final String INVALID_NEGATIVE_START_VALUE = "-12";
  private static final String INVALID_START_VALUE_QUERY = "unprocessableQuery";
  private static final String VOUCHER_START_PATH = "/voucher/voucher-number/start" + "/%s";
  private static final String VOUCHER_NUMBER_START_PATH = "/voucher/voucher-number/start";
  private static final String BAD_QUERY = "unprocessableQuery";

  @Test
  public void testGetVoucherVouchers() {
    logger.info("=== Test Get Vouchers by without query - get 200 by successful retrieval of vouchers ===");

    final VoucherCollection resp = verifySuccessGet(VOUCHER_PATH, VoucherCollection.class);
    assertEquals(2, resp.getTotalRecords()
      .intValue());
  }

  @Test
  public void testGetVoucherVouchersWithQueryParam() {
    logger.info("=== Test Get Vouchers with query - get 200 by successful retrieval of vouchers by query ===");

    String endpointQuery = String.format("%s?query=%s==%s", VOUCHER_PATH, INVOICE_ID, APPROVED_INVOICE_ID);
    final VoucherCollection resp = verifySuccessGet(endpointQuery, VoucherCollection.class);
    assertEquals(1, resp.getTotalRecords()
      .intValue());
  }

  @Test
  public void testGetVouchersBadQuery() {
    logger.info("=== Test Get Vouchers by query - unprocessable query to emulate 400 from storage ===");

    String endpointQuery = String.format("%s?query=%s", VOUCHER_PATH, BAD_QUERY);
    verifyGet(endpointQuery, APPLICATION_JSON, 400);
  }

  @Test
  public void testGetVouchersVoucherById() throws IOException {
    logger.info("=== Test Get Voucher By Id ===");

    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    String id = vouchersList.getJsonArray("vouchers")
      .getJsonObject(0)
      .getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHERS_LIST_PATH, id));

    final Voucher resp = verifySuccessGet(String.format(VOUCHER_ID_PATH, id), Voucher.class);

    assertNotNull(resp.getVendorAddress());

    assertThat(resp.getVendorAddress())
        .hasFieldOrPropertyWithValue("addressLine1", "10 Estes Street")
        .hasFieldOrPropertyWithValue("addressLine2", "22")
        .hasFieldOrPropertyWithValue("city", "Ipswich")
        .hasFieldOrPropertyWithValue("stateRegion", "MA")
        .hasFieldOrPropertyWithValue("zipCode", "01938")
        .hasFieldOrPropertyWithValue("country", "USA");

    logger.info(JsonObject.mapFrom(resp)
      .encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void testGetVouchersVoucherByIdNotFound() {
    logger.info("=== Test Get Voucher by Id - 404 Not found ===");

    final Response resp = verifyGet(String.format(VOUCHER_ID_PATH, BAD_VOUCHER_ID), APPLICATION_JSON, 404);

    String actual = resp.getBody()
      .as(Errors.class)
      .getErrors()
      .get(0)
      .getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_VOUCHER_ID, actual);
  }

  @Test
  public void testGetVouchersVoucherByIdInvalidFormat() {
    logger.info("=== Test Get Voucher by Id - 400 Bad request ===");

    final Response resp = verifyGet(String.format(VOUCHER_ID_PATH, INVALID_VOUCHER_ID), TEXT_PLAIN, 400);

    String actual = resp.getBody().asString();
    logger.info(actual);

    assertNotNull(actual);
    assertTrue(actual.contains(INVALID_VOUCHER_ID));
  }

  @Test
  public void testPostVoucherStartValue() {
    logger.info("=== Test POST Voucher valid start value - 200 Request ===");

    verifyPostResponse(String.format(VOUCHER_START_PATH, VALID_START_VALUE), "",
        prepareHeaders(X_OKAPI_TENANT), "", 204);
  }

  @Test
  public void testPostVoucherNegativeStartValueBadRequest() {
    logger.info("=== Test POST Voucher negative start value - 400 Bad Request ===");

    verifyPostResponse(String.format(VOUCHER_START_PATH, INVALID_NEGATIVE_START_VALUE), "",
        prepareHeaders(X_OKAPI_TENANT), "", 400);
  }

  @Test
  public void testPostVoucherStartValueInvalidQuery() {
    logger.info("=== Test POST Voucher start value invalid query - 400 Bad request ===");

    verifyPostResponse(String.format(VOUCHER_START_PATH, INVALID_START_VALUE_QUERY), "",
        prepareHeaders(X_OKAPI_TENANT), "", 400);
  }

  @Test
  public void testGetVoucherNumberStart() {
    logger.info("=== Test Get Voucher number start value - success ===");

    SequenceNumber number = verifyGet(VOUCHER_NUMBER_START_PATH, APPLICATION_JSON, 200).as(SequenceNumber.class);
    assertThat(number.getSequenceNumber()).isNotBlank();
  }

  @Test
  public void testGetVoucherNumberStartInternalError() {
    logger.info("=== Test Get Voucher number start value - 500 Internal Server Error ===");

    verifyGet(VOUCHER_NUMBER_START_PATH, prepareHeaders(X_OKAPI_URL, ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);
  }

  @Test
  public void testVoucherUpdateProtectedFields() throws IOException {
    logger.info("=== Test Put Voucher with edited protected fields - 400 Bad request ===");

    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    JsonObject voucher = vouchersList.getJsonArray("vouchers").getJsonObject(0);
    String id = voucher.getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHERS_LIST_PATH, id));

    voucher.put("systemCurrency", "EUR");
    voucher.put("amount", 25.5);

    verifyPut(String.format(VOUCHER_ID_PATH, id), voucher, "", 400);
  }

  @Test
  public void testUpdateVoucherEditableFields() throws IOException {
    logger.info("=== Test Put Voucher with changes in editable fields - 204 No content ===");

    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    JsonObject voucher = vouchersList.getJsonArray("vouchers").getJsonObject(0);
    String id = voucher.getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHERS_LIST_PATH, id));

    voucher.put("voucherNumber", "999");
    voucher.put("disbursementAmount", 10);
    voucher.put("disbursementDate", "2020-02-26T00:00:00.000+0000");

    final Response resp = verifyPut(String.format(VOUCHER_ID_PATH, id), voucher, "", 204);
    logger.info(resp.getBody().prettyPrint());
  }

  @Test
  public void testUpdateVoucherEditableFieldsWithWrongValues() throws IOException {
    logger.info("=== Test Put Voucher with wrong values in editable fields - 400 Bad request ===");

    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    JsonObject voucher = vouchersList.getJsonArray("vouchers").getJsonObject(0);
    String id = voucher.getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHERS_LIST_PATH, id));

    voucher.put("voucherNumber", 999);
    voucher.put("disbursementAmount", "10");
    voucher.put("disbursementDate", "wrong date");

    final Response resp = verifyPut(String.format(VOUCHER_ID_PATH, id), voucher, "", 400);
  }

  @Test
  public void testPutVouchersVoucherByIdInvalidFormat() throws IOException {
    logger.info("=== Test Put Voucher by Id - 400 Bad request ===");

    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    JsonObject voucher = vouchersList.getJsonArray("vouchers").getJsonObject(0);

    final Response resp = verifyPut(String.format(VOUCHER_ID_PATH, INVALID_VOUCHER_ID), voucher, TEXT_PLAIN, 400);

    String actual = resp.getBody().asString();
    logger.info(actual);

    assertNotNull(actual);
    assertTrue(actual.contains(INVALID_VOUCHER_ID));
  }
}
