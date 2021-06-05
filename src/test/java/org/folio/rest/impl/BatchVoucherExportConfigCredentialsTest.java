package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.folio.rest.jaxrs.model.Credentials;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class BatchVoucherExportConfigCredentialsTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(BatchVoucherExportConfigCredentialsTest.class);

  static final String CREDENTIALS_ID = "574f0791-beca-4470-8037-050660cfb73a";
  static final String BAD_CREDENTIALS_ID = "badf0791-beca-4470-8037-050660cfb73a";
  static final String CONFIGURATION_ID = "089b333c-503f-4627-895d-26eaab1e392e";
  static final String BAD_CREDENTIALS_CONFIGURATION_ID = "badb333c-503f-4627-895d-26eaab1e392e";

  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials", CONFIGURATION_ID);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials/test", CONFIGURATION_ID);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ERROR_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials/test", BAD_CREDENTIALS_CONFIGURATION_ID);

  static final String BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExportConfigs/";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "credentials/";

  static final String BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH + CREDENTIALS_ID + ".json";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_BAD_CREDENTIALS_SAMPLE_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH + BAD_CREDENTIALS_ID + ".json";
  static final String BAD_CREDENTIALS_BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH + BAD_CREDENTIALS_CONFIGURATION_ID + ".json";

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
    Assertions.assertEquals(CREDENTIALS_ID, credentials.getId());
  }

  @Test
  public void testPutExportConfigCredentials() {
    logger.info("=== Test edit batch voucher export configuration credentials - 204 expected ===");

    Credentials credentials = getMockAsJson(BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID).mapTo(Credentials.class);

    verifyPut(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT, JsonObject.mapFrom(credentials), "", 204);

    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.PUT), hasSize(1));
  }

  @Test
  public void testPostExportConfigCredentialsTestSuccess() {
    logger.info("=== Test create batch voucher export config credentials test endpoint - 200 expected ===");

    verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ENDPOINT, "",
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 200);
  }

  @Test
  public void testPostExportConfigCredentialsTestFailure() {
    logger.info("=== Test create batch voucher export config credentials test endpoint - 400 expected ===");

    verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ERROR_ENDPOINT, "",
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 500);
  }
}
