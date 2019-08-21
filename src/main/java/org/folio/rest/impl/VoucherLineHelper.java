package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getVoucherLineById;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class VoucherLineHelper extends AbstractHelper {
  private static final String GET_VOUCHER_LINE_BY_QUERY = resourcesPath(VOUCHER_LINES) + SEARCH_PARAMS;

  VoucherLineHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
  }

  VoucherLineHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Void> updateVoucherLine(VoucherLine voucherLine) {
    String path = resourceByIdPath(VOUCHER_LINES, voucherLine.getId(), lang);
    return handlePutRequest(path, JsonObject.mapFrom(voucherLine), httpClient, ctx, okapiHeaders, logger);
  }

  public CompletableFuture<VoucherLine> getVoucherLine(String id) {
    CompletableFuture<VoucherLine> future = new VertxCompletableFuture<>(ctx);
    try {
      getVoucherLineById(id, lang, httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonVoucherLine -> {
          logger.info("Successfully retrieved voucher line: " + jsonVoucherLine.encodePrettily());
          future.complete(jsonVoucherLine.mapTo(VoucherLine.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting voucher line", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  /**
   * Gets list of voucher line
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link VoucherLineCollection} on success or an exception if processing fails
   */
  public CompletableFuture<VoucherLineCollection> getVoucherLines(int limit, int offset, String query) {
    CompletableFuture<VoucherLineCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String queryParam = getEndpointWithQuery(query, logger);
      String endpoint = String.format(GET_VOUCHER_LINE_BY_QUERY, limit, offset, queryParam, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonVoucherLines -> {
          logger.info("Successfully retrieved voucher lines: " + jsonVoucherLines.encodePrettily());
          future.complete(jsonVoucherLines.mapTo(VoucherLineCollection.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting voucher lines", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  CompletableFuture<String> createVoucherLine(VoucherLine voucherLine) {
    JsonObject line = JsonObject.mapFrom(voucherLine);
    return createRecordInStorage(line, resourcesPath(VOUCHER_LINES));
  }

  CompletableFuture<Void> deleteVoucherLine(String id) {
    return handleDeleteRequest(resourceByIdPath(VOUCHER_LINES, id, lang), httpClient, ctx, okapiHeaders, logger)
      .exceptionally(t -> {
        logger.error("Error deleting voucherLines", t);
        throw  new CompletionException(t.getCause());
      });
  }

}
