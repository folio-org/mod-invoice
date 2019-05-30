package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getVoucherById;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

import io.vertx.core.Context;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class VoucherHelper extends AbstractHelper {

  private static final String GET_VOUCHERS_BY_QUERY = resourcesPath(VOUCHERS) + "?limit=%s&offset=%s%s&lang=%s";
  
  VoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Voucher> getVoucher(String id) {
    CompletableFuture<Voucher> future = new VertxCompletableFuture<>(ctx);
    getVoucherById(id, lang, httpClient, ctx, okapiHeaders, logger).thenAccept(jsonInvoice -> {
      logger.info("Successfully retrieved voucher by id: " + jsonInvoice.encodePrettily());
      future.complete(jsonInvoice.mapTo(Voucher.class));
    })
      .exceptionally(t -> {
        logger.error("Failed to retrieve Voucher", t.getCause());
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  /**
   * Gets list of voucher
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link VoucherCollection} on success or an exception if processing fails
   */
  public CompletableFuture<VoucherCollection> getVouchers(int limit, int offset, String query) {
    CompletableFuture<VoucherCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String queryParam = getEndpointWithQuery(query, logger);
      String endpoint = String.format(GET_VOUCHERS_BY_QUERY, limit, offset, queryParam, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenAccept(jsonVouchers -> {
        logger.info("Successfully retrieved vouchers: " + jsonVouchers.encodePrettily());
        future.complete(jsonVouchers.mapTo(VoucherCollection.class));
      })
      .exceptionally(t -> {
        logger.error("Error getting vouchers", t);
        future.completeExceptionally(t);
        return null;
      });
    } catch (Exception e) {
        future.completeExceptionally(e);
    }
    return future;
  }
}
