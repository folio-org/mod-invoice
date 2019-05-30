package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getVoucherById;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import io.vertx.core.Context;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

public class VoucherHelper extends AbstractHelper {

  private static final String CALLING_ENDPOINT_MSG = "Sending {} {}";
  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";
  
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

  public CompletableFuture<SequenceNumber> getVoucherNumberStartValue() {

    return handleGetRequest(resourcesPath(VOUCHER_NUMBER_START), httpClient, ctx, okapiHeaders, logger)
      .thenApply(entry -> entry.mapTo(SequenceNumber.class));
  }
  
  /**
   * This endpoint is a means for the UI to set/reset the start value of the voucher-number sequence
   * @param value start value to be set/reset
   * @return completable future on success or {@code null} if validation fails or an exception if any issue happens
   */
  public CompletableFuture<Void> setStartValue(String value) {
    return handlePostStartValueRequest(resourcesPath(VOUCHER_NUMBER_START) + "/" + value, httpClient, ctx, okapiHeaders, logger);
  }

  /**
   * Proxy the request to storage module POST /voucher-storage/voucher-number/start/<val>
   * @param url Storage url to proxy the request
   * @param httpClient HttpClientInterface
   * @param ctx Context of the vertex thread
   * @param okapiHeaders Map of okapi request headers and its values
   * @param logger vertx logger classes for logging purpose
   * @return completable future on success or {@code null} if validation fails or an exception if any issue happens
   */
  public CompletableFuture<Void> handlePostStartValueRequest(String url, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger) {
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);

    logger.info(CALLING_ENDPOINT_MSG, HttpMethod.POST, url);

    try {
      httpClient.request(HttpMethod.POST, url, okapiHeaders)
        .thenAccept(HelperUtils::verifyResponse)
        .thenApply(future::complete)
        .exceptionally(t -> {
          logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, t, HttpMethod.POST, url);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, e, HttpMethod.POST, url);
      future.completeExceptionally(e);
    }

    return future;
  }
}
