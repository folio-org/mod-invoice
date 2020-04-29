package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.rest.impl.ApiTestSuite.mockPort;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

public class BatchVoucherExportsHelperTest extends ApiTestBase {
  private Context context;
  private Map<String, String> okapiHeaders;

  @BeforeEach
  public void setUp()  {
    super.setUp();
    context = Vertx.vertx().getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
  }

  @Test
  public void testPostBatchVoucherExportsFailedIfBatchVoucherExportIsIncorrect() {
    Assertions.assertThrows(CompletionException.class, () -> {
      BatchVoucherExportsHelper helper = new BatchVoucherExportsHelper(okapiHeaders, context, "en");
      CompletableFuture<BatchVoucherExport> future = helper.createBatchVoucherExports(null);
      future.join();
    });

  }
}
