package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import io.restassured.http.Header;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.Test;

public class VoucherNumberApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(VoucherNumberApiTest.class);

  static final String VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "vouchers/";
  private static final String VALID_START_VALUE = "101";
  private static final String INVALID_NEGATIVE_START_VALUE = "-12";
  private static final String INVALID_FLOATING_START_VALUE = "5.2";

  private static final String VOUCHER_START_PATH = "/voucher/voucher-number/start" + "/%s";
  static final Header EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10 = new Header(OKAPI_HEADER_TENANT, "test_diku_limit_10");

  @Test
  public void testPostVoucherStartValue() {
    logger.info("=== Test POST Voucher valid start value - 200 Request ===");

    verifyPostStartValueResponse(String.format(VOUCHER_START_PATH, VALID_START_VALUE), "",
        prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10), "", 204);
  }

  @Test
  public void testPostVoucherNegativeStartValueBadRequest() {
    logger.info("=== Test POST Voucher negative start value - 404 Bad Request ===");

    verifyPostStartValueResponse(String.format(VOUCHER_START_PATH, INVALID_NEGATIVE_START_VALUE), "",
        prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10), "", 404);
  }

  @Test
  public void testPostVoucherFloatingStartValueBadRequest() {
    logger.info("=== Test POST Voucher floating start value - 404 Bad Request ===");

    verifyPostStartValueResponse(String.format(VOUCHER_START_PATH, INVALID_FLOATING_START_VALUE), "",
        prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10), "", 404);
  }
}
