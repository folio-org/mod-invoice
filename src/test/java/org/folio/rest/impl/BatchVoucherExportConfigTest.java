package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.ExportConfig.Format;
import org.folio.rest.jaxrs.model.ExportConfigCollection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class BatchVoucherExportConfigTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(BatchVoucherExportConfigTest.class);
  private static final String BATCH_VOUCHER_EXPORT_CONFIGS_ENDPOINT = "/batch-voucher/export-configurations";

  static final String EXPORT_CONFIG_ID = "089b333c-503f-4627-895d-26eaab1e392e";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "batchVoucherExportConfigs/";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH_WITH_ID = BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH
      + EXPORT_CONFIG_ID + ".json";
  static final String BATCH_VOUCHER_EXPORT_CONFIGS_SAMPLE_PATH = BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH + "configs.json";
  static final String BATCH_VOUCHER_EXPORT_CONFIG_ENDPOINT_WITH_ID = BATCH_VOUCHER_EXPORT_CONFIGS_ENDPOINT + "/" + EXPORT_CONFIG_ID;


  @Test
  public void testPostExportConfig() throws IOException {
    logger.info("=== Test create batch voucher export config ===");

    String body = getMockData(BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH_WITH_ID);

    final ExportConfig exportConfig = verifyPostResponse(BATCH_VOUCHER_EXPORT_CONFIGS_ENDPOINT, body,
        prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(ExportConfig.class);

    String id = exportConfig.getId();
    assertThat(id, notNullValue());
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS, HttpMethod.POST), hasSize(1));
  }

  @Test
  public void testGetExportConfig() {
    logger.info("=== Test get batch voucher export configuration by id ===");

    final ExportConfig exportConfig = verifySuccessGet(BATCH_VOUCHER_EXPORT_CONFIG_ENDPOINT_WITH_ID, ExportConfig.class);
    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS, HttpMethod.GET), hasSize(1));
    Assertions.assertEquals(EXPORT_CONFIG_ID, exportConfig.getId());
  }

  @Test
  public void testGetExportConfigs() {
    logger.info("=== Test Get batch voucher export configurations without query - get 200 by successful retrieval of configs ===");

    final ExportConfigCollection resp = verifySuccessGet(BATCH_VOUCHER_EXPORT_CONFIGS_ENDPOINT, ExportConfigCollection.class);

    Assertions.assertEquals(2, resp.getTotalRecords().intValue());
  }

  @Test
  public void testPutExportConfig() {
    logger.info("=== Test edit batch voucher export configuration - 204 expected ===");

    ExportConfig exportConfig = getMockAsJson(BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH_WITH_ID).mapTo(ExportConfig.class);
    exportConfig.setFormat(Format.APPLICATION_JSON);

    verifyPut(BATCH_VOUCHER_EXPORT_CONFIG_ENDPOINT_WITH_ID, JsonObject.mapFrom(exportConfig), "", 204);

    assertThat(MockServer.serverRqRs.get(BATCH_VOUCHER_EXPORT_CONFIGS, HttpMethod.PUT), hasSize(1));
  }

  @Test
  public void testDeleteExportConfig() {
    logger.info("=== Test delete batch voucher export configuration by id ===");
    verifyDeleteResponse(BATCH_VOUCHER_EXPORT_CONFIG_ENDPOINT_WITH_ID, "", 204);
  }

}
