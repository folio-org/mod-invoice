package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getInvoiceById;

import io.vertx.core.Context;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.folio.rest.jaxrs.model.Voucher;

public class VoucherHelper extends AbstractHelper {

  VoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Voucher> getVoucher(String id) {
    CompletableFuture<Voucher> future = new VertxCompletableFuture<>(ctx);
    getInvoiceById(id, lang, httpClient, ctx, okapiHeaders, logger).thenAccept(jsonInvoice -> {
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

}
