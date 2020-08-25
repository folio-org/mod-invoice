package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class VoucherHelper extends AbstractHelper {

  private static final String CALLING_ENDPOINT_MSG = "Sending {} {}";
  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";

  @Autowired
  private VoucherRetrieveService voucherRetrieveService;
  @Autowired
  private VoucherCommandService voucherCommandService;


  public VoucherHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
    SpringContextUtil.autowireDependencies(this, ctx);
  }


  public VoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  public VoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang,
                          VoucherRetrieveService voucherRetrieveService, VoucherCommandService voucherCommandService) {
    super(okapiHeaders, ctx, lang);
    this.voucherRetrieveService = voucherRetrieveService;
    this.voucherCommandService = voucherCommandService;
  }

  public CompletableFuture<Voucher> getVoucher(String id) {
    return voucherRetrieveService.getVoucherById(id, new RequestContext(ctx, okapiHeaders));
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
    return voucherRetrieveService.getVouchers(limit, offset, query, new RequestContext(ctx, okapiHeaders));
  }

  /**
   * Handles update of the voucher. Allows update of editable fields:
   * <ul>
   *   <li>voucher.dispersementNumber</li>
   *   <li>voucher.dispersementDate</li>
   *   <li>voucher.dispersementAmount</li>
   *   <li>voucher.voucherNumber</li>
   * </ul>
   * Attempting to edit any other fields will result in an {@link HttpException}.
   *
   * @param id updated {@link Voucher} voucher id
   * @param voucher updated {@link Voucher} voucher
   * @return completable future holding response indicating success or error if failed
   */
  public CompletableFuture<Void> partialVoucherUpdate(String id, Voucher voucher) {
    return voucherCommandService.partialVoucherUpdate(id, voucher, new RequestContext(ctx, okapiHeaders));
  }
}
