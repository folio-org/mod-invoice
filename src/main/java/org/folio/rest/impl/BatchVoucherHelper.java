package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.xml.stream.XMLStreamException;

import org.folio.converters.BatchVoucherModelConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.jaxb.XMLConverter;
import org.folio.rest.jaxrs.model.BatchVoucher;
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
  public CompletableFuture<BatchVoucher> getBatchVoucherById(String id) {
    CompletableFuture<BatchVoucher> future = new VertxCompletableFuture<>(ctx);
    String endpoint = resourceByIdPath(BATCH_VOUCHER_STORAGE, id, lang);
    handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApplyAsync(jsonObject -> jsonObject.mapTo(BatchVoucher.class))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public String convertBatchVoucher(BatchVoucher batchVoucher, String contentType) {
    String content;
    if (contentType.equalsIgnoreCase(APPLICATION_XML)) {
      BatchVoucherType xmlBatchVoucher = batchVoucherModelConverter.convert(batchVoucher);
      try {
        content = xmlConverter.marshal(BatchVoucherType.class, xmlBatchVoucher, null,true);
      } catch (XMLStreamException e) {
        throw new HttpException(400, MARSHAL_ERROR_MSG);
      }
    } else if (contentType.equalsIgnoreCase(APPLICATION_JSON)){
      content = JsonObject.mapFrom(batchVoucher).encodePrettily();
    } else {
      throw new HttpException(400, HEADER_ERROR_MSG);
    }
    return content;
  }
}
