package org.folio.invoices.utils;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.rest.RestConstants.SEMAPHORE_MAX_ACTIVE_THREADS;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;
import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;

import io.vertx.core.Vertx;
import io.vertxconcurrent.Semaphore;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.ProtectionHelper;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.javamoney.moneta.function.MonetaryOperators;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import one.util.streamex.StreamEx;

@Log4j2
public class HelperUtils {

  public static final String INVOICE_ID = "invoiceId";
  public static final String INVOICE = "invoice";
  public static final String QUERY_PARAM_START_WITH = "invoiceLines.id==";
  public static final String SEARCH_PARAMS = "?limit=%s&offset=%s%s";
  public static final String IS_DELETED_PROP = "isDeleted";
  public static final String ALL_UNITS_CQL = IS_DELETED_PROP + "=*";
  public static final String BATCH_VOUCHER_EXPORT = "batchVoucherExport";

  private static final Pattern CQL_SORT_BY_PATTERN = Pattern.compile("(.*)(\\ssortBy\\s.*)", Pattern.CASE_INSENSITIVE);
  private static final EnumSet<Adjustment.RelationToTotal> SUPPORTED_RELATION_TO_TOTALS =
    EnumSet.of(Adjustment.RelationToTotal.IN_ADDITION_TO, Adjustment.RelationToTotal.INCLUDED_IN);

  private HelperUtils() {
  }

  /**
   * @param query string representing CQL query
   * @return URL encoded string
   */
  public static String encodeQuery(String query) {
    return URLEncoder.encode(query, StandardCharsets.UTF_8);
  }

  public static String getEndpointWithQuery(String query) {
    return isEmpty(query) ? EMPTY : "&query=" + encodeQuery(query);
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

  public static MonetaryAmount calculateAdjustmentsTotal(List<Adjustment> adjustments, MonetaryAmount subTotal) {
    return adjustments.stream()
      .filter(adj -> SUPPORTED_RELATION_TO_TOTALS.contains(adj.getRelationToTotal()))
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

  public static double calculateAdjustment(Adjustment adjustment, Invoice invoice) {
    MonetaryAmount subTotal = Money.of(invoice.getSubTotal(), invoice.getCurrency());
    if (adjustment.getType().equals(Adjustment.Type.PERCENTAGE)) {
      // The adjustment amount is calculated by absolute value of subTotal i.e. sign of the percent value defines resulted sign
      return convertToDoubleWithRounding(subTotal.abs().with(MonetaryOperators.percent(adjustment.getValue())));
    }
    return convertToDoubleWithRounding(Money.of(adjustment.getValue(), subTotal.getCurrency()));
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

  /**
   * Transform list of id's to CQL query using 'or' operation
   * @param ids list of id's
   * @return String representing CQL query to get records by id's
   */
  public static String convertIdsToCqlQuery(Collection<String> ids) {
    return convertIdsToCqlQuery(ids, ID, true);
  }

  /**
   * Transform list of values for some property to CQL query using 'or' operation
   * @param values list of field values
   * @param fieldName the property name to search by
   * @param strictMatch indicates whether strict match mode (i.e. ==) should be used or not (i.e. =)
   * @return String representing CQL query to get records by some property values
   */
  public static String convertIdsToCqlQuery(Collection<String> values, String fieldName, boolean strictMatch) {
    String prefix = fieldName + (strictMatch ? "==(" : "=(");
    return StreamEx.of(values).joining(" or ", prefix, ")");
  }

  /**
   * Wait for all requests completion and collect all resulting objects. In case any failed, complete resulting future with the exception
   * @param futures list of futures and each produces resulting object on completion
   * @param <T> resulting objects type
   * @return Future with resulting objects
   */
  public static <T> Future<List<T>> collectResultsOnSuccess(List<Future<T>> futures) {
    return GenericCompositeFuture.join(new ArrayList<>(futures))
      .map(CompositeFuture::list);
  }

  public static <I, O> Future<List<O>> executeWithSemaphores(Collection<I> collection,
                                                             FunctionReturningFuture<I, O> f, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(collection))
      return Future.succeededFuture(List.of());
    return requestContext.getContext().<List<Future<O>>>executeBlocking(promise -> {
      Semaphore semaphore = new Semaphore(SEMAPHORE_MAX_ACTIVE_THREADS, Vertx.currentContext().owner());
      List<Future<O>> futures = new ArrayList<>();
      for (I item : collection) {
        semaphore.acquire(() -> {
          Future<O> future = f.apply(item)
            .onComplete(asyncResult -> semaphore.release());
          futures.add(future);
          if (futures.size() == collection.size()) {
            promise.complete(futures);
          }
        });
      }
    }).compose(HelperUtils::collectResultsOnSuccess);
  }

  public interface FunctionReturningFuture<I, O> {
    Future<O> apply(I item);
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
    BigDecimal invoiceLineSubTotal = BigDecimal.valueOf(invoiceLine.getSubTotal()).setScale(2, RoundingMode.HALF_EVEN);
    MonetaryAmount subTotal = Money.of(invoiceLineSubTotal, currencyUnit);
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
    return switch (entity) {
      case INVOICE_LINES -> INVOICES + "." + ProtectionHelper.ACQUISITIONS_UNIT_IDS;
      case VOUCHER_LINES -> VOUCHERS_STORAGE + "." + ProtectionHelper.ACQUISITIONS_UNIT_IDS;
      default -> ProtectionHelper.ACQUISITIONS_UNIT_IDS;
    };
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

  public static MonetaryAmount summarizeSubTotals(List<InvoiceLine> lines, CurrencyUnit currency, boolean byAbsoluteValue) {
    return lines.stream()
      .map(InvoiceLine::getSubTotal)
      .map(subTotal -> Money.of(byAbsoluteValue ? Math.abs(subTotal) : subTotal, currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }

  public static ConversionQuery buildConversionQuery(Invoice invoice, String systemCurrency) {
    if (invoice.getExchangeRate() != null){
      return ConversionQueryBuilder.of().setBaseCurrency(invoice.getCurrency())
        .setTermCurrency(systemCurrency)
        .set(RATE_KEY, invoice.getExchangeRate()).build();
    }
    return ConversionQueryBuilder.of().setBaseCurrency(invoice.getCurrency()).setTermCurrency(systemCurrency).build();
  }

  public static boolean isNotFound(Throwable t) {
    return t instanceof HttpException && ((HttpException) t).getCode() == 404;
  }
}
