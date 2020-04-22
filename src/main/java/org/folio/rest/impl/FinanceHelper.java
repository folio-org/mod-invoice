package org.folio.rest.impl;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.CURRENT_FISCAL_YEAR_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.TRANSACTION_CREATION_FAILURE;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_CREDITS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.impl.InvoiceHelper.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.javamoney.moneta.Money;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;

public class FinanceHelper extends AbstractHelper {

  private static final String GET_TRANSACTIONS_BY_QUERY = resourcesPath(FINANCE_TRANSACTIONS) + SEARCH_PARAMS;
  private static final String GET_FUNDS_WITH_SEARCH_PARAMS = resourcesPath(FUNDS) + SEARCH_PARAMS;
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

    Map<String, List<Transaction>> groupedByFund = groupTransactionsByFund(transactions);

    return groupByLedgerIds(groupedByFund).thenCompose(groupedByLedgerId -> VertxCompletableFuture.allOf(ctx,
      groupedByLedgerId.entrySet()
        .stream()
        .map(entry -> getCurrentFiscalYear(entry.getKey()).thenAcceptAsync(fiscalYear -> updateTransactions(entry.getValue(), fiscalYear)))
        .collect(toList())
        .toArray(new CompletableFuture[0])))
      .thenApply(aVoid -> transactions);
  }

  private List<Transaction> buildAdjustmentTransactions(Invoice invoice) {
    return invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream()
        .map(fundDistribution -> buildTransactionByAdjustments(fundDistribution, adjustment, invoice)))
      .collect(toList());
  }

  private Transaction buildTransactionByAdjustments(FundDistribution fundDistribution, Adjustment adjustment, Invoice invoice) {
    Transaction transaction = new Transaction();

    transaction.setCurrency(invoice.getCurrency());
    transaction.setSourceInvoiceId(invoice.getId());
    transaction.setPaymentEncumbranceId(fundDistribution.getEncumbrance());
    transaction.setSource(Transaction.Source.INVOICE);

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
        transaction -> transaction.getTransactionType() == Transaction.TransactionType.PAYMENT ? transaction.getFromFundId()
          : transaction.getToFundId()));
  }

  private Transaction buildTransactionByInvoiceLine(FundDistribution fundDistribution, InvoiceLine invoiceLine, Invoice invoice) {
    Transaction transaction = new Transaction();

    transaction.setCurrency(invoice.getCurrency());
    transaction.setSourceInvoiceId(invoice.getId());
    transaction.setSourceInvoiceLineId(invoiceLine.getId());
    transaction.setPaymentEncumbranceId(fundDistribution.getEncumbrance());
    transaction.setSource(Transaction.Source.INVOICE);

    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), invoice.getCurrency());
    if (amount.isPositive()) {
      transaction.withFromFundId(fundDistribution.getFundId()).withTransactionType(Transaction.TransactionType.PAYMENT);
    } else {
      transaction.withToFundId(fundDistribution.getFundId()).withTransactionType(Transaction.TransactionType.CREDIT);
    }

    transaction.setAmount(convertToDoubleWithRounding(amount.abs()));
    return transaction;
  }

  private CompletableFuture<Map<String, List<Transaction>>> groupByLedgerIds(Map<String, List<Transaction>> groupedByFund) {
    return getFunds(groupedByFund).thenApply(lists -> lists.stream()
      .flatMap(Collection::stream)
      .collect(HashMap::new, accumulator(groupedByFund), Map::putAll));
  }

  private CompletableFuture<List<List<Fund>>> getFunds(Map<String, List<Transaction>> groupedByFund) {
    return collectResultsOnSuccess(StreamEx.ofSubLists(new ArrayList<>(groupedByFund.entrySet()), MAX_IDS_FOR_GET_RQ)
      .map(entries -> entries.stream()
        .map(Map.Entry::getKey)
        .distinct()
        .collect(toList()))
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
        throw new HttpException(400, FUNDS_NOT_FOUND.toError().withParameters(Collections.singletonList(new Parameter().withKey("funds").withValue(missingIds))));
      });
  }

  private BiConsumer<HashMap<String, List<Transaction>>, Fund> accumulator(Map<String, List<Transaction>> groupedByFund) {
    return (map, fund) -> map.merge(fund.getLedgerId(), groupedByFund.get(fund.getId()), (transactions, transactions2) -> {
      transactions.addAll(transactions2);
      return transactions;
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
      CurrencyConversion conversion = getExchangeRateProvider().getCurrencyConversion(finalCurrency);
      transaction.withFiscalYearId(fiscalYear.getId())
        .withCurrency(finalCurrency)
        .withAmount(convertToDoubleWithRounding(initialAmount.with(conversion)));
    });
  }

  private CompletionStage<Void> createPaymentsAndCredits(List<Transaction> transactions) {
    return VertxCompletableFuture.allOf(ctx, transactions.stream().map(tr -> createRecordInStorage(JsonObject.mapFrom(tr), resolveTransactionPath(tr))
      .exceptionally(t -> {
        logger.error("Failed to create transaction for invoice with id - {}", t, tr.getSourceInvoiceId());
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter().withKey("invoiceLineId").withValue(tr.getSourceInvoiceLineId()));
        parameters.add(new Parameter().withKey("fundId")
          .withValue((tr.getTransactionType() == Transaction.TransactionType.PAYMENT) ? tr.getFromFundId() : tr.getToFundId()));
        throw new HttpException(400, TRANSACTION_CREATION_FAILURE.toError().withParameters(parameters));
      }))
      .toArray(CompletableFuture[]::new));
  }

  private String resolveTransactionPath(Transaction transaction) {
    return resourcesPath(transaction.getTransactionType() == Transaction.TransactionType.PAYMENT ? FINANCE_PAYMENTS : FINANCE_CREDITS);
  }

}
