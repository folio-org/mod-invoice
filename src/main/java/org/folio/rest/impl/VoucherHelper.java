package org.folio.rest.impl;

import static org.folio.invoices.utils.ErrorCodes.VOUCHER_UPDATE_FAILURE;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getVoucherById;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class VoucherHelper extends AbstractHelper {

  private static final String CALLING_ENDPOINT_MSG = "Sending {} {}";
  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";
  private static final String GET_VOUCHERS_BY_QUERY = resourcesPath(VOUCHERS) + "?limit=%s&offset=%s%s&lang=%s";

  VoucherHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  VoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
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

  CompletableFuture<String> generateVoucherNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(VOUCHER_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

  CompletableFuture<Voucher> createVoucher(Voucher voucher) {
    JsonObject voucherRecord = JsonObject.mapFrom(voucher);
    return createRecordInStorage(voucherRecord, resourcesPath(VOUCHERS))
      .thenApply(voucher::withId);
  }

  public CompletableFuture<Voucher> getVoucherByInvoiceId(String invoiceId) {
    return getVouchers(1, 0, String.format(QUERY_BY_INVOICE_ID, invoiceId))
      .thenApply(VoucherCollection::getVouchers)
      .thenApply(vouchers -> vouchers.isEmpty() ? null : vouchers.get(0));
  }

  /**
   * In case voucher's status is already Paid, returns completed future. Otherwise updates voucher in storage with Paid status.
   * @param voucher voucher to update status to Paid for
   * @return completed future on success or with {@link HttpException} if update fails
   */
  public CompletableFuture<Void> updateVoucherStatusToPaid(Voucher voucher) {
    if (voucher.getStatus() == Voucher.Status.PAID) {
      // Voucher already marked as paid
      return CompletableFuture.completedFuture(null);
    } else {
      return updateVoucher(voucher.withStatus(Voucher.Status.PAID))
        .exceptionally(fail -> {
          throw new HttpException(500, VOUCHER_UPDATE_FAILURE.toError());
        });
    }
  }

  public CompletableFuture<Void> updateVoucher(Voucher voucher) {
    String path = resourceByIdPath(VOUCHERS, voucher.getId(), lang);
    return handlePutRequest(path, JsonObject.mapFrom(voucher), httpClient, ctx, okapiHeaders, logger);
  }
}
