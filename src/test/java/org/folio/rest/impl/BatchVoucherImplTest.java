package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class BatchVoucherImplTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(BatchVoucherImplTest.class);

  public static final String BATCH_VOUCHER_PATH = "/batch-voucher/batch-vouchers";
  public static final String BATCH_VOUCHER_ID_PATH = BATCH_VOUCHER_PATH + "/%s";
  private static final String BAD_BATCH_VOUCHER_ID = "35657479-83b9-4760-9c39-b58dcd02ee11";
  private static final String VALID_BATCH_VOUCHER_ID ="35657479-83b9-4760-9c39-b58dcd02ee14";
  public static final String BATCH_VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVouchers/";
  private static final String BATCH_VOUCHER_SAMPLE_PATH = BATCH_VOUCHER_MOCK_DATA_PATH + VALID_BATCH_VOUCHER_ID + ".json";
  public static final String NON_EXISTING_BATCH_VOUCHER_ID = "12345678-83b9-4760-9c39-b58dcd02ee16";

  // ISO 8601 date format pattern (e.g., "2019-12-07T00:01:04.000+00:00")
  private static final Pattern ISO_DATE_PATTERN = Pattern.compile(
    "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}"
  );

  @Test
  public void testGetJSONShouldReturnBatchVoucherInById() {
    logger.info("=== Test Get Batch voucher by Id - JSON format with date validation ===");
    final Response resp = verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID),
      Headers.headers(ACCEPT_JSON_HEADER, X_OKAPI_TENANT), APPLICATION_JSON, 200);

    BatchVoucher batchVoucher = resp.getBody().as(BatchVoucher.class);
    String actual = batchVoucher.getId();

    assertEquals(VALID_BATCH_VOUCHER_ID, actual);
    assertAllFieldsExistAndEqual(getMockAsJson(BATCH_VOUCHER_SAMPLE_PATH), resp);
    assertValidDateFormats(resp);
  }

  @Test
  public void testGetXMLShouldReturnBatchVoucherInById() {
    logger.info("=== Test Get Batch voucher by Id - 404 Not found ===");
    final Response resp = verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID)
      , Headers.headers(ACCEPT_XML_HEADER, X_OKAPI_TENANT), APPLICATION_XML, 200);
    String actual = resp.getBody()
      .as(BatchVoucherType.class)
      .getId();
    logger.info("Id found: " + actual);

    assertEquals(VALID_BATCH_VOUCHER_ID, actual);
  }

  @Test
  public void testGetShouldReturn400IfAcceptHeaderIsAbsent() {
    logger.info("=== Test Get Batch voucher by Id - 400 Bad request ===");
    verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID)
      , Headers.headers(X_OKAPI_TENANT), APPLICATION_JSON, 400);
  }

  @Test
  public void testGetShouldReturn404IfBatchVoucherISAbsent() {
    logger.info("=== Test Get Batch voucher by Id - 404 Bad request ===");
    verifyGet(String.format(BATCH_VOUCHER_ID_PATH, NON_EXISTING_BATCH_VOUCHER_ID)
      , Headers.headers(ACCEPT_JSON_HEADER, X_OKAPI_TENANT), APPLICATION_JSON, 404);
  }

  private void assertValidDateFormats(Response resp) {
    String plainJson = resp.getBody().asString();
    JsonObject properDateJson = new JsonObject(plainJson);

    // Check that date fields are in ISO format, not epoch
    validateDateFormat(properDateJson.getString("created"), "created");
    validateDateFormat(properDateJson.getString("start"), "start");
    validateDateFormat(properDateJson.getString("end"), "end");

    // Check voucher dates within batchedVouchers array
    JsonObject firstVoucher = properDateJson.getJsonArray("batchedVouchers").getJsonObject(0);
    validateDateFormat(firstVoucher.getString("disbursementDate"), "disbursementDate");
    validateDateFormat(firstVoucher.getString("invoiceDate"), "invoiceDate");
    validateDateFormat(firstVoucher.getString("voucherDate"), "voucherDate");
  }

  /**
   * Validates that a date string is in proper ISO 8601 format and not in epoch format
   */
  private void validateDateFormat(String dateString, String fieldName) {
    assertTrue(ISO_DATE_PATTERN.matcher(dateString).matches(),
      "Field '" + fieldName + "' should be in ISO format but was: " + dateString);

    // Additional check: ensure it's not a long number (epoch format)
    try {
      Long.parseLong(dateString);
      throw new AssertionError("Field '" + fieldName + "' appears to be in epoch format: " + dateString);
    } catch (NumberFormatException e) {
      // This is expected - the date should not be parseable as a long
    }
  }
}
