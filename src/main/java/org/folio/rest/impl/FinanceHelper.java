package org.folio.rest.impl;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.allOf;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_IS_INACTIVE;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.folio.invoices.utils.ErrorCodes.INACTIVE_EXPENSE_CLASS;
import static org.folio.invoices.utils.ErrorCodes.LEDGER_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGET_EXPENSE_CLASSES;
import static org.folio.invoices.utils.ResourcePathResolver.CURRENT_BUDGET;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.LEDGERS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.Budget.BudgetStatus;
import org.folio.rest.acq.model.finance.BudgetExpenseClassCollection;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.LedgerCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.finance.CurrentFiscalYearService;
import org.folio.spring.SpringContextUtil;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class FinanceHelper extends AbstractHelper {

  public static final String FUND_ID = "fundId";
  private static final String QUERY_EQUALS = "&query=";

  private static final String GET_CURRENT_ACTIVE_BUDGET_BY_FUND_ID = resourcesPath(CURRENT_BUDGET) + "?lang=%s&status=Active";
  private static final String GET_LEDGERS_WITH_SEARCH_PARAMS = resourcesPath(LEDGERS) + SEARCH_PARAMS;
  private static final String GET_FUNDS_WITH_SEARCH_PARAMS = resourcesPath(FUNDS) + SEARCH_PARAMS;
  private static final String GET_BUDGET_EXPENSE_CLASSES_QUERY = resourcesPath(BUDGET_EXPENSE_CLASSES) + SEARCH_PARAMS;

  @Autowired
  private ExchangeRateProviderResolver exchangeRateProviderResolver;
  @Autowired
  private CurrentFiscalYearService currentFiscalYearService;

  public FinanceHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  public FinanceHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang,
                       ExchangeRateProviderResolver exchangeRateProviderResolver) {
    super(httpClient, okapiHeaders, ctx, lang);
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
  }

  public CompletableFuture<List<Budget>> fetchBudgetsByFundIds(List<String> fundIds) {
    List<CompletableFuture<Budget>> futureList = fundIds.stream()
      .distinct()
      .map(this::getActiveBudgetByFundId)
      .collect(toList());

    return VertxCompletableFuture.allOf(ctx, futureList.toArray(new CompletableFuture[0]))
      .thenApply(v -> futureList.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

  private CompletableFuture<Budget> getActiveBudgetByFundId(String fundId) {
    String endpoint = String.format(GET_CURRENT_ACTIVE_BUDGET_BY_FUND_ID, fundId, lang);

    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApply(entries -> entries.mapTo(Budget.class))
      .exceptionally(t -> {
      if (t.getCause() instanceof HttpException) {
        throw new HttpException(404, BUDGET_NOT_FOUND
          .toError().withParameters(Collections.singletonList(new Parameter().withKey("fund").withValue(fundId))));
      }
      throw new CompletionException(t.getCause());
    });
  }

  public CompletableFuture<Map<String, Ledger>> getLedgersGroupedByFundId(List<String> fundIds) {
    return fetchFundsByIds(fundIds)
      .thenCompose(funds -> {
        List<String> ledgerIds = funds.stream().map(Fund::getLedgerId).collect(toList());

        return fetchLedgersById(ledgerIds)
          .thenApply(ledgers -> funds.stream().collect(toMap(Fund::getId, fund -> ledgers.stream()
            .filter(ledger -> ledger.getId().equals(fund.getLedgerId()))
            .findFirst()
            .orElseThrow(() -> new HttpException(
              HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(), LEDGER_NOT_FOUND.toError())))));
      });
  }

  public CompletableFuture<List<Ledger>> fetchLedgersById(List<String> ids) {
    return getLedgersByChunks(ids)
      .thenApply(lists -> lists.stream()
      .flatMap(Collection::stream)
      .collect(toList()));
  }

  public CompletableFuture<List<List<Ledger>>> getLedgersByChunks(List<String> ids) {
    List<String> uniqueFundIds = ids.stream().distinct().collect(toList());
    return collectResultsOnSuccess(ofSubLists(uniqueFundIds, MAX_IDS_FOR_GET_RQ)
      .map(this::getLedgersByIds)
      .toList());
  }

  public CompletableFuture<List<Ledger>> getLedgersByIds(List<String> ids) {

    String query = convertIdsToCqlQuery(ids, ID, true);
    String queryParam = QUERY_EQUALS + encodeQuery(query, logger);
    String endpoint = String.format(GET_LEDGERS_WITH_SEARCH_PARAMS, MAX_IDS_FOR_GET_RQ, 0, queryParam, lang);

    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(entries -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> entries.mapTo(LedgerCollection.class)))
      .thenApply(ledgerCollection -> {
        if (ids.size() == ledgerCollection.getLedgers().size()) {
          return ledgerCollection.getLedgers();
        }
        String missingIds = String.join(", ", CollectionUtils.subtract(ids, ledgerCollection.getLedgers().stream().map(Ledger::getId).collect(toList())));
        throw new HttpException(404, LEDGER_NOT_FOUND
          .toError().withParameters(Collections.singletonList(new Parameter().withKey("ledgers").withValue(missingIds))));
      });
  }

  public CompletableFuture<Void> checkEnoughMoneyInBudget(List<InvoiceLine> lines, Invoice invoice) {

    List<String> fundIds = lines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream())
      .map(FundDistribution::getFundId)
      .distinct()
      .collect(toList());

    return fetchBudgetsByFundIds(fundIds)
      .thenCompose(budgets -> getLedgersGroupedByFundId(fundIds)
        .thenCompose(ledgers ->
          currentFiscalYearService.getCurrentFiscalYear(ledgers.entrySet().iterator().next().getValue().getId(), new RequestContext(ctx, okapiHeaders))
            .thenAccept(fiscalYear -> {
              final String invoiceCurrency = invoice.getCurrency();
              Map<String, MonetaryAmount> groupedAmountByFundId = getGroupedAmountByFundId(lines, invoice, invoiceCurrency);
              List<String> failedBudgetIds = validateAndGetFailedBudgets(invoiceCurrency, budgets, ledgers, groupedAmountByFundId,
                fiscalYear);

              if (!failedBudgetIds.isEmpty()) {
                throw new HttpException(422, FUND_CANNOT_BE_PAID.toError().withAdditionalProperty(BUDGETS, failedBudgetIds));
              }
            })));
  }

  private List<String> validateAndGetFailedBudgets(String systemCurrency, List<Budget> budgets, Map<String, Ledger> ledgers,
    Map<String, MonetaryAmount> fdMap, FiscalYear fiscalYear) {
    ConversionQuery conversionQuery = ConversionQueryBuilder.of().setTermCurrency(systemCurrency).build();
    ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery);
    CurrencyConversion currencyConversion =  exchangeRateProvider.getCurrencyConversion(systemCurrency);

    return budgets.stream()
      .filter(budget -> {
        String fundId = budget.getFundId();
        if (!budget.getBudgetStatus().equals(BudgetStatus.ACTIVE))
          throw new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(), BUDGET_IS_INACTIVE.toError());

        Ledger processedLedger = ledgers.get(fundId);

        if (Boolean.TRUE.equals(processedLedger.getRestrictExpenditures()) && budget.getAllowableExpenditure() != null) {

          String fyCurrency = fiscalYear.getCurrency();

          //[remaining amount we can expend] = (allocated * allowableExpenditure) - (allocated - (unavailable + available)) - (awaitingPayment + expended)
          Money allocated = Money.of(budget.getAllocated(), fyCurrency).with(currencyConversion);
          BigDecimal allowableExpenditures = BigDecimal.valueOf(budget.getAllowableExpenditure()).movePointLeft(2);
          Money unavailable = Money.of(budget.getUnavailable(), fyCurrency).with(currencyConversion);
          Money available = Money.of(budget.getAvailable(), fyCurrency).with(currencyConversion);

          Money awaitingPayment = Money.of(budget.getAwaitingPayment(), fyCurrency).with(currencyConversion);
          Money expended = Money.of(budget.getExpenditures(), fyCurrency).with(currencyConversion);

          MonetaryAmount amountCanBeExpended = allocated.multiply(allowableExpenditures)
            .subtract(allocated.subtract(unavailable.add(available)))
            .subtract(awaitingPayment.add(expended));

          MonetaryAmount fdMoneyAmount = fdMap.get(fundId);
          return fdMoneyAmount.isGreaterThan(amountCanBeExpended);
        }
        return false;
      })
      .map(Budget::getId)
      .collect(toList());
  }

  private Map<String, MonetaryAmount> getGroupedAmountByFundId(List<InvoiceLine> lines, Invoice invoice, String systemCurrency) {
    ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, systemCurrency);
    ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery);
    CurrencyConversion conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery);
    return lines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
        .map(fd -> Pair.of(fd.getFundId(),
          getFundDistributionAmount(fd, invoiceLine.getTotal(), invoice.getCurrency()).with(conversion))))
      .collect(groupingBy(Pair::getKey, sumFundAmount(systemCurrency)));
  }

  private Collector<Pair<String, MonetaryAmount>, ?, MonetaryAmount> sumFundAmount(String currency) {
    return Collectors.mapping(Pair::getValue,
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private CompletableFuture<List<Fund>> fetchFundsByIds(List<String> fundIds) {
    return getFunds(fundIds)
      .thenApply(lists -> lists.stream()
        .flatMap(Collection::stream)
        .collect(toList()));
  }

  private CompletableFuture<List<List<Fund>>> getFunds(List<String> fundIds) {
    return collectResultsOnSuccess(ofSubLists(fundIds, MAX_IDS_FOR_GET_RQ)
      .map(this::getFundsByIds)
      .toList());
  }

  public CompletableFuture<List<Fund>> getFundsByIds(List<String> ids) {
    String query = convertIdsToCqlQuery(ids);
    String queryParam = getEndpointWithQuery(query, logger);
    String endpoint = String.format(GET_FUNDS_WITH_SEARCH_PARAMS, MAX_IDS_FOR_GET_RQ, 0, queryParam, lang);

    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(fc -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> fc.mapTo(FundCollection.class)))
      .thenApply(fundCollection -> {
        if (ids.size() == fundCollection.getFunds().size()) {
          return fundCollection.getFunds();
        }
        String missingIds = String.join(", ", CollectionUtils.subtract(ids, fundCollection.getFunds().stream().map(Fund::getId).collect(toList())));
        logger.info("Funds with ids - {} are missing.", missingIds);
        throw new HttpException(404, FUNDS_NOT_FOUND.toError().withParameters(Collections.singletonList(new Parameter().withKey("funds").withValue(missingIds))));
      });
  }


  public CompletionStage<Void> checkExpenseClasses(List<InvoiceLine> invoiceLines, Invoice invoice) {
    List<FundDistribution> fundDistributionsWithExpenseClasses = getFundDistributionsWithExpenseClasses(invoiceLines, invoice);

    return allOf(ctx, fundDistributionsWithExpenseClasses.stream()
      .map(this::checkExpenseClassIsActiveByFundDistribution)
      .toArray(CompletableFuture[]::new));

  }


  private CompletableFuture<Void> checkExpenseClassIsActiveByFundDistribution(FundDistribution fundDistribution) {
    String query = String.format("budget.fundId==%s and budget.budgetStatus==Active and status==Inactive and expenseClassId==%s",
      fundDistribution.getFundId(), fundDistribution.getExpenseClassId());
    String queryParam = QUERY_EQUALS + encodeQuery(query, logger);
    String endpoint = String.format(GET_BUDGET_EXPENSE_CLASSES_QUERY, MAX_IDS_FOR_GET_RQ, 0, queryParam, lang);

    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(entries -> entries.mapTo(BudgetExpenseClassCollection.class))
      .thenAccept(budgetExpenseClasses -> {
        if (budgetExpenseClasses.getTotalRecords() > 0) {
          throw new HttpException(400, INACTIVE_EXPENSE_CLASS.toError()
            .withParameters(Arrays.asList(
              new Parameter()
                .withKey(FUND_ID).withValue(fundDistribution.getFundId()),
              new Parameter()
                .withKey("expenseClassId").withValue(fundDistribution.getExpenseClassId())
            )));
        }
      });
  }

  private List<FundDistribution> getFundDistributionsWithExpenseClasses(List<InvoiceLine> invoiceLines, Invoice invoice) {

    List<FundDistribution> fdFromInvoiceLines = invoiceLines.stream()
      .flatMap(lines -> lines.getFundDistributions().stream())
      .filter(fundDistribution -> Objects.nonNull(fundDistribution.getExpenseClassId()))
      .collect(toList());

    List<FundDistribution> fdFromAdjustments = invoice.getAdjustments().stream()
      .flatMap(adj -> adj.getFundDistributions().stream())
      .filter(fundDistribution -> Objects.nonNull(fundDistribution.getExpenseClassId()))
      .collect(toList());

    fdFromInvoiceLines.addAll(fdFromAdjustments);
    return fdFromInvoiceLines;
  }

}
