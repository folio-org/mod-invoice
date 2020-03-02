package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.folio.HttpStatus;
import org.folio.jaxb.XMLConverter;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class BatchVoucherHelper extends AbstractHelper {
  private static final String HEADER_ERROR_MSG = "Accept header must be [\"application/xml\",\"application/json\"]";

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

  private Response buildSuccessResponse(JsonObject jsonObjectBatchVoucher, String acceptHeader) {
    BatchVoucher jsonBatchVoucher = jsonObjectBatchVoucher.mapTo(BatchVoucher.class);
    switch (acceptHeader) {
    case APPLICATION_XML:
      BatchVoucherType xmlBatchVoucher = conversionService.convert(jsonBatchVoucher, BatchVoucherType.class);
      String xmlBatchVoucherResponse = xmlConverter.marshal(xmlBatchVoucher, true);
      return buildSuccessResponse(xmlBatchVoucherResponse, APPLICATION_XML);

    case APPLICATION_JSON:
      return buildSuccessResponse(jsonBatchVoucher, APPLICATION_JSON);

    default:
      return handleAcceptHeaderError();
    }
  }

  private Response handleAcceptHeaderError() {
    Error error = new Error();
    error.setCode(HttpStatus.HTTP_BAD_REQUEST.toString());
    error.setMessage(HEADER_ERROR_MSG);

    Errors errors = new Errors();
    errors.withErrors(Collections.singletonList(error));
    errors.setTotalRecords(1);
    closeHttpClient();
    return Response.status(400)
                   .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                   .entity(errors)
                   .build();
  }

}
