package org.folio.invoices.utils;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;

import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import one.util.streamex.StreamEx;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.utils.TenantTool;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.javamoney.moneta.function.MonetaryOperators;

import io.vertx.core.Context;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class HelperUtils {

  public static final String INVOICE_ID = "invoiceId";
  public static final String INVOICE = "invoice";
  public static final String LANG = "lang";
  public static final String OKAPI_URL = "X-Okapi-Url";

  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";
  private static final String CALLING_ENDPOINT_MSG = "Sending {} {}";

  private HelperUtils() {

  }

  public static HttpClientInterface getHttpClient(Map<String, String> okapiHeaders) {
    final String okapiURL = okapiHeaders.getOrDefault(OKAPI_URL, "");
    final String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(OKAPI_HEADER_TENANT));

    HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);

    Map<String, String> customHeader = new HashMap<>();
    customHeader.put(HttpHeaders.ACCEPT.toString(), APPLICATION_JSON + ", " + TEXT_PLAIN);
    httpClient.setDefaultHeaders(customHeader);
    return httpClient;
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
   * @param recordData json to use for update operation
   * @param endpoint endpoint
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

  public static double convertToDoubleWithRounding(MonetaryAmount amount) {
    return amount.with(MonetaryOperators.rounding())
      .getNumber()
      .doubleValue();
  }

  public static boolean isPostApproval(Invoice invoice) {
    return invoice.getStatus() == Invoice.Status.APPROVED || invoice.getStatus() == Invoice.Status.PAID
        || invoice.getStatus() == Invoice.Status.CANCELLED;
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

  /**
   * Transform list of id's to CQL query using 'or' operation
   * @param ids list of id's
   * @return String representing CQL query to get records by id's
   */
  public static String convertIdsToCqlQuery(List<String> ids) {
    return StreamEx.of(ids).map(id -> "id==" + id).joining(" or ");
  }

  /**
   * Wait for all requests completion and collect all resulting objects. In case any failed, complete resulting future with the exception
   * @param futures list of futures and each produces resulting object on completion
   * @param <T> resulting objects type
   * @return CompletableFuture with resulting objects
   */
  public static <T> CompletableFuture<List<T>> collectResultsOnSuccess(List<CompletableFuture<T>> futures) {
    return VertxCompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures
        .stream()
        // The CompletableFuture::join can be safely used because the `allOf` guaranties success at this step
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .collect(toList())
      );
  }

  public static double calculateVoucherAmount(Voucher voucher, List<VoucherLine> voucherLines) {

    CurrencyUnit currency = Monetary.getCurrency(voucher.getSystemCurrency());

    MonetaryAmount amount = voucherLines.stream()
      .map(line -> Money.of(line.getAmount(), currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();

    return convertToDoubleWithRounding(amount);
  }

  public static Double calculateVoucherLineAmount(List<FundDistribution> fundDistributions, Map<String, MonetaryAmount> invoiceLineIdMonetaryAmountMap, CurrencyConversion conversion) {
    CurrencyUnit invoiceCurrency = invoiceLineIdMonetaryAmountMap.values().stream().findAny().orElseThrow(IllegalArgumentException::new).getCurrency();
    MonetaryAmount voucherLineAmount = Money.zero(invoiceCurrency);

    for (FundDistribution fundDistribution : fundDistributions) {
      MonetaryAmount invoiceLineTotal = invoiceLineIdMonetaryAmountMap.get(fundDistribution.getInvoiceLineId());
      voucherLineAmount = voucherLineAmount.add(invoiceLineTotal.with(MonetaryOperators.percent(fundDistribution.getPercentage())));
    }

    MonetaryAmount convertedAmount = voucherLineAmount.with(conversion);

    return convertToDoubleWithRounding(convertedAmount);
  }

  public static InvoiceLine calculateInvoiceLineTotals(InvoiceLine invoiceLine, Invoice invoice) {
    if (!isPostApproval(invoice)) {
      String currency = invoice.getCurrency();
      CurrencyUnit currencyUnit = Monetary.getCurrency(currency);
      MonetaryAmount subTotal = Money.of(invoiceLine.getSubTotal(), currencyUnit);

      MonetaryAmount adjustmentTotals = calculateAdjustmentsTotal(invoiceLine.getAdjustments(), subTotal);
      MonetaryAmount total = adjustmentTotals.add(subTotal);
      invoiceLine.setAdjustmentsTotal(convertToDoubleWithRounding(adjustmentTotals));
      invoiceLine.setTotal(convertToDoubleWithRounding(total));
    }

    return invoiceLine;
  }

  public static Map<String, String> getOkapiHeaders(Message<JsonObject> message) {
    Map<String, String> okapiHeaders = new CaseInsensitiveMap<>();
    message.headers()
      .entries()
      .forEach(entry -> okapiHeaders.put(entry.getKey(), entry.getValue()));
    return okapiHeaders;
  }
}
