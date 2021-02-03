package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.impl.MockServer.getBatchGroupUpdates;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.model.BatchGroupCollection;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class BatchGroupsApiTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(BatchGroupsApiTest.class);

  public static final String BATCH_GROUPS_PATH = "/batch-groups";
  public static final String BATCH_GROUPS_ID_PATH = BATCH_GROUPS_PATH + "/%s";
  private static final String BAD_BATCH_GROUP_ID = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  private static final String VALID_BATCH_GROUP_ID ="e91d44e4-ae4f-401a-b355-3ea44f57a628";
  private static final String INVALID_BATCH_GROUP_ID = "invalidBatchGroupId";
  static final String BATCH_GROUP_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "batchGroups/";
  static final String BATCH_GROUPS_LIST_PATH = BATCH_GROUP_MOCK_DATA_PATH + "batch_group_collection.json";
  public static final String BATCH_GROUP_SAMPLE_PATH = BATCH_GROUP_MOCK_DATA_PATH + VALID_BATCH_GROUP_ID + ".json";

  @Test
  public void testGetBatchGroups(){
    logger.info("=== Test Get Batch-groups without query - get 200 by successful retrieval of batch-groups ===");

    final BatchGroupCollection resp = verifySuccessGet(BATCH_GROUPS_PATH, BatchGroupCollection.class);
    assertEquals(1, resp.getTotalRecords()
      .intValue());
  }

  @Test
  public void testGetBatchGroupsById() throws IOException {
    logger.info("=== Test Get Batch-group By Id ===");

    JsonObject batchGroupsList = new JsonObject(getMockData(BATCH_GROUPS_LIST_PATH));
    String id = batchGroupsList.getJsonArray("batchGroups")
      .getJsonObject(0)
      .getString(ID);
    logger.info(String.format("using mock datafile: %s%s.json", BATCH_GROUPS_LIST_PATH, id));

    final BatchGroup resp = verifySuccessGet(String.format(BATCH_GROUPS_ID_PATH, id), BatchGroup.class);

    logger.info(JsonObject.mapFrom(resp)
      .encodePrettily());
    assertEquals(id, resp.getId());
  }

  @Test
  public void testGetBatchGroupsByIdNotFound() {
    logger.info("=== Test Get Batch-group by Id - 404 Not found ===");

    final Response resp = verifyGet(String.format(BATCH_GROUPS_ID_PATH, BAD_BATCH_GROUP_ID), APPLICATION_JSON, 404);

    String actual = resp.getBody()
      .as(Errors.class)
      .getErrors()
      .get(0)
      .getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_BATCH_GROUP_ID, actual);
  }

  @Test
  public void testGetBatchGroupsByIdInvalidFormat() {
    logger.info("=== Test Get Batch group by Id - 400 Bad request ===");

    final Response resp = verifyGet(String.format(BATCH_GROUPS_ID_PATH, INVALID_BATCH_GROUP_ID), TEXT_PLAIN, 400);

    String actual = resp.getBody().asString();
    logger.info(actual);

    assertNotNull(actual);
    assertTrue(actual.contains(INVALID_BATCH_GROUP_ID));
  }

  @Test
  public void testPostBatchGroup() {
    logger.info("=== Test POST Batch group ===");
    String jsonBody = getMockAsJson(BATCH_GROUP_SAMPLE_PATH).toString();
    verifySuccessPost(BATCH_GROUPS_PATH,jsonBody);
  }

  @Test
  public void testUpdateBatchGroup() {
    logger.info("=== Test Put Batch group ===");

    String newName = "New name";

    BatchGroup reqData = getMockAsJson(BATCH_GROUP_SAMPLE_PATH).mapTo(BatchGroup.class);
    reqData.setName(newName);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(BATCH_GROUPS_ID_PATH, id), jsonBody, "", 204);
    assertThat(getBatchGroupUpdates().get(0).getString("name"), is(newName));
  }

  @Test
  public void testDeleteExistingBatchGroup() {
    logger.info("=== Test Delete Batch group ===");
    verifyDeleteResponse(String.format(BATCH_GROUPS_ID_PATH, VALID_BATCH_GROUP_ID),"",204);
  }

  @Test
  public void testDeleteNonExistingBatchGroup() {
    logger.info("=== Test Delete Non-existing Batch group ===");
    verifyDeleteResponse(String.format(BATCH_GROUPS_ID_PATH, BAD_BATCH_GROUP_ID),"",404);
  }
}
