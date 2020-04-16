package org.folio.invoices.utils;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.allOf;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.NOT_PRORATED;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.impl.ProtectionHelper;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
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
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;

public class HelperUtils {

  public static final String INVOICE_ID = "invoiceId";
  public static final String INVOICE = "invoice";
  public static final String LANG = "lang";
  public static final String OKAPI_URL = "X-Okapi-Url";
  public static final String QUERY_PARAM_START_WITH = "invoiceLines.id==";
  public static final String QUERY_PARAM_FOR_BATCH_GROUP_ID = "batchGroupId==";
  public static final String SEARCH_PARAMS = "?limit=%s&offset=%s%s&lang=%s";
  public static final String IS_DELETED_PROP = "isDeleted";
  public static final String ALL_UNITS_CQL = IS_DELETED_PROP + "=*";
  public static final String BATCH_VOUCHER_EXPORT = "batchVoucherExport";

  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";
  private static final String CALLING_ENDPOINT_MSG = "Sending {} {}";
  private static final Pattern CQL_SORT_BY_PATTERN = Pattern.compile("(.*)(\\ssortBy\\s.*)", Pattern.CASE_INSENSITIVE);


  private static final Predicate<Adjustment> NOT_PRORATED_ADJUSTMENTS_PREDICATE = adj -> adj.getProrate() == NOT_PRORATED;

  private HelperUtils() {

  }

  public static HttpClientInterface getHttpClient(Map<String, String> okapiHeaders) {
    final String okapiURL = okapiHeaders.getOrDefault(OKAPI_URL, "");
    final String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(OKAPI_HEADER_TENANT));

    HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiURL, tenantId);

    // The RMB's HttpModuleClient2.ACCEPT is in sentence case. Using the same format to avoid duplicates (issues migrating to RMB 27.1.1)
    httpClient.setDefaultHeaders(Collections.singletonMap("Accept", APPLICATION_JSON + ", " + TEXT_PLAIN));
    return httpClient;
  }

  public static CompletableFuture<InvoiceCollection> getInvoices(String query, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger, String lang) {

    String getInvoiceByQuery = resourcesPath(INVOICES) + SEARCH_PARAMS;

    String queryParam = getEndpointWithQuery(query, logger);
    String endpoint = String.format(getInvoiceByQuery, Integer.MAX_VALUE, 0, queryParam, lang);
    return getInvoicesFromStorage(endpoint, httpClient, ctx, okapiHeaders, logger).thenCompose(invoiceCollection -> {
      logger.info("Successfully retrieved invoices: " + invoiceCollection);
      return CompletableFuture.completedFuture(invoiceCollection);
    });
  }

  public static CompletableFuture<InvoiceCollection> getInvoicesFromStorage(String endpoint, HttpClientInterface httpClient,
      Context ctx, Map<String, String> okapiHeaders, Logger logger) {
    CompletableFuture<InvoiceCollection> future = new VertxCompletableFuture<>(ctx);

    try {
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenAccept(jsonInvoices -> {
        logger.info("Successfully retrieved invoices: " + jsonInvoices.encodePrettily());
        future.complete(jsonInvoices.mapTo(InvoiceCollection.class));
      })
        .exceptionally(t -> {
          logger.error("Error getting invoices", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
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

  public static CompletableFuture<Voucher> getVoucherById(String id, String lang, HttpClientInterface httpClient, Context ctx,
      Map<String, String> okapiHeaders, Logger logger) {
    String endpoint = resourceByIdPath(VOUCHERS, id, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApplyAsync(json -> json.mapTo(Voucher.class));
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

  public static String combineCqlExpressions(String term, String... expressions) {
    if (ArrayUtils.isEmpty(expressions)) {
      return EMPTY;
    }

    String sorting = EMPTY;

    // Check whether last expression contains sorting query. If it does, extract it to be added in the end of the resulting query
    Matcher matcher = CQL_SORT_BY_PATTERN.matcher(expressions[expressions.length - 1]);
    if (matcher.find()) {
      expressions[expressions.length - 1] = matcher.group(1);
      sorting = matcher.group(2);
    }

    return StreamEx.of(expressions)
      .filter(StringUtils::isNotBlank)
      .joining(") " + term + " (", "(", ")") + sorting;
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
          if (logger.isInfoEnabled()) {
            logger.info("The response body for GET {}: {}", endpoint, nonNull(body) ? body.encodePrettily() : null);
          }
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
      // The adjustment amount is calculated by absolute value of subTotal i.e. sign of the percent value defines resulted sign
      return subTotal.abs().with(MonetaryOperators.percent(adjustment.getValue()));
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
    return convertIdsToCqlQuery(ids, ID, true);
  }

  /**
   * Transform list of values for some property to CQL query using 'or' operation
   * @param values list of field values
   * @param fieldName the property name to search by
   * @param strictMatch indicates whether strict match mode (i.e. ==) should be used or not (i.e. =)
   * @return String representing CQL query to get records by some property values
   */
  public static String convertIdsToCqlQuery(List<String> values, String fieldName, boolean strictMatch) {
    String prefix = fieldName + (strictMatch ? "==(" : "=(");
    return StreamEx.of(values).joining(" or ", prefix, ")");
  }

  /**
   * Wait for all requests completion and collect all resulting objects. In case any failed, complete resulting future with the exception
   * @param futures list of futures and each produces resulting object on completion
   * @param <T> resulting objects type
   * @return CompletableFuture with resulting objects
   */
  public static <T> CompletableFuture<List<T>> collectResultsOnSuccess(List<CompletableFuture<T>> futures) {
    return allOf(futures.toArray(new CompletableFuture[0]))
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

  public static double calculateVoucherLineAmount(List<FundDistribution> fundDistributions, String systemCurrency) {
    MonetaryAmount voucherLineAmount = fundDistributions.stream()
      .map(fundDistribution -> Money.of(fundDistribution.getValue(), systemCurrency))
      .reduce(Money::add)
      .orElse(Money.zero(Monetary.getCurrency(systemCurrency)));
    return convertToDoubleWithRounding(voucherLineAmount);
  }

  public static void calculateInvoiceLineTotals(InvoiceLine invoiceLine, Invoice invoice) {
    String currency = invoice.getCurrency();
    CurrencyUnit currencyUnit = Monetary.getCurrency(currency);
    MonetaryAmount subTotal = Money.of(invoiceLine.getSubTotal(), currencyUnit);

    MonetaryAmount adjustmentTotals = calculateAdjustmentsTotal(invoiceLine.getAdjustments(), subTotal);
    MonetaryAmount total = adjustmentTotals.add(subTotal);
    invoiceLine.setAdjustmentsTotal(convertToDoubleWithRounding(adjustmentTotals));
    invoiceLine.setTotal(convertToDoubleWithRounding(total));
  }

  public static Map<String, String> getOkapiHeaders(Message<JsonObject> message) {
    Map<String, String> okapiHeaders = new CaseInsensitiveMap<>();
    message.headers()
      .entries()
      .forEach(entry -> okapiHeaders.put(entry.getKey(), entry.getValue()));
    return okapiHeaders;
  }

  public static String getNoAcqUnitCQL(String entity) {
    return String.format(ProtectionHelper.NO_ACQ_UNIT_ASSIGNED_CQL, getAcqUnitIdsQueryParamName(entity));
  }

  public static String getAcqUnitIdsQueryParamName(String entity) {
    switch (entity) {
      case INVOICE_LINES: return INVOICES + "." + ProtectionHelper.ACQUISITIONS_UNIT_IDS;
      case VOUCHER_LINES: return VOUCHERS+ "." + ProtectionHelper.ACQUISITIONS_UNIT_IDS;
      default: return ProtectionHelper.ACQUISITIONS_UNIT_IDS;
    }
  }

  public static List<Adjustment> getNotProratedAdjustments(List<Adjustment> adjustments) {
    return filterAdjustments(adjustments, NOT_PRORATED_ADJUSTMENTS_PREDICATE);
  }

  public static List<Adjustment> getProratedAdjustments(List<Adjustment> adjustments) {
    return filterAdjustments(adjustments, NOT_PRORATED_ADJUSTMENTS_PREDICATE.negate());
  }

  private static List<Adjustment> filterAdjustments(List<Adjustment> adjustments, Predicate<Adjustment> predicate) {
    return adjustments.stream()
      .filter(predicate)
      .collect(toList());
  }

  public static String getId(JsonObject jsonObject) {
    return jsonObject.getString(ID);
  }

  public static MonetaryAmount getFundDistributionAmount(FundDistribution fundDistribution, MonetaryAmount totalAmount) {
    return fundDistribution.getDistributionType() == PERCENTAGE ?
      totalAmount.with(MonetaryOperators.percent(fundDistribution.getValue())) :
      Money.of(fundDistribution.getValue(), totalAmount.getCurrency());
  }

  public static MonetaryAmount getFundDistributionAmount(FundDistribution fundDistribution, double total, String currencyUnit) {
    return getFundDistributionAmount(fundDistribution, Money.of(total, currencyUnit));
  }

  public static MonetaryAmount getFundDistributionAmount(FundDistribution fundDistribution, double total, CurrencyUnit currencyUnit) {
    return getFundDistributionAmount(fundDistribution, Money.of(total, currencyUnit));
  }

  public static MonetaryAmount getAdjustmentFundDistributionAmount(FundDistribution fundDistribution, Adjustment adjustment, Invoice invoice) {
    MonetaryAmount adjustmentTotal = calculateAdjustment(adjustment, Money.of(invoice.getSubTotal(), invoice.getCurrency()));
    return getFundDistributionAmount(fundDistribution, adjustmentTotal.getNumber().doubleValue(), invoice.getCurrency());
  }

  public static <T> Map<Integer, List<T>> buildIdsChunks(List<T> source, int maxListRecords) {
    int size = source.size();
    if (size <= 0)
      return Collections.emptyMap();
    int fullChunks = (size - 1) / maxListRecords;
    HashMap<Integer, List<T>> idChunkMap = new HashMap<>();
    IntStream.range(0, fullChunks + 1)
      .forEach(n -> {
        List<T> subList = source.subList(n * maxListRecords, n == fullChunks ? size : (n + 1) * maxListRecords);
        idChunkMap.put(n, subList);
      });
    return idChunkMap;
  }
}
