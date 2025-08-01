package org.folio;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.invoices.utils.ErrorCodes.MULTIPLE_FISCAL_YEARS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.CacheableExchangeRateService;
import org.folio.services.exchange.CustomExchangeRateProvider;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;

import io.vertx.core.Future;

public class InvoiceWorkflowDataHolderBuilder {

  private static final Logger logger = LogManager.getLogger();

  private final FiscalYearService fiscalYearService;
  private final FundService fundService;
  private final LedgerService ledgerService;
  private final BaseTransactionService baseTransactionService;
  private final BudgetService budgetService;
  private final ExpenseClassRetrieveService expenseClassRetrieveService;
  private final CacheableExchangeRateService cacheableExchangeRateService;

  public InvoiceWorkflowDataHolderBuilder(FiscalYearService fiscalYearService,
                                          FundService fundService,
                                          LedgerService ledgerService,
                                          BaseTransactionService baseTransactionService,
                                          BudgetService budgetService,
                                          ExpenseClassRetrieveService expenseClassRetrieveService,
                                          CacheableExchangeRateService cacheableExchangeRateService) {
    this.fiscalYearService = fiscalYearService;
    this.fundService = fundService;
    this.ledgerService = ledgerService;
    this.baseTransactionService = baseTransactionService;
    this.budgetService = budgetService;
    this.expenseClassRetrieveService = expenseClassRetrieveService;
    this.cacheableExchangeRateService = cacheableExchangeRateService;
  }

  public Future<List<InvoiceWorkflowDataHolder>> buildCompleteHolders(Invoice invoice,
                                                                      List<InvoiceLine> invoiceLines,
                                                                      RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> dataHolders = buildHoldersSkeleton(invoiceLines, invoice);
    return withFunds(dataHolders, requestContext)
      .compose(holders -> withLedgers(holders, requestContext))
      .compose(holders -> withBudgets(holders, requestContext))
      .map(this::checkMultipleFiscalYears)
      .compose(holders -> withFiscalYear(holders, requestContext))
      .compose(holders -> withEncumbrances(holders, requestContext))
      .compose(holders -> withExpenseClasses(holders, requestContext))
      .compose(holders -> withExchangeRate(holders, requestContext));
  }

  public List<InvoiceWorkflowDataHolder> buildHoldersSkeleton(List<InvoiceLine> lines, Invoice invoice) {
    List<InvoiceWorkflowDataHolder> holders = lines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
        .map(fundDistribution -> new InvoiceWorkflowDataHolder()
          .withInvoice(invoice)
          .withInvoiceLine(invoiceLine)
          .withFundDistribution(fundDistribution)))
      .collect(toList());

    List<InvoiceWorkflowDataHolder> holdersFromAdjustments = invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream()
        .map(fundDistribution -> new InvoiceWorkflowDataHolder()
          .withInvoice(invoice)
          .withAdjustment(adjustment)
          .withFundDistribution(fundDistribution)))
      .toList();

    holders.addAll(holdersFromAdjustments);
    return holders;
  }

  public Future<List<InvoiceWorkflowDataHolder>> withFunds(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<String> fundIds = holders.stream().map(InvoiceWorkflowDataHolder::getFundId).distinct().collect(toList());
    return fundService.getFunds(fundIds, requestContext)
      .map(funds -> funds.stream().collect(toMap(Fund::getId, Function.identity())))
      .map(idFundMap -> holders.stream()
        .map(holder -> holder.withFund(idFundMap.get(holder.getFundId())))
        .collect(toList()));
  }

  public Future<List<InvoiceWorkflowDataHolder>> withLedgers(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<String> ledgerIds = holders.stream().map(InvoiceWorkflowDataHolder::getLedgerId).distinct().collect(toList());
    return ledgerService.retrieveRestrictedLedgersByIds(ledgerIds, requestContext)
      .map(ledgers -> ledgers.stream().map(Ledger::getId).collect(toSet()))
      .map(ids -> holders.stream()
        .map(holder -> holder.withRestrictExpenditures(ids.contains(holder.getLedgerId())))
        .collect(toList()));
  }

  public Future<List<InvoiceWorkflowDataHolder>> withBudgets(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    if (holders.isEmpty()) {
      return succeededFuture(holders);
    }
    List<String> fundIds = holders.stream().map(InvoiceWorkflowDataHolder::getFundId).distinct().collect(toList());
    String invoiceFiscalYearId = holders.getFirst().getInvoice().getFiscalYearId();
    return budgetService.getBudgetsByFundIds(fundIds, invoiceFiscalYearId, requestContext)
      .map(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
      .map(fundIdBudgetMap -> holders.stream()
        .map(holder -> holder.withBudget(fundIdBudgetMap.get(holder.getFundId())))
        .collect(toList()));
  }

  public List<InvoiceWorkflowDataHolder> checkMultipleFiscalYears(List<InvoiceWorkflowDataHolder> holders) {
    Map<String, List<InvoiceWorkflowDataHolder>> fiscalYearToHolders = holders.stream()
      .filter(h -> h.getBudget() != null && h.getBudget().getFiscalYearId() != null)
      .collect(groupingBy(h -> h.getBudget().getFiscalYearId()));
    if (fiscalYearToHolders.size() > 1) {
      List<String> fiscalYearIds = new ArrayList<>(fiscalYearToHolders.keySet());
      InvoiceWorkflowDataHolder h1 = fiscalYearToHolders.get(fiscalYearIds.get(0)).getFirst();
      InvoiceWorkflowDataHolder h2 = fiscalYearToHolders.get(fiscalYearIds.get(1)).getFirst();
      String message = String.format(MULTIPLE_FISCAL_YEARS.getDescription(), h1.getFundDistribution().getCode(),
        h2.getFundDistribution().getCode());
      Error error = new Error().withCode(MULTIPLE_FISCAL_YEARS.getCode()).withMessage(message);
      logger.error("checkMultipleFiscalYears:: More than one fiscal years found: {}", JsonObject.mapFrom(error).encodePrettily());
      throw new HttpException(422, error);
    }
    return holders;
  }

  public Future<List<InvoiceWorkflowDataHolder>> withFiscalYear(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    if (holders.isEmpty())
      return succeededFuture(holders);
    String fiscalYearId = holders.getFirst().getBudget().getFiscalYearId();
    return fiscalYearService.getFiscalYear(fiscalYearId, requestContext)
      .map(fiscalYear -> holders.stream().map(holder -> holder.withFiscalYear(fiscalYear)).collect(toList()));
  }

  public Future<List<InvoiceWorkflowDataHolder>> withEncumbrances(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<String> trIds = holders.stream().map(InvoiceWorkflowDataHolder::getFundDistribution).map(FundDistribution::getEncumbrance).distinct().filter(Objects::nonNull).collect(toList());
    return baseTransactionService.getTransactionsByIds(trIds, requestContext)
      .map(transactions -> transactions.stream().collect(toMap(Transaction::getId, Function.identity())))
      .map(idTransactionMap -> holders.stream()
        .map(holder -> holder.withEncumbrance(idTransactionMap.get(holder.getFundDistribution().getEncumbrance())))
        .collect(toList()));
  }

  public Future<List<InvoiceWorkflowDataHolder>> withExpenseClasses(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<String> expenseClassIds = holders.stream()
      .map(InvoiceWorkflowDataHolder::getExpenseClassId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
    return expenseClassRetrieveService.getExpenseClasses(expenseClassIds, requestContext)
      .map(expenseClasses -> expenseClasses.stream().collect(toMap(ExpenseClass::getId, Function.identity())))
      .map(idExpenseClassMap -> holders.stream()
        .map(holder -> holder.withExpenseClass(idExpenseClassMap.get(holder.getExpenseClassId())))
        .collect(toList()));
  }

  public Future<List<InvoiceWorkflowDataHolder>> withExchangeRate(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    return holders.stream()
      .findFirst()
      .map(holder -> setExchangeRateToHolder(holders, holder, requestContext))
      .orElseGet(() -> succeededFuture(holders));
  }

  private Future<List<InvoiceWorkflowDataHolder>> setExchangeRateToHolder(List<InvoiceWorkflowDataHolder> holders, InvoiceWorkflowDataHolder holder,
                                                                          RequestContext requestContext) {
    var invoice = holder.getInvoice();
    var fiscalYear = holder.getFiscalYear();
    return cacheableExchangeRateService.getExchangeRate(invoice.getCurrency(), fiscalYear.getCurrency(), invoice.getExchangeRate(), invoice.getOperationMode(), requestContext)
      .compose(exchangeRate -> {
        var query = HelperUtils.buildConversionQuery(invoice.getCurrency(), fiscalYear.getCurrency(), exchangeRate.getExchangeRate());
        var provider = new CustomExchangeRateProvider(exchangeRate.getOperationMode());
        invoice.setExchangeRate(provider.getExchangeRate(query).getFactor().doubleValue());
        invoice.setOperationMode(exchangeRate.getOperationMode().name());
        return Future.succeededFuture(provider.getCurrencyConversion(query));
      })
      .map(conversion -> holders.stream()
        .map(h -> h.withConversion(conversion))
        .toList());
  }

  public Future<List<InvoiceWorkflowDataHolder>> withExistingTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    return holders.stream().findFirst().map(holder -> {
      String query = String.format("sourceInvoiceId==%s AND transactionType==Pending payment", holder.getInvoice().getId());
      return baseTransactionService.getTransactions(query, 0, holders.size(), requestContext)
        .map(TransactionCollection::getTransactions)
        .map(transactions -> mapTransactionsToHolders(transactions, holders));
    }).orElseGet(() -> succeededFuture(holders));
  }

  private List<InvoiceWorkflowDataHolder> mapTransactionsToHolders(List<Transaction> transactions, List<InvoiceWorkflowDataHolder> holders) {
    return holders.stream().map(holder -> holder.withExistingTransaction(mapTransactionToHolder(holder, transactions))).collect(toList());
  }

  private Transaction mapTransactionToHolder(InvoiceWorkflowDataHolder holder, List<Transaction> transactions) {
    Transaction transaction = transactions.stream()
      .filter(tr -> isTransactionRefersToHolder(tr, holder))
      .findFirst()
      .orElseGet(() -> new Transaction().withAmount(0d).withCurrency(holder.getFyCurrency()));
    // Added remove so that in case of multiple transaction update our holder won't hold the same transaction because of findFirst().
    // And in this case we can avoid "Primary Key Violation" when working with invoice summaries.
    transactions.remove(transaction);
    return transaction;
  }

  private boolean isTransactionRefersToHolder(Transaction transaction, InvoiceWorkflowDataHolder holder) {
    return !transaction.getFromFundId().equals(holder.getFundId())
      || !Objects.equals(transaction.getSourceInvoiceLineId(), holder.getInvoiceLineId())
      || !Objects.equals(transaction.getExpenseClassId(), holder.getFundDistribution().getEncumbrance())
      || !Objects.nonNull(transaction.getAwaitingPayment())
      || Objects.equals(transaction.getAwaitingPayment().getEncumbranceId(), holder.getFundDistribution().getEncumbrance());
  }
}
