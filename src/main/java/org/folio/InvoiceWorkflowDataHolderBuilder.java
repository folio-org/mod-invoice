package org.folio;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.invoices.utils.ErrorCodes.MULTIPLE_FISCAL_YEARS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRateProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;

public class InvoiceWorkflowDataHolderBuilder {

    private static final Logger log = LogManager.getLogger(InvoiceWorkflowDataHolderBuilder.class);

    private final ExchangeRateProviderResolver exchangeRateProviderResolver;
    private final FiscalYearService fiscalYearService;
    private final FundService fundService;
    private final LedgerService ledgerService;
    private final BaseTransactionService baseTransactionService;
    private final BudgetService budgetService;
    private final ExpenseClassRetrieveService expenseClassRetrieveService;

    public InvoiceWorkflowDataHolderBuilder(ExchangeRateProviderResolver exchangeRateProviderResolver,
                                            FiscalYearService fiscalYearService,
                                            FundService fundService,
                                            LedgerService ledgerService,
                                            BaseTransactionService baseTransactionService,
                                            BudgetService budgetService,
                                            ExpenseClassRetrieveService expenseClassRetrieveService) {
        this.exchangeRateProviderResolver = exchangeRateProviderResolver;
        this.fiscalYearService = fiscalYearService;
        this.fundService = fundService;
        this.ledgerService = ledgerService;
        this.baseTransactionService = baseTransactionService;
        this.budgetService = budgetService;
        this.expenseClassRetrieveService = expenseClassRetrieveService;
    }


    public List<InvoiceWorkflowDataHolder> buildHoldersSkeleton(List<InvoiceLine> lines, Invoice invoice) {
        List<InvoiceWorkflowDataHolder>  holders = lines.stream()
                .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
                        .map(fundDistribution -> new InvoiceWorkflowDataHolder()
                                .withInvoice(invoice)
                                .withInvoiceLine(invoiceLine)
                                .withFundDistribution(fundDistribution))).collect(toList());

        List<InvoiceWorkflowDataHolder>  holdersFromAdjustments = invoice.getAdjustments().stream()
                .flatMap(adjustment -> adjustment.getFundDistributions().stream()
                        .map(fundDistribution -> new InvoiceWorkflowDataHolder()
                                .withInvoice(invoice)
                                .withAdjustment(adjustment)
                                .withFundDistribution(fundDistribution))).collect(toList());

        holders.addAll(holdersFromAdjustments);
        return holders;
    }


    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withFunds(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        List<String> fundIds = holders.stream().map(InvoiceWorkflowDataHolder::getFundId).distinct().collect(toList());
        return fundService.getFunds(fundIds, requestContext)
                .thenApply(funds -> funds.stream().collect(toMap(Fund::getId, Function.identity())))
                .thenApply(idFundMap -> holders.stream()
                        .map(holder -> holder.withFund(idFundMap.get(holder.getFundId())))
                        .collect(toList()));
    }

    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withLedgers(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        List<String> ledgerIds = holders.stream().map(InvoiceWorkflowDataHolder::getLedgerId).distinct().collect(toList());
        return ledgerService.retrieveRestrictedLedgersByIds(ledgerIds, requestContext)
                .thenApply(ledgers -> ledgers.stream().map(Ledger::getId).collect(toSet()))
                .thenApply(ids -> holders.stream()
                        .map(holder -> holder.withRestrictExpenditures(ids.contains(holder.getLedgerId())))
                        .collect(toList()));
    }

    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withBudgets(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        List<String> fundIds = holders.stream().map(InvoiceWorkflowDataHolder::getFundId).distinct().collect(toList());
        return budgetService.fetchBudgetsByFundIds(fundIds, requestContext)
                .thenApply(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
                .thenApply(fundIdBudgetMap -> holders.stream()
                        .map(holder -> holder.withBudget(fundIdBudgetMap.get(holder.getFundId())))
                        .collect(toList()));
    }

  public List<InvoiceWorkflowDataHolder> checkMultipleFiscalYears(List<InvoiceWorkflowDataHolder> holders) {
    Map<String, List<InvoiceWorkflowDataHolder>> fiscalYearToHolders = holders.stream()
      .filter(h -> h.getBudget() != null && h.getBudget().getFiscalYearId() != null)
      .collect(groupingBy(h -> h.getBudget().getFiscalYearId()));
    if (fiscalYearToHolders.size() > 1) {
      List<String> fiscalYearIds = new ArrayList<>(fiscalYearToHolders.keySet());
      InvoiceWorkflowDataHolder h1 = fiscalYearToHolders.get(fiscalYearIds.get(0)).get(0);
      InvoiceWorkflowDataHolder h2 = fiscalYearToHolders.get(fiscalYearIds.get(1)).get(0);
      String message = String.format(MULTIPLE_FISCAL_YEARS.getDescription(), h1.getFundDistribution().getCode(),
        h2.getFundDistribution().getCode());
      Error error = new Error().withCode(MULTIPLE_FISCAL_YEARS.getCode()).withMessage(message);
      log.error(error);
      throw new HttpException(422, error);
    }
    return holders;
  }

  public CompletableFuture<List<InvoiceWorkflowDataHolder>> withFiscalYear(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        return holders.stream().map(InvoiceWorkflowDataHolder::getBudget).map(Budget::getFiscalYearId).findFirst()
                .map(s -> fiscalYearService.getFiscalYear(s, requestContext)
                        .thenApply(fiscalYear -> holders.stream().map(holder -> holder.withFiscalYear(fiscalYear)).collect(toList())))
                .orElseGet(() -> CompletableFuture.completedFuture(holders));

    }

    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withEncumbrances(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        List<String> trIds = holders.stream().map(InvoiceWorkflowDataHolder::getFundDistribution).map(FundDistribution::getEncumbrance).distinct().filter(Objects::nonNull).collect(toList());
        return baseTransactionService.getTransactions(trIds, requestContext)
                .thenApply(transactions -> transactions.stream().collect(toMap(Transaction::getId, Function.identity())))
                .thenApply(idTransactionMap -> holders.stream()
                        .map(holder -> holder.withEncumbrance(idTransactionMap.get(holder.getFundDistribution().getEncumbrance())))
                        .collect(toList()));
    }

    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withExpenseClasses(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        List<String> expenseClassIds = holders.stream()
                .map(InvoiceWorkflowDataHolder::getExpenseClassId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(toList());
        return expenseClassRetrieveService.getExpenseClasses(expenseClassIds, requestContext)
                .thenApply(expenseClasses -> expenseClasses.stream().collect(toMap(ExpenseClass::getId, Function.identity())))
                .thenApply(idExpenseClassMap -> holders.stream()
                        .map(holder -> holder.withExpenseClass(idExpenseClassMap.get(holder.getExpenseClassId())))
                        .collect(toList()));
    }

    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withExchangeRate(List<InvoiceWorkflowDataHolder> holders,
        RequestContext requestContext) {
      return holders.stream()
        .findFirst()
        .map(holder -> FolioVertxCompletableFuture.supplyBlockingAsync(requestContext.getContext(), () -> {
          Invoice invoice = holder.getInvoice();
          FiscalYear fiscalYear = holder.getFiscalYear();
          ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, fiscalYear.getCurrency());
          ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, requestContext);
          invoice.setExchangeRate(exchangeRateProvider.getExchangeRate(conversionQuery).getFactor().doubleValue());
          return exchangeRateProvider.getCurrencyConversion(conversionQuery);
        })
          .thenApply(conversion -> holders.stream()
            .map(h -> h.withConversion(conversion))
            .collect(toList())))
        .orElseGet(() -> CompletableFuture.completedFuture(holders));

    }

    public CompletableFuture<List<InvoiceWorkflowDataHolder>> withExistingTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
        return holders.stream().findFirst().map(holder -> {
            String query = String.format("sourceInvoiceId==%s AND transactionType==Pending payment", holder.getInvoice().getId());
            return baseTransactionService.getTransactions(query, 0, holders.size(), requestContext)
                    .thenApply(TransactionCollection::getTransactions)
                    .thenApply(transactions -> mapTransactionsToHolders(transactions, holders));
        }).orElseGet(() -> CompletableFuture.completedFuture(holders));

    }

    private List<InvoiceWorkflowDataHolder> mapTransactionsToHolders(List<Transaction> transactions, List<InvoiceWorkflowDataHolder> holders) {
        return holders.stream().map(holder -> holder.withExistingTransaction(mapTransactionToHolder(holder, transactions))).collect(toList());
    }

    private Transaction mapTransactionToHolder(InvoiceWorkflowDataHolder holder, List<Transaction> transactions) {
        return transactions.stream()
                .filter(transaction -> isTransactionRefersToHolder(transaction, holder))
                .findFirst()
                .orElseGet(() -> new Transaction().withAmount(0d).withCurrency(holder.getFyCurrency()));
    }

    private boolean isTransactionRefersToHolder(Transaction transaction, InvoiceWorkflowDataHolder holder) {
      return !transaction.getFromFundId()
              .equals(holder.getFundId())
              || !Objects.equals(transaction.getSourceInvoiceLineId(), holder.getInvoiceLineId())
              || !Objects.equals(transaction.getExpenseClassId(), holder.getFundDistribution().getEncumbrance())
              || !Objects.nonNull(transaction.getAwaitingPayment())
              || Objects.equals(transaction.getAwaitingPayment().getEncumbranceId(),holder.getFundDistribution().getEncumbrance());
    }
}
