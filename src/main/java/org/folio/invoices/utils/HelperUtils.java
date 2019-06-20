package org.folio.invoices.utils;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.money.MonetaryAmount;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.javamoney.moneta.function.MonetaryOperators;

import io.vertx.core.Context;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class HelperUtils {

  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";
  private static final String CALLING_ENDPOINT_MSG = "Sending {} {}";

  private HelperUtils() {

  }

  public static CompletableFuture<Invoice> getInvoiceById(String id, String lang, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(INVOICES, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(jsonInvoice -> {
        if (logger.isInfoEnabled()) {
          logger.info("Successfully retrieved invoice by id: " + jsonInvoice.encodePrettily());
        }
        return jsonInvoice.mapTo(Invoice.class);
      });
  }

  public static CompletableFuture<JsonObject> getVoucherLineById(String id, String lang, HttpClientInterface httpClient,
                                                                 Context ctx, Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(VOUCHER_LINES, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger);
  }

  public static CompletableFuture<JsonObject> getVoucherById(String id, String lang, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(VOUCHERS, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger);
  }

  public static JsonObject verifyAndExtractBody(Response response) {
    verifyResponse(response);
    return response.getBody();
  }

  public static void verifyResponse(Response response) {
    if (!Response.isSuccess(response.getCode())) {
      throw new CompletionException(
        new HttpException(response.getCode(), response.getError().getString("errorMessage")));
    }
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
      logger.debug(CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint);
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


  /**
   * A common method to update an entry in the storage
   *
   * @param recordData
   *          json to use for update operation
   * @param endpoint
   *          endpoint
   */
  public static CompletableFuture<Void> handlePutRequest(String endpoint, JsonObject recordData,
      HttpClientInterface httpClient,
      Context ctx, Map<String, String> okapiHeaders, Logger logger) {
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("Sending 'PUT {}' with body: {}", endpoint, recordData.encodePrettily());
      }
      httpClient
        .request(HttpMethod.PUT, recordData.toBuffer(), endpoint, okapiHeaders)
        .thenApply(HelperUtils::verifyAndExtractBody)
        .thenAccept(response -> {
          logger.debug("'PUT {}' request successfully processed", endpoint);
          future.complete(null);
        })
        .exceptionally(e -> {
          future.completeExceptionally(e);
          logger.error("'PUT {}' request failed. Request body: {}", e, endpoint, recordData.encodePrettily());
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  public static CompletableFuture<Void> handleDeleteRequest(String url, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger) {
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);

    logger.debug(CALLING_ENDPOINT_MSG, HttpMethod.DELETE, url);

    try {
      httpClient.request(HttpMethod.DELETE, url, okapiHeaders)
        .thenAccept(HelperUtils::verifyResponse)
        .thenApply(future::complete)
        .exceptionally(t -> {
          logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, t, HttpMethod.DELETE, url);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, e, HttpMethod.DELETE, url);
      future.completeExceptionally(e);
    }

    return future;
  }

  public static MonetaryAmount calculateAdjustmentsTotal(List<Adjustment> adjustments, MonetaryAmount subTotal) {
    return adjustments.stream()
      .filter(adj -> adj.getRelationToTotal().equals(Adjustment.RelationToTotal.IN_ADDITION_TO))
      .map(adj -> calculateAdjustment(adj, subTotal))
      .collect(MonetaryFunctions.summarizingMonetary(subTotal.getCurrency()))
      .getSum()
      .with(MonetaryOperators.rounding());
  }

  public static MonetaryAmount calculateAdjustment(Adjustment adjustment, MonetaryAmount subTotal) {
    if (adjustment.getType().equals(Adjustment.Type.PERCENTAGE)) {
      return subTotal.with(MonetaryOperators.percent(adjustment.getValue()));
    }
    return Money.of(adjustment.getValue(), subTotal.getCurrency());
  }

  public static double convertToDouble(MonetaryAmount amount) {
    return amount.with(MonetaryOperators.rounding())
      .getNumber()
      .doubleValue();
  }

  public static boolean isFieldsVerificationNeeded(Invoice existedInvoice) {
    return existedInvoice.getStatus() == Invoice.Status.APPROVED || existedInvoice.getStatus() == Invoice.Status.PAID
        || existedInvoice.getStatus() == Invoice.Status.CANCELLED;
  }

  public static Set<String> findChangedProtectedFields(Object newObject, Object existedObject, List<String> protectedFields) {
    Set<String> fields = new HashSet<>();
    for(String field : protectedFields) {
      try {
        if(isFieldNotEmpty(newObject, existedObject, field) && isFieldChanged(newObject, existedObject, field)) {
          fields.add(field);
        }
      } catch(IllegalAccessException e) {
        throw new CompletionException(e);
      }
    }
    return fields;
  }

  private static boolean isFieldNotEmpty(Object newObject, Object existedObject, String field) {
    return FieldUtils.getDeclaredField(newObject.getClass(), field, true) != null && FieldUtils.getDeclaredField(existedObject.getClass(), field, true) != null;
  }

  private static boolean isFieldChanged(Object newObject, Object existedObject, String field) throws IllegalAccessException {
    return !EqualsBuilder.reflectionEquals(FieldUtils.readDeclaredField(newObject, field, true), FieldUtils.readDeclaredField(existedObject, field, true), true, existedObject.getClass(), true);
  }
}
