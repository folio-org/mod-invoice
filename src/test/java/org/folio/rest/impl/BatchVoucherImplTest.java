package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.folio.rest.jaxrs.model.Errors;
import org.junit.Test;

import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchVoucherImplTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(BatchVoucherImplTest.class);

  public static final String BATCH_VOUCHER_PATH = "/batch-vouchers";
  public static final String BATCH_VOUCHER_ID_PATH = BATCH_VOUCHER_PATH + "/%s";
  private static final String BAD_BATCH_VOUCHER_ID = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  private static final String VALID_BATCH_VOUCHER_ID ="test_batch_voucher";
  private static final String INVALID_BATCH_VOUCHER_ID = "invalidBatchGroupId";
  static final String BATCH_VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVouchers/";
  public static final String BATCH_GROUP_SAMPLE_PATH = BATCH_VOUCHER_MOCK_DATA_PATH + VALID_BATCH_VOUCHER_ID + ".json";


  @Test
  public void testGetBatchGroupsByIdFound() {
    logger.info("=== Test Get Batch-group by Id - 404 Not found ===");

    final Response resp = verifyGet(String.format(BATCH_VOUCHER_ID_PATH, VALID_BATCH_VOUCHER_ID), APPLICATION_XML, 200);

    String actual = resp.getBody()
      .as(Errors.class)
      .getErrors()
      .get(0)
      .getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_BATCH_VOUCHER_ID, actual);
  }

  @Test
  public void testGetBatchGroupsByIdInvalidFormat() {
    logger.info("=== Test Get Batch group by Id - 400 Bad request ===");

    final Response resp = verifyGet(String.format(BATCH_VOUCHER_ID_PATH, INVALID_BATCH_VOUCHER_ID), TEXT_PLAIN, 400);

    String actual = resp.getBody().asString();
    logger.info(actual);

    assertNotNull(actual);
    assertTrue(actual.contains(INVALID_BATCH_VOUCHER_ID));
  }
}
