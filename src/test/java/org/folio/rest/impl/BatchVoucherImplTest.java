package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.junit.Test;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchVoucherImplTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(BatchVoucherImplTest.class);

  public static final String BATCH_VOUCHER_PATH = "/batch-voucher/batch-vouchers";
  public static final String BATCH_VOUCHER_ID_PATH = BATCH_VOUCHER_PATH + "/%s";
  private static final String BAD_BATCH_VOUCHER_ID = "35657479-83b9-4760-9c39-b58dcd02ee11";
  private static final String VALID_BATCH_VOUCHER_ID ="35657479-83b9-4760-9c39-b58dcd02ee14";
  public static final String BATCH_VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVouchers/";
  private static final String BATCH_VOUCHER_SAMPLE_PATH = BATCH_VOUCHER_MOCK_DATA_PATH + VALID_BATCH_VOUCHER_ID + ".json";

  @Test
  public void testGetJSONShouldReturnBatchVoucherInById() {
    logger.info("=== Test Get Batch-group by Id - 404 Not found ===");
      final Response resp = verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID)
        , Headers.headers(ACCEPT_JSON_HEADER, X_OKAPI_TENANT), APPLICATION_JSON, 200);
      String actual = resp.getBody()
        .as(BatchVoucher.class)
        .getId();
      logger.info("Id found: " + actual);

      assertEquals(VALID_BATCH_VOUCHER_ID, actual);
      assertAllFieldsExistAndEqual(getMockAsJson(BATCH_VOUCHER_SAMPLE_PATH), resp);
  }

  @Test
  public void testGetXMLShouldReturnBatchVoucherInById() {
    logger.info("=== Test Get Batch-group by Id - 404 Not found ===");
    final Response resp = verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID)
      , Headers.headers(ACCEPT_XML_HEADER, X_OKAPI_TENANT), APPLICATION_XML, 200);
    String actual = resp.getBody()
      .as(BatchVoucherType.class)
      .getId();
    logger.info("Id found: " + actual);

    assertEquals(VALID_BATCH_VOUCHER_ID, actual);
    assertAllFieldsExistAndEqual(getMockAsJson(BATCH_VOUCHER_SAMPLE_PATH), resp);
  }

  @Test
  public void testGetShouldReturn400IfAcceptHeaderIsAbsent() {
    logger.info("=== Test Get Batch group by Id - 400 Bad request ===");
    verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID)
      , Headers.headers(X_OKAPI_TENANT), APPLICATION_JSON, 400);
  }

}
