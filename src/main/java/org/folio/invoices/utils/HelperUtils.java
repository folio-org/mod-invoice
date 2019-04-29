package org.folio.invoices.utils;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class HelperUtils {

  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";
  public static final String URL_WITH_LANG_PARAM = "%s?lang=%s";
  private static final String GET_INVOICE_BYID = resourceByIdPath(INVOICES) + URL_WITH_LANG_PARAM;
  
  private HelperUtils() {

  }

  public static Invoice convertToInvoice(JsonObject poJson) {
    return poJson.mapTo(Invoice.class);
  }
  
  public static CompletableFuture<JsonObject> getInvoiceById(String id, String lang, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = String.format(GET_INVOICE_BYID, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger);
  }

  public static JsonObject verifyAndExtractBody(Response response) {
    if (!Response.isSuccess(response.getCode())) {
      throw new CompletionException(
        new HttpException(response.getCode(), response.getError().getString("errorMessage")));
    }
    return response.getBody();
  }

  /**
   * @param query string representing CQL query
   * @param logger {@link Logger} to log error if any
   * @return URL encoded string
   */
  public static String encodeQuery(String query, Logger logger) {
    try {
      return URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      logger.error("Error happened while attempting to encode '{}'", e, query);
      throw new CompletionException(e);
    }
  }

  public static String getEndpointWithQuery(String query, Logger logger) {
    return isEmpty(query) ? EMPTY : "&query=" + encodeQuery(query, logger);
  }

  public static CompletableFuture<JsonObject> handleGetRequest(String endpoint, HttpClientInterface
    httpClient, Context ctx, Map<String, String> okapiHeaders, Logger logger) {
    CompletableFuture<JsonObject> future = new VertxCompletableFuture<>(ctx);
    try {
      logger.debug("Calling GET {}", endpoint);
      httpClient
        .request(HttpMethod.GET, endpoint, okapiHeaders)
        .thenApply(response -> {
          logger.debug("Validating response for GET {}", endpoint);
          return verifyAndExtractBody(response);
        })
        .thenAccept(body -> {
          logger.debug("The response body for GET {}: {}", endpoint, nonNull(body) ? body.encodePrettily() : null);
          future.complete(body);
        })
        .exceptionally(t -> {
          logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, t, HttpMethod.GET, endpoint);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
        logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, e, HttpMethod.GET, endpoint);
        future.completeExceptionally(e);
    }
    return future;
  }
}
