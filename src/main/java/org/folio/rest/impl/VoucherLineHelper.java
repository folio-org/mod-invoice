package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getVoucherLineById;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.jaxrs.model.VoucherLine;
import io.vertx.core.Context;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class VoucherLineHelper extends AbstractHelper {

  VoucherLineHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<VoucherLine> getVoucherLines(String id) {
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
}
