package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Credentials;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class BatchVoucherExportConfigCredentialsTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(BatchVoucherExportConfigCredentialsTest.class);

  static final String CREDENTIALS_ID = "574f0791-beca-4470-8037-050660cfb73a";
  static final String BAD_CREDENTIALS_ID = "badf0791-beca-4470-8037-050660cfb73a";
  static final String EMPTY_CREDENTIALS_ID = "ef48e416-3411-48f2-9e60-a0297d385403";
  static final String CONFIGURATION_ID = "089b333c-503f-4627-895d-26eaab1e392e";
  static final String EMPTY_CONFIGURATION_ID = "a00ae5e7-2aab-44ec-b0e1-f932248cff6f";
  static final String BAD_CREDENTIALS_CONFIGURATION_ID = "badb333c-503f-4627-895d-26eaab1e392e";

  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials", CONFIGURATION_ID);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_EMPTY_CREDENTIALS_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials", EMPTY_CONFIGURATION_ID);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials/test", CONFIGURATION_ID);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS_TEST_ERROR_ENDPOINT =
    String.format("/batch-voucher/export-configurations/%s/credentials/test", BAD_CREDENTIALS_CONFIGURATION_ID);

  static final String BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "credentials/";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH + CREDENTIALS_ID + ".json";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_BAD_CREDENTIALS_SAMPLE_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH + BAD_CREDENTIALS_ID + ".json";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_EMPTY_CREDENTIALS_PATH_WITH_ID =
    BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH + EMPTY_CREDENTIALS_ID + ".json";

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
  public void testNotPostExportConfigCredentialsWhenEmpty() throws IOException {
    logger.info("=== Test post batch voucher export configuration credentials with empty username and password. It shouldn't be saved to db");

    var credentials = getMockAsJson(BATCH_VOUCHER_EXPORT_CONFIG_EMPTY_CREDENTIALS_PATH_WITH_ID).mapTo(Credentials.class);
    credentials.setUsername("john");
    credentials.setPassword("1234");

    var res = verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_EMPTY_CREDENTIALS_ENDPOINT, credentials,
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Credentials.class);

    String id = res.getId();
    assertThat(id, notNullValue());
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.POST), hasSize(1));

    credentials.setUsername("");
    credentials.setPassword("");

    var res2 = verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_EMPTY_CREDENTIALS_ENDPOINT, credentials,
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Credentials.class);

    String id2 = res2.getId();
    assertThat(id2, nullValue());
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.POST), hasSize(1));

    credentials.setUsername("john");
    credentials.setPassword("");

    var res3 = verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_EMPTY_CREDENTIALS_ENDPOINT, credentials,
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Credentials.class);

    String id3 = res3.getId();
    assertThat(id3, nullValue());
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, HttpMethod.POST), hasSize(1));

    credentials.setUsername(" ");
    credentials.setPassword("123");

    var res4 = verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_EMPTY_CREDENTIALS_ENDPOINT, credentials,
      prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Credentials.class);

    String id4 = res4.getId();
    assertThat(id4, nullValue());
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
