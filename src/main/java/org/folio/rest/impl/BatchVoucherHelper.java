package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.folio.jaxb.XMLConverter;
import org.folio.rest.jaxrs.model.BatchGroup;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.resource.BatchVoucherBatchVouchers;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_GROUPS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;

public class BatchVoucherHelper extends AbstractHelper {
  @Autowired
  private XMLConverter xmlConverter;
  @Autowired
  private ConversionService conversionService;

  public BatchVoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  /**
   * Gets batch voucher by id
   *
   * @param id batch voucher uuid
   * @return completable future with {@link BatchVoucher} on success or an exception if processing fails
   */
  public CompletableFuture<Response> getBatchVoucherById(String id, String acceptHeader) {
    CompletableFuture<Response> future = new VertxCompletableFuture<>(ctx);
    String endpoint = resourceByIdPath(BATCH_VOUCHER_STORAGE, id, lang);
    handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApplyAsync(jsonObject -> buildSuccessResponse(jsonObject, acceptHeader))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public Response buildSuccessResponse(JsonObject jsonObjectBatchVoucher, String acceptHeader) {
    BatchVoucher jsonBatchVoucher = jsonObjectBatchVoucher.mapTo(BatchVoucher.class);
    switch (acceptHeader){
      case APPLICATION_XML:
        BatchVoucherType xmlBatchVoucher = conversionService.convert(jsonBatchVoucher, BatchVoucherType.class);
        String xmlBatchVoucherResponse = xmlConverter.marshal(xmlBatchVoucher, true);
        return buildSuccessResponse(xmlBatchVoucherResponse, APPLICATION_XML);

      case APPLICATION_JSON :
        return buildSuccessResponse(jsonBatchVoucher, APPLICATION_JSON);

      default: return buildErrorResponse(400);
    }
  }


  private static CompletableFuture<BatchGroup> getBatchGroupById(String id, String lang, HttpClientInterface httpClient, Context ctx,
                                                                 Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(BATCH_GROUPS, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApplyAsync(json -> json.mapTo(BatchGroup.class));
  }
}
