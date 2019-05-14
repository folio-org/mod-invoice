package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.junit.Assert.assertEquals;


public class VoucherLinesApiTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(VoucherLinesApiTest.class);

  static final Header NON_EXIST_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoicetest");
  static final String VOUCHER_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "voucherLines/";
  private static final String VOUCHER_LINES_LIST_PATH = VOUCHER_LINES_MOCK_DATA_PATH + "voucher_lines.json";
  private static final String VOUCHER_LINES_PATH = "/voucher/voucher-lines";
  private static final String NOT_FOUND_VOUCHER_LINE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  
  @Test
  public void getVouchersVoucherLinesByIdTest() throws Exception {
    logger.info("=== Test Get Voucher line By Id ===");

    JsonObject voucherLinesList = new JsonObject(getMockData(VOUCHER_LINES_LIST_PATH));
    String id = voucherLinesList.getJsonArray("voucherLines").getJsonObject(0).getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", VOUCHER_LINES_LIST_PATH, id));

    final VoucherLine resp = verifySuccessGet(VOUCHER_LINES_PATH + "/" + id, VoucherLine.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void getVouchersVoucherLinesByIdNotFoundTest() throws MalformedURLException {
    logger.info("=== Test Get Voucher line by Id - 404 Not found ===");

    final Response resp = verifyGet(VOUCHER_LINES_PATH + "/" + NOT_FOUND_VOUCHER_LINE_ID, APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(NOT_FOUND_VOUCHER_LINE_ID, actual);
  }
}
