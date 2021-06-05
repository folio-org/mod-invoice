package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.folio.rest.impl.MockServer.getBatchVoucherExportUpdates;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchVoucherExportCollection;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class BatchVoucherExportsApiTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(BatchVoucherExportsApiTest.class);

  public static final String BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExports/";
  public static final String BATCH_VOUCHER_EXPORTS_LIST_PATH = BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH + "batch_voucher_exports_collection.json";
  private static final String BATCH_VOUCHER_EXPORTS_PATH = "/batch-voucher/batch-voucher-exports";
  private static final String BATCH_VOUCHER_EXPORTS_ID_PATH = BATCH_VOUCHER_EXPORTS_PATH + "/%s";
  private static final String BAD_BATCH_VOUCHER_EXPORTS_ID = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  private static final String VALID_BATCH_VOUCHER_EXPORTS_ID ="566c9156-e52f-4597-9fee-5ddac91d14f2";
  private static final String BAD_BATCH_VOUCHER_GROUP_ID =  "e91d44e4-ae4f-401a-b355-3ea44f57a621";
  private static final String BATCH_VOUCHER_EXPORT_SAMPLE_PATH = BATCH_VOUCHER_EXPORTS_MOCK_DATA_PATH + VALID_BATCH_VOUCHER_EXPORTS_ID + ".json";
  private static final String BATCH_VOUCHER_EXPORT_UPLOAD_ENDPOINT_PATH = String
    .format("/batch-voucher/batch-voucher-exports/%s/upload", BAD_BATCH_VOUCHER_EXPORTS_ID);
  private static final String BATCH_VOUCHER_EXPORT_SCHEDULE_ENDPOINT_PATH = String
    .format("/batch-voucher/batch-voucher-exports/scheduled");

  @Test
  public void testPostBatchVoucherExports() {
    logger.info(" === Test POST Batch Voucher Exports === ");
    String jsonBody = getMockAsJson(BATCH_VOUCHER_EXPORT_SAMPLE_PATH).toString();
    Response resp = verifySuccessPost(BATCH_VOUCHER_EXPORTS_PATH, jsonBody);
    Assertions.assertEquals(resp.getStatusCode(), CREATED.getStatusCode());
  }

  @Test
  public void testPostBatchVoucherExports422() {
    logger.info(" === Test POST Batch Voucher Exports 422 - voucher \"end\" is required and cannot be null === ");
    BatchVoucherExport batchVoucherExport = getMockAsJson(BATCH_VOUCHER_EXPORT_SAMPLE_PATH).mapTo(BatchVoucherExport.class);
    batchVoucherExport.setEnd(null);
    String jsonBody = JsonObject.mapFrom(batchVoucherExport).encode();
    Headers headers = prepareHeaders(X_OKAPI_USER_ID, X_OKAPI_TENANT);
    verifyPostResponse(BATCH_VOUCHER_EXPORTS_PATH, jsonBody, headers, APPLICATION_JSON, 422);
  }

  @Test
  public void testGetBatchVoucherExports() {
    logger.info(" === Test Get Batch-voucher-exports without query - get 200 by successful retrieval of batch-voucher-exports === ");

    final BatchVoucherExportCollection resp = verifySuccessGet(BATCH_VOUCHER_EXPORTS_PATH, BatchVoucherExportCollection.class);
    Assertions.assertEquals(1, resp.getTotalRecords()
      .intValue());
  }

  @Test
  public void testGetBatchVoucherExportBadQuery() {
    logger.info(" === Test Get Batch voucher export by query - unprocessable query to emulate 400 from storage === ");

    String endpointQuery = String.format("%s?query=%s", BATCH_VOUCHER_EXPORTS_PATH, BAD_QUERY);
    verifyGet(endpointQuery, APPLICATION_JSON, 400);
  }

  @Test
  public void testGetBatchVoucherExportsByIdInternalServerError() {
    logger.info(" === Test Get Batch-voucher-exports by query - emulating 500 from storage === ");

    String endpointQuery = String.format("%s?query=%s", BATCH_VOUCHER_EXPORTS_PATH,  ID_FOR_INTERNAL_SERVER_ERROR);

    verifyGet(endpointQuery, APPLICATION_JSON, 500);
  }

  @Test
  public void testGetBatchVoucherExportsById() throws IOException {
    logger.info(" === Test Get Batch-voucher-exports By Id === ");

    JsonObject batchVoucherExportsList = new JsonObject(getMockData(BATCH_VOUCHER_EXPORTS_LIST_PATH));
    String id = batchVoucherExportsList.getJsonArray("batchVoucherExports")
      .getJsonObject(0)
      .getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", BATCH_VOUCHER_EXPORTS_LIST_PATH, id));

    final BatchVoucherExport resp = verifySuccessGet(String.format(BATCH_VOUCHER_EXPORTS_ID_PATH, id), BatchVoucherExport.class);

    logger.info(JsonObject.mapFrom(resp)
      .encodePrettily());
    Assertions.assertEquals(id, resp.getId());
  }

  @Test
  public void testGetBatchVoucherExportsByIdNotFound() {
    logger.info(" === Test Get Batch-voucher-exports by Id - 404 Not found === ");

    final Response resp = verifyGet(String.format(BATCH_VOUCHER_EXPORTS_ID_PATH, BAD_BATCH_VOUCHER_EXPORTS_ID), APPLICATION_JSON, 404);

    String actual = resp.getBody()
      .as(Errors.class)
      .getErrors()
      .get(0)
      .getMessage();

    Assertions.assertEquals(BAD_BATCH_VOUCHER_EXPORTS_ID, actual);
  }

  @Test
  public void testUpdateBatchVoucherExport() {
    logger.info(" === Test Put Batch voucher export === ");

    String newMessage = "Updated message";

    BatchVoucherExport reqData = getMockAsJson(BATCH_VOUCHER_EXPORT_SAMPLE_PATH).mapTo(BatchVoucherExport.class);
    reqData.setMessage(newMessage);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(BATCH_VOUCHER_EXPORTS_ID_PATH, id), jsonBody, "", 204);
    assertThat(getBatchVoucherExportUpdates().get(0).getString("message"), is(newMessage));
  }

  @Test
  public void testDeleteExistingBatchVoucherExport() {
    logger.info(" === Test Delete existing Batch voucher export === ");
    verifyDeleteResponse(String.format(BATCH_VOUCHER_EXPORTS_ID_PATH, VALID_BATCH_VOUCHER_EXPORTS_ID),"",204);
  }

  @Test
  public void testDeleteNonExistingBatchVoucherExport() {
    logger.info(" === Test Delete Non-existing Batch voucher export === ");
    verifyDeleteResponse(String.format(BATCH_VOUCHER_EXPORTS_ID_PATH, BAD_BATCH_VOUCHER_EXPORTS_ID), "", 404);
  }

  @Test
  public void postBatchVoucherBatchVoucherExportsUploadTest() {
    given().header(X_OKAPI_TENANT)
      .post(BATCH_VOUCHER_EXPORT_UPLOAD_ENDPOINT_PATH)
      .then()
      .statusCode(202);
  }
}
