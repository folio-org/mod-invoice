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
import javax.xml.stream.XMLStreamException;

import org.folio.HttpStatus;
import org.folio.converters.BatchVoucherModelConverter;
import org.folio.jaxb.XMLConverter;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;


import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class BatchVoucherHelper extends AbstractHelper {
  private static final String HEADER_ERROR_MSG = "Accept header must be [\"application/xml\",\"application/json\"]";
  private static final String MARSHAL_ERROR_MSG = "Internal server error. Can't marshal response to XML";

  private final XMLConverter xmlConverter = XMLConverter.getInstance();
  private final BatchVoucherModelConverter batchVoucherModelConverter = BatchVoucherModelConverter.getInstance();

  public BatchVoucherHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
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
      BatchVoucherType xmlBatchVoucher = batchVoucherModelConverter.convert(jsonBatchVoucher);
      String xmlBatchVoucherResponse = null;
      try {
        xmlBatchVoucherResponse = xmlConverter.marshal(BatchVoucherType.class, xmlBatchVoucher, null,true);
      } catch (XMLStreamException e) {
        handleError(HttpStatus.HTTP_BAD_REQUEST, 500, MARSHAL_ERROR_MSG );
      }
      return buildSuccessResponse(xmlBatchVoucherResponse, APPLICATION_XML);

    case APPLICATION_JSON:
      return buildSuccessResponse(jsonBatchVoucher, APPLICATION_JSON);

    default:
      return handleError(HttpStatus.HTTP_BAD_REQUEST, 400, HEADER_ERROR_MSG );
    }
  }

  private Response handleError(HttpStatus httpStatus, int code, String message) {
    Error error = new Error();
    error.setCode(httpStatus.toString());
    error.setMessage(message);

    Errors errors = new Errors();
    errors.withErrors(Collections.singletonList(error));
    errors.setTotalRecords(1);
    closeHttpClient();
    return Response.status(code)
                   .header(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                   .entity(errors)
                   .build();
  }

}
