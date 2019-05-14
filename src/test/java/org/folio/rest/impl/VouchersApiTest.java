package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.junit.Assert.assertEquals;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Voucher;
import org.junit.Test;

public class VouchersApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(VouchersApiTest.class);

  private static final String VOUCHER_PATH = "/voucher/vouchers";
  private static final String VOUCHER_ID_PATH = VOUCHER_PATH + "/%s";
  static final String VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "vouchers/";
  private static final String VOUCHERS_LIST_PATH = VOUCHER_MOCK_DATA_PATH + "vouchers.json";

  static final String BAD_QUERY = "unprocessableQuery";
  static final String EXISTING_VENDOR_INV_NO = "existingVendorInvoiceNo";
  static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  private static final String BAD_VOUCHER_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";

  @Test
  public void testGetVouchersVoucherById() throws IOException {
    logger.info("=== Test Get Voucher By Id ===");

    JsonObject vouchersList = new JsonObject(getMockData(VOUCHERS_LIST_PATH));
    String id = vouchersList.getJsonArray("vouchers")
      .getJsonObject(0)
      .getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHERS_LIST_PATH, id));

    final Voucher resp = verifySuccessGet(String.format(VOUCHER_ID_PATH, id), Voucher.class);

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

}
