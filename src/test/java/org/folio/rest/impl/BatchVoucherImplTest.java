package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.folio.config.ApplicationConfig;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchVoucherImplTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(BatchVoucherImplTest.class);

  public static final String BATCH_VOUCHER_PATH = "/batch-vouchers";
  public static final String BATCH_VOUCHER_ID_PATH = BATCH_VOUCHER_PATH + "/%s";
  private static final String BAD_BATCH_VOUCHER_ID = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  private static final String VALID_BATCH_VOUCHER_ID ="35657479-83b9-4760-9c39-b58dcd02ee14";
  private static final String INVALID_BATCH_VOUCHER_ID = "invalidBatchGroupId";
  static final String BATCH_VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVouchers/";
  public static final String BATCH_GROUP_SAMPLE_PATH = BATCH_VOUCHER_MOCK_DATA_PATH + VALID_BATCH_VOUCHER_ID + ".json";

  @Test
  public void testGetBatchGroupsByIdFound() {
    logger.info("=== Test Get Batch-group by Id - 404 Not found ===");
    try {
      final Response resp1 = verifyGet(String.format("/batch-voucher/batch-vouchers/35657479-83b9-4760-9c39-b58dcd02ee14", VALID_BATCH_VOUCHER_ID), APPLICATION_JSON, 200);
      String actual = resp1.getBody()
        .as(BatchVoucher.class)
        .getId();
      logger.info("Id not found: " + actual);

      assertEquals(VALID_BATCH_VOUCHER_ID, actual);
    }catch (Exception e)
    {
      logger.error("dddddddddddddddddddd");
    }

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
