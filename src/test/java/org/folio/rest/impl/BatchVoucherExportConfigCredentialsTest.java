package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.folio.rest.jaxrs.model.Credentials;
import org.junit.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class BatchVoucherExportConfigCredentialsTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(BatchVoucherExportConfigCredentialsTest.class);
  private static final String CREDENTIALS_ID = "574f0791-beca-4470-8037-050660cfb73a";
  private static final String CONFIGURATION_ID = "e91d44e4-ae4f-401a-b355-3ea44f57a628";
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials", CONFIGURATION_ID);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials/test", CONFIGURATION_ID);

  static final String BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "credentials/";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH + CREDENTIALS_ID + ".json";

  @Test
  public void testPostExportConfigCredentials() throws IOException {
    logger.info("=== Test create batch voucher export config credentials ===");

    String body = getMockData(BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID);

    final Credentials credentials = verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT, body,
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Credentials.class);

    String id = credentials.getId();
    assertThat(id, notNullValue());
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.POST), hasSize(1));
  }

  @Test
  public void testGetExportConfigCredentials() {
    logger.info("=== Test get batch voucher export configuration credentials ===");

    final Credentials credentials = verifySuccessGet(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT, Credentials.class);
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.GET), hasSize(1));
    assertEquals(CREDENTIALS_ID, credentials.getId());
  }

  @Test
  public void testPutExportConfigCredentials() {
    logger.info("=== Test edit batch voucher export configuration credentials - 204 expected ===");

    Credentials credentials = getMockAsJson(BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID).mapTo(Credentials.class);

    verifyPut(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT, JsonObject.mapFrom(credentials), "", 204);

    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.PUT), hasSize(1));
  }

  @Test
  public void testPostExportConfigCredentialsTest() throws IOException {
    logger.info("=== Test create batch voucher export config credentials test endpoint (stub) ===");

    String body = getMockData(BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID);

    verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ENDPOINT, body,
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 200);
  }
}
