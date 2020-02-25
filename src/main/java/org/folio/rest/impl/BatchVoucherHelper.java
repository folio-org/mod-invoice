package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHERS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.BatchVoucherType;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.logging.Logger;

/**
 * As part of refactoring and make Storage services aka. helpers stateless
 */
public class BatchVoucherHelper extends AbstractHelper {
  public BatchVoucherHelper() {
    super(null, null, null, null);
  }

  public CompletableFuture<BatchVoucherType> getBatchVoucherByIdAs(String id, RequestHolder requestHolder, Logger logger) {
    HttpClientInterface httpClient = HelperUtils.getHttpClient(requestHolder.getOkapiHeaders());
    String endpoint = resourceByIdPath(BATCH_VOUCHERS, id, requestHolder.getLang());
    return handleGetRequest(endpoint, httpClient, requestHolder.getCtx(), requestHolder.getOkapiHeaders(), logger)
      .thenApplyAsync(json -> json.mapTo(BatchVoucherType.class));
  }

  public static class RequestHolder {
    private final Map<String, String> okapiHeaders;
    private final Context ctx;
    private final String lang;

    public RequestHolder(Map<String, String> okapiHeaders, Context ctx, String lang) {
      this.okapiHeaders = okapiHeaders;
      this.ctx = ctx;
      this.lang = lang;
    }

    public Map<String, String> getOkapiHeaders() {
      return okapiHeaders;
    }

    public Context getCtx() {
      return ctx;
    }

    public String getLang() {
      return lang;
    }
  }
}
