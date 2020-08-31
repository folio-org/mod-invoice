package org.folio.rest.impl;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.allOf;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_IS_INACTIVE;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.CURRENT_FISCAL_YEAR_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.folio.invoices.utils.ErrorCodes.INACTIVE_EXPENSE_CLASS;
import static org.folio.invoices.utils.ErrorCodes.LEDGER_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.TRANSACTION_CREATION_FAILURE;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGET_EXPENSE_CLASSES;
import static org.folio.invoices.utils.ResourcePathResolver.CURRENT_BUDGET;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_CREDITS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.LEDGERS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.impl.InvoiceHelper.MAX_IDS_FOR_GET_RQ;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.FundDistributionTransactionHolder;
import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.Budget.BudgetStatus;
import org.folio.rest.acq.model.finance.BudgetExpenseClassCollection;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.LedgerCollection;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.Source;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class FinanceHelper extends AbstractHelper {

  public static final String FUND_ID = "fundId";
  private static final String QUERY_EQUALS = "&query=";

  private static final String GET_CURRENT_ACTIVE_BUDGET_BY_FUND_ID = resourcesPath(CURRENT_BUDGET) + "?lang=%s&status=Active";
  private static final String GET_LEDGERS_WITH_SEARCH_PARAMS = resourcesPath(LEDGERS) + SEARCH_PARAMS;
  private static final String GET_TRANSACTIONS_BY_QUERY = resourcesPath(FINANCE_TRANSACTIONS) + SEARCH_PARAMS;
  private static final String GET_FUNDS_WITH_SEARCH_PARAMS = resourcesPath(FUNDS) + SEARCH_PARAMS;
  private static final String GET_BUDGET_EXPENSE_CLASSES_QUERY = resourcesPath(BUDGET_EXPENSE_CLASSES) + SEARCH_PARAMS;
  private static final String GET_CURRENT_FISCAL_YEAR_BY_ID = "/finance/ledgers/%s/current-fiscal-year?lang=%s";

  public FinanceHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  /**
   * This method processes payment&credit transactions based on information from invoice (invoice lines & fund distributions)
   * @param invoice {@link Invoice} that should be processed
   * @return {@link CompletableFuture<Void>}
   */
  public CompletableFuture<Void> handlePaymentsAndCredits(Invoice invoice, List<InvoiceLine > invoiceLines) {
    return isPaymentsAlreadyProcessed(invoice.getId()).thenCompose(isProcessed -> {
        if (Boolean.TRUE.equals(isProcessed)) {
          return completedFuture(null);
        }

      return loadTenantConfiguration(SYSTEM_CONFIG_QUERY)
        .thenCompose(lines -> buildTransactions(invoiceLines, invoice))
        .thenCompose(this::createPaymentsAndCredits);

    });
  }

  /**
   * This method check if the payments or credits have already been processed
   * @param invoiceId id of invoice for which payments and credits are verifying
   * @return {@link CompletableFuture<Boolean>} with true if payments or credits have already processed otherwise - with false
   */
  public CompletableFuture<Boolean> isPaymentsAlreadyProcessed(String invoiceId) {
    String query = String.format("sourceInvoiceId==%s and (transactionType==Payment or transactionType==Credit)", invoiceId);
    String endpoint = String.format(GET_TRANSACTIONS_BY_QUERY, 0, 0, getEndpointWithQuery(query, logger), lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(transactionCollection -> transactionCollection.mapTo(TransactionCollection.class).getTotalRecords() > 0);
  }

  private CompletableFuture<List<Transaction>> buildTransactions(List<InvoiceLine> invoiceLines, Invoice invoice) {
    List<Transaction> transactions = buildBaseTransactions(invoiceLines, invoice);

    transactions.addAll(buildAdjustmentTransactions(invoice));

    return groupByLedgerIds(transactions).thenCompose(groupedByLedgerId -> VertxCompletableFuture.allOf(ctx,
      groupedByLedgerId.entrySet()
        .stream()
        .map(entry -> getCurrentFiscalYear(entry.getKey()).thenAcceptAsync(fiscalYear -> updateTransactions(entry.getValue(), fiscalYear)))
        .collect(toList())
        .toArray(new CompletableFuture[0])))
      .thenApply(aVoid -> transactions);
  }

  public CompletableFuture<List<Transaction>> buildPendingPaymentTransactions(List<InvoiceLine> invoiceLines, Invoice invoice) {

    return VertxCompletableFuture.supplyBlockingAsync(ctx.owner(), () -> {
      List<FundDistributionTransactionHolder> distributionTransactionHolders = buildBasePendingPaymentTransactions(invoiceLines, invoice);
      distributionTransactionHolders.addAll(buildAdjustmentFundDistributionPendingPaymentHolderList(invoice));
      return distributionTransactionHolders;
    })
      .thenCompose(holders -> {
        List<Transaction> transactions = holders.stream().map(FundDistributionTransactionHolder::getTransaction).collect(toList());
        return groupHoldersByLedgerIds(holders)
          .thenCompose(groupedByLedgerId -> VertxCompletableFuture.allOf(ctx,
            groupedByLedgerId.entrySet()
              .stream()
              .map(entry -> getCurrentFiscalYear(entry.getKey())
                .thenAcceptAsync(fiscalYear -> updatePendingPaymentTransactionWithFiscalYear(entry.getValue(), fiscalYear)))
              .collect(toList())
              .toArray(new CompletableFuture[0])))
          .thenApply(aVoid -> transactions);
      });

  }

  private Map<String, List<FundDistributionTransactionHolder>> groupHoldersByFund(List<FundDistributionTransactionHolder> distributionTransactionHolders) {
    return distributionTransactionHolders.stream().collect(groupingBy(holder -> holder.getFundDistribution().getFundId()));
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
        .thenCompose(ledgers -> collectResultsOnSuccess(ledgers.values().stream()
          .map(Ledger::getId)
          .distinct()
          .map(this::getCurrentFiscalYear)
          .collect(toList()))
          .thenAccept(fiscalYears -> {
            final String systemCurrency = getSystemCurrency();
            Map<String, MonetaryAmount> groupedAmountByFundId = getGroupedAmountByFundId(lines, invoice, systemCurrency);
            List<String> failedBudgetIds = validateAndGetFailedBudgets(systemCurrency, budgets, ledgers, groupedAmountByFundId, fiscalYears);

            if (!failedBudgetIds.isEmpty()) {
              throw new HttpException(422, FUND_CANNOT_BE_PAID.toError().withAdditionalProperty(BUDGETS, failedBudgetIds));
            }
          })));
  }

  private List<String> validateAndGetFailedBudgets(String systemCurrency, List<Budget> budgets, Map<String, Ledger> ledgers,
    Map<String, MonetaryAmount> fdMap, List<FiscalYear> fiscalYears) {

    CurrencyConversion currencyConversion = getCurrentExchangeRateProvider().getCurrencyConversion(systemCurrency);

    return budgets.stream()
      .filter(budget -> {
        String fundId = budget.getFundId();
        if (!budget.getBudgetStatus().equals(BudgetStatus.ACTIVE))
          throw new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(), BUDGET_IS_INACTIVE.toError());

        Ledger processedLedger = ledgers.get(fundId);

        if (Boolean.TRUE.equals(processedLedger.getRestrictExpenditures()) && budget.getAllowableExpenditure() != null) {

          FiscalYear fyForLedger = fiscalYears.stream()
            .filter(fiscalYear -> processedLedger.getFiscalYearOneId().equals(fiscalYear.getId()))
            .findFirst()
            .orElseThrow(
              () -> new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(), CURRENT_FISCAL_YEAR_NOT_FOUND.toError()));

          String fyCurrency = fyForLedger.getCurrency();

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
    CurrencyConversion currencyConversion = getCurrentExchangeRateProvider().getCurrencyConversion(systemCurrency);
    return lines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
        .map(fd -> Pair.of(fd.getFundId(),
          getFundDistributionAmount(fd, invoiceLine.getTotal(), invoice.getCurrency())
            .with(currencyConversion))))
      .collect(groupingBy(Pair::getKey, sumFundAmount(systemCurrency)));
  }

  private Collector<Pair<String, MonetaryAmount>, ?, MonetaryAmount> sumFundAmount(String currency) {
    return Collectors.mapping(Pair::getValue,
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }


  private List<FundDistributionTransactionHolder> buildBasePendingPaymentTransactions(List<InvoiceLine> invoiceLines, Invoice invoice) {
    CurrencyConversion conversion = getCurrentExchangeRateProvider().getCurrencyConversion(getSystemCurrency());
    return invoiceLines.stream()
      .flatMap(line -> line.getFundDistributions().stream()
        .map(fundDistribution -> buildPendingPaymentTransactionByInvoiceLine(fundDistribution, line, invoice.getCurrency(), conversion)))
      .collect(toList());
  }

  private FundDistributionTransactionHolder buildPendingPaymentTransactionByInvoiceLine(FundDistribution fundDistribution, InvoiceLine invoiceLine,
    String currency,
    CurrencyConversion conversion) {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), currency).with(conversion);
    Transaction transaction = new Transaction();

    if (fundDistribution.getEncumbrance() != null) {
      transaction
        .withAwaitingPayment(new AwaitingPayment()
          .withEncumbranceId(fundDistribution.getEncumbrance())
          .withReleaseEncumbrance(invoiceLine.getReleaseEncumbrance()));
    }

    Transaction pendingPayment =  transaction
      .withTransactionType(TransactionType.PENDING_PAYMENT)
      .withAmount(convertToDoubleWithRounding(amount))
      .withSource(Source.INVOICE)
      .withSourceInvoiceId(invoiceLine.getInvoiceId())
      .withSourceInvoiceLineId(invoiceLine.getId())
      .withFromFundId(fundDistribution.getFundId());

    return new FundDistributionTransactionHolder()
      .withTransaction(pendingPayment)
      .withFundDistribution(fundDistribution);
  }

  private List<Transaction> buildAdjustmentTransactions(Invoice invoice) {
    return invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream()
        .map(fundDistribution -> buildTransactionByAdjustments(fundDistribution, adjustment, invoice)))
      .collect(toList());
  }

  private List<FundDistributionTransactionHolder> buildAdjustmentFundDistributionPendingPaymentHolderList(Invoice invoice) {
    return invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream()
        .map(fundDistribution -> buildFundDistributionPendingPaymentHolder(fundDistribution, adjustment, invoice)))
      .collect(toList());
  }

  private FundDistributionTransactionHolder buildFundDistributionPendingPaymentHolder(FundDistribution fundDistribution, Adjustment adjustment, Invoice invoice) {
    MonetaryAmount amount = getAdjustmentFundDistributionAmount(fundDistribution, adjustment, invoice);

    Transaction pendingPayment = new Transaction()
      .withTransactionType(TransactionType.PENDING_PAYMENT)
      .withCurrency(invoice.getCurrency())
      .withSourceInvoiceId(invoice.getId())
      .withSource(Source.INVOICE)
      .withAmount(convertToDoubleWithRounding(amount))
      .withFromFundId(fundDistribution.getFundId())
      .withExpenseClassId(fundDistribution.getExpenseClassId());
    return new FundDistributionTransactionHolder()
      .withTransaction(pendingPayment)
      .withFundDistribution(fundDistribution);
  }

  private Transaction buildTransactionByAdjustments(FundDistribution fundDistribution, Adjustment adjustment, Invoice invoice) {
    Transaction transaction = new Transaction();

    transaction.setCurrency(invoice.getCurrency());
    transaction.setSourceInvoiceId(invoice.getId());
    transaction.setPaymentEncumbranceId(fundDistribution.getEncumbrance());
    transaction.setSource(Transaction.Source.INVOICE);
    transaction.setExpenseClassId(fundDistribution.getExpenseClassId());

    MonetaryAmount amount = getAdjustmentFundDistributionAmount(fundDistribution, adjustment, invoice);
    if (amount.isPositive()) {
      transaction.withFromFundId(fundDistribution.getFundId()).withTransactionType(Transaction.TransactionType.PAYMENT);
    } else {
      transaction.withToFundId(fundDistribution.getFundId()).withTransactionType(Transaction.TransactionType.CREDIT);
    }

    transaction.setAmount(convertToDoubleWithRounding(amount.abs()));
    return transaction;
  }

  private List<Transaction> buildBaseTransactions(List<InvoiceLine> invoiceLines, Invoice invoice) {
    return invoiceLines
      .stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions()
        .stream()
        .map(fundDistribution -> buildTransactionByInvoiceLine(fundDistribution, invoiceLine, invoice)))
      .collect(toList());
  }

  private Map<String, List<Transaction>> groupTransactionsByFund(List<Transaction> transactions) {
    return transactions.stream()
      .collect(groupingBy(
        transaction -> transaction.getTransactionType() == Transaction.TransactionType.PAYMENT ||
           transaction.getTransactionType() == TransactionType.PENDING_PAYMENT
          ? transaction.getFromFundId()
          : transaction.getToFundId()));
  }

  private Transaction buildTransactionByInvoiceLine(FundDistribution fundDistribution, InvoiceLine invoiceLine, Invoice invoice) {
    Transaction transaction = new Transaction();

    transaction.setCurrency(invoice.getCurrency());
    transaction.setSourceInvoiceId(invoice.getId());
    transaction.setSourceInvoiceLineId(invoiceLine.getId());
    transaction.setPaymentEncumbranceId(fundDistribution.getEncumbrance());
    transaction.setSource(Transaction.Source.INVOICE);
    transaction.setExpenseClassId(fundDistribution.getExpenseClassId());

    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), invoice.getCurrency());
    if (amount.isPositive()) {
      transaction.withFromFundId(fundDistribution.getFundId()).withTransactionType(Transaction.TransactionType.PAYMENT);
    } else {
      transaction.withToFundId(fundDistribution.getFundId()).withTransactionType(Transaction.TransactionType.CREDIT);
    }

    transaction.setAmount(convertToDoubleWithRounding(amount.abs()));
    return transaction;
  }

  private CompletableFuture<Map<String, List<Transaction>>> groupByLedgerIds(List<Transaction> transactions) {
    Map<String, List<Transaction>> groupedByFund = groupTransactionsByFund(transactions);
    return getFunds(new ArrayList<>(groupedByFund.keySet())).thenApply(lists -> lists.stream()
      .flatMap(Collection::stream)
      .collect(HashMap::new, accumulator(groupedByFund), Map::putAll));
  }

  private CompletableFuture<Map<String, List<FundDistributionTransactionHolder>>> groupHoldersByLedgerIds(List<FundDistributionTransactionHolder> holders) {
    Map<String, List<FundDistributionTransactionHolder>> groupedByFund = groupHoldersByFund(holders);
    return getFunds(new ArrayList<>(groupedByFund.keySet())).thenApply(funds -> funds.stream()
      .flatMap(Collection::stream)
      .map(fund -> {
        groupedByFund.get(fund.getId())
          .forEach(holder -> holder.getFundDistribution().setCode(fund.getCode()));
        return fund;
      })
      .collect(HashMap::new, accumulator(groupedByFund), Map::putAll));
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

  private CompletableFuture<List<Fund>> getFundsByIds(List<String> ids) {
    String query = convertIdsToCqlQuery(ids);
    String queryParam = getEndpointWithQuery(query, logger);
    String endpoint = String.format(GET_FUNDS_WITH_SEARCH_PARAMS, MAX_IDS_FOR_GET_RQ, 0, queryParam, lang);

    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(entries -> entries.mapTo(FundCollection.class))
      .thenApply(fundCollection -> {
        if (ids.size() == fundCollection.getFunds().size()) {
          return fundCollection.getFunds();
        }
        String missingIds = String.join(", ", CollectionUtils.subtract(ids, fundCollection.getFunds().stream().map(Fund::getId).collect(toList())));
        logger.info("Funds with ids - {} are missing.", missingIds);
        throw new HttpException(404, FUNDS_NOT_FOUND.toError().withParameters(Collections.singletonList(new Parameter().withKey("funds").withValue(missingIds))));
      });
  }

  private <T> BiConsumer<HashMap<String, List<T>>, Fund> accumulator(Map<String, List<T>> groupedByFund) {
    return (map, fund) -> map.merge(fund.getLedgerId(), groupedByFund.get(fund.getId()), (list, list1) -> {
      list.addAll(list1);
      return list;
    });
  }

  private CompletableFuture<FiscalYear> getCurrentFiscalYear(String ledgerId) {
    String endpoint = String.format(GET_CURRENT_FISCAL_YEAR_BY_ID, ledgerId, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApply(entry -> entry.mapTo(FiscalYear.class))
      .exceptionally(t -> {
        if (isFiscalYearNotFound(t)) {
          List<Parameter> parameters = Collections.singletonList(new Parameter().withValue(ledgerId)
            .withKey("ledgerId"));
          throw new HttpException(400, CURRENT_FISCAL_YEAR_NOT_FOUND.toError()
            .withParameters(parameters));
        }
        throw new CompletionException(t.getCause());
      });
  }

  private boolean isFiscalYearNotFound(Throwable t) {
    return t.getCause() instanceof HttpException && ((HttpException) t.getCause()).getCode() == 404;
  }

  private void updateTransactions(List<Transaction> transactions, FiscalYear fiscalYear) {
    String finalCurrency = StringUtils.isNotEmpty(fiscalYear.getCurrency()) ? fiscalYear.getCurrency() : getSystemCurrency();
    transactions.forEach(transaction ->  {
      String initialCurrency = transaction.getCurrency();
      MonetaryAmount initialAmount = Money.of(transaction.getAmount(), initialCurrency);
      CurrencyConversion conversion = getCurrentExchangeRateProvider().getCurrencyConversion(finalCurrency);
      transaction.withFiscalYearId(fiscalYear.getId())
        .withCurrency(finalCurrency)
        .withAmount(convertToDoubleWithRounding(initialAmount.with(conversion)));
    });
  }

  private void updatePendingPaymentTransactionWithFiscalYear(List<FundDistributionTransactionHolder> holders, FiscalYear fiscalYear) {
    String finalCurrency = StringUtils.isNotEmpty(fiscalYear.getCurrency()) ? fiscalYear.getCurrency() : getSystemCurrency();
    holders.stream()
      .map(FundDistributionTransactionHolder::getTransaction)
      .forEach(transaction -> transaction.withFiscalYearId(fiscalYear.getId())
        .withCurrency(finalCurrency));
  }

  private CompletionStage<Void> createPaymentsAndCredits(List<Transaction> transactions) {
    return VertxCompletableFuture.allOf(ctx, transactions.stream().map(tr -> createRecordInStorage(JsonObject.mapFrom(tr), resolveTransactionPath(tr))
      .exceptionally(t -> {
        logger.error("Failed to create transaction for invoice with id - {}", t, tr.getSourceInvoiceId());
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter().withKey("invoiceLineId").withValue(tr.getSourceInvoiceLineId()));
        parameters.add(new Parameter().withKey(FUND_ID)
          .withValue((tr.getTransactionType() == Transaction.TransactionType.PAYMENT) ? tr.getFromFundId() : tr.getToFundId()));
        throw new HttpException(400, TRANSACTION_CREATION_FAILURE.toError().withParameters(parameters));
      }))
      .toArray(CompletableFuture[]::new));
  }

  private String resolveTransactionPath(Transaction transaction) {
    return resourcesPath(transaction.getTransactionType() == Transaction.TransactionType.PAYMENT ? FINANCE_PAYMENTS : FINANCE_CREDITS);
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
