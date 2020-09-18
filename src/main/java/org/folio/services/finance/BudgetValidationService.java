package org.folio.services.finance;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class BudgetValidationService {

  private final ExchangeRateProviderResolver exchangeRateProviderResolver;
  private final FiscalYearService fiscalYearService;
  private final FundService fundService;
  private final LedgerService ledgerService;
  private final RestClient activeBudgetRestClient;

  public BudgetValidationService(ExchangeRateProviderResolver exchangeRateProviderResolver,
                                 FiscalYearService fiscalYearService,
                                 FundService fundService,
                                 LedgerService ledgerService, RestClient activeBudgetRestClient) {
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
    this.fiscalYearService = fiscalYearService;
    this.fundService = fundService;
    this.ledgerService = ledgerService;
    this.activeBudgetRestClient = activeBudgetRestClient;
  }

  public CompletableFuture<Void> checkEnoughMoneyInBudget(List<InvoiceLine> lines, Invoice invoice, RequestContext requestContext) {

    List<String> fundIds = lines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream())
      .map(FundDistribution::getFundId)
      .distinct()
      .collect(toList());

    return getRestrictedBudgets(fundIds, requestContext)
      .thenCompose(budgets -> Optional.ofNullable(budgets.get(0))
        .map(budget -> fiscalYearService.getFiscalYear(budget.getFiscalYearId(), requestContext)
          .thenAccept(fiscalYear -> {
            ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, fiscalYear.getCurrency());
            ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, requestContext);
            invoice.setExchangeRate(exchangeRateProvider.getExchangeRate(conversionQuery).getFactor().doubleValue());
            CurrencyConversion conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery);

            Map<String, MonetaryAmount> groupedAmountByFundId = getGroupedAmountByFundId(lines, invoice, conversion);
            List<String> failedBudgetIds = validateAndGetFailedBudgets(budgets, groupedAmountByFundId,
              fiscalYear.getCurrency());

            if (!failedBudgetIds.isEmpty()) {
              Parameter parameter = new Parameter()
                .withKey(BUDGETS)
                .withValue(failedBudgetIds.toString());
              throw new HttpException(422, FUND_CANNOT_BE_PAID.toError().withParameters(Collections.singletonList(parameter)));
            }
          }))
        .orElse(CompletableFuture.completedFuture(null)));
  }

  public CompletableFuture<Void> checkEnoughMoneyInBudget(List<Transaction> newTransactions, List<Transaction> existingTransactions, RequestContext requestContext) {

    Map<String, MonetaryAmount> existingFundAmountMap = collectAmountSumByFund(existingTransactions);

    Map<String, MonetaryAmount> newFundAmountMap = collectAmountSumByFund(newTransactions);

    Map<String, MonetaryAmount> fundIdAmountDiffMap = existingFundAmountMap.entrySet().stream()
      .collect(toMap(Map.Entry::getKey, entry -> newFundAmountMap.get(entry.getKey()).subtract(entry.getValue())));

    String fiscalYearCurrency = existingTransactions.get(0).getCurrency();

    List<String> fundIds = new ArrayList<>(fundIdAmountDiffMap.keySet());
    return getRestrictedBudgets(fundIds, requestContext)
      .thenAccept(budgets -> {
        List<String> failedBudgetIds = validateAndGetFailedBudgets(budgets, fundIdAmountDiffMap,
          fiscalYearCurrency);

        if (!failedBudgetIds.isEmpty()) {
          Parameter parameter = new Parameter()
            .withKey(BUDGETS)
            .withValue(failedBudgetIds.toString());
          throw new HttpException(422, FUND_CANNOT_BE_PAID.toError().withParameters(Collections.singletonList(parameter)));
        }
      });
  }

  private HashMap<String, MonetaryAmount> collectAmountSumByFund(List<Transaction> existingTransactions) {
    String currency = existingTransactions.get(0).getCurrency();
    return existingTransactions.stream()
      .collect(groupingBy(Transaction::getFromFundId, HashMap::new,
        Collectors.mapping(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()),
          Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum))));
  }

  private CompletableFuture<List<Budget>> getRestrictedBudgets(Collection<String> fundIds, RequestContext requestContext) {
    return getRestrictedFundsIds(fundIds, requestContext)
      .thenCompose(restrictedFundIds -> fetchBudgetsByFundIds(restrictedFundIds, requestContext));
  }

  private CompletableFuture<List<String>> getRestrictedFundsIds(Collection<String> fundIds, RequestContext requestContext) {
    return fundService.getFunds(fundIds, requestContext)
      .thenCompose(funds -> {
        List<String> ledgerIds = funds.stream().map(Fund::getLedgerId).distinct().collect(toList());

        return ledgerService.retrieveRestrictedLedgersByIds(ledgerIds, requestContext)
          .thenApply(ledgers -> filterRestrictedFunds(funds, ledgers));
      });
  }

  private List<String> filterRestrictedFunds(List<Fund> funds, List<Ledger> ledgers) {
    Map<String, Ledger> idLedgerMap = ledgers.stream().collect(toMap(Ledger::getId, Function.identity()));
    return funds.stream()
      .filter(fund -> idLedgerMap.containsKey(fund.getLedgerId()))
      .map(Fund::getId)
      .collect(toList());
  }

  private List<String> validateAndGetFailedBudgets(List<Budget> budgets, Map<String, MonetaryAmount> fdMap, String fyCurrency) {

    return budgets.stream()
      .filter(budget -> Objects.nonNull(budget.getAllowableExpenditure()))
      .filter(budget -> isRemainingAmountExceed(fdMap, fyCurrency, budget))
      .map(Budget::getId)
      .collect(toList());
  }

  private boolean isRemainingAmountExceed(Map<String, MonetaryAmount> fdMap, String fyCurrency, Budget budget) {
    String fundId = budget.getFundId();

    //[remaining amount we can expend] = (allocated * allowableExpenditure) - (allocated - (unavailable + available)) - (awaitingPayment + expended)
    Money allocated = Money.of(budget.getAllocated(), fyCurrency);
    BigDecimal allowableExpenditures = BigDecimal.valueOf(budget.getAllowableExpenditure()).movePointLeft(2);
    Money unavailable = Money.of(budget.getUnavailable(), fyCurrency);
    Money available = Money.of(budget.getAvailable(), fyCurrency);

    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), fyCurrency);
    Money expended = Money.of(budget.getExpenditures(), fyCurrency);

    MonetaryAmount amountCanBeExpended = allocated.multiply(allowableExpenditures)
      .subtract(allocated.subtract(unavailable.add(available)))
      .subtract(awaitingPayment.add(expended));

    MonetaryAmount fdMoneyAmount = fdMap.get(fundId);
    return fdMoneyAmount.isGreaterThan(amountCanBeExpended);
  }

  private Map<String, MonetaryAmount> getGroupedAmountByFundId(List<InvoiceLine> lines, Invoice invoice, CurrencyConversion conversion) {

    return lines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
        .map(fd -> Pair.of(fd.getFundId(),
          getFundDistributionAmount(fd, invoiceLine.getTotal(), invoice.getCurrency()).with(conversion))))
      .collect(groupingBy(Pair::getKey, sumFundAmount(conversion.getCurrency())));
  }

  private Collector<Pair<String, MonetaryAmount>, ?, MonetaryAmount> sumFundAmount(CurrencyUnit currency) {
    return Collectors.mapping(Pair::getValue,
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }


  private CompletableFuture<List<Budget>> fetchBudgetsByFundIds(Collection<String> fundIds, RequestContext requestContext) {
    List<CompletableFuture<Budget>> futureList = fundIds.stream()
      .distinct()
      .map(fundId -> getActiveBudgetByFundId(fundId, requestContext))
      .collect(toList());

    return VertxCompletableFuture.allOf(requestContext.getContext(), futureList.toArray(new CompletableFuture[0]))
      .thenApply(v -> futureList.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

  private CompletableFuture<Budget> getActiveBudgetByFundId(String fundId, RequestContext requestContext) {
    return activeBudgetRestClient.getById(fundId, requestContext, Budget.class)
      .exceptionally(t -> {
        Throwable cause = Objects.isNull(t.getCause()) ? t : t.getCause();
        if (cause instanceof HttpException) {
          throw new HttpException(404, BUDGET_NOT_FOUND
            .toError().withParameters(Collections.singletonList(new Parameter().withKey("fund").withValue(fundId))));
        }
        throw new CompletionException(t.getCause());
      });
  }

}
