package org.folio.services.finance;

import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.ManualExchangeRateProvider;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Vertx;


public class BudgetValidationServiceTest {

  @InjectMocks
  private BudgetValidationService budgetValidationService;

  @Mock
  private FundService fundService;
  @Mock
  private LedgerService ledgerService;
  @Mock
  private FiscalYearService fiscalYearService;
  @Mock
  private ExchangeRateProviderResolver exchangeRateProviderResolver;
  @Mock
  private RestClient restClient;
  @Mock
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void checkEnoughMoneyInBudgetShouldThrowFundCannotBePaidIfTransactionsAmountDifferenceGreaterThanBudgetRemainingAmount() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    Transaction existingTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(50d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Transaction newTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(60d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(59d)
      .withTotalFunding(59d)
      .withAvailable(9d)
      .withUnavailable(50d)
      .withAwaitingPayment(50D)
      .withAllowableExpenditure(100d);

    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(true);

    when(fundService.getFunds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(fund)));
    when(ledgerService.retrieveRestrictedLedgersByIds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(ledger)));
    when(restClient.getById(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(budget));
    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    CompletableFuture<Void> future = budgetValidationService.checkEnoughMoneyInBudget(Collections.singletonList(newTransaction), Collections.singletonList(existingTransaction), requestContext);
    ExecutionException executionException = assertThrows(ExecutionException.class, future::get);

    assertThat(executionException.getCause(), IsInstanceOf.instanceOf(HttpException.class));

    HttpException httpException = (HttpException) executionException.getCause();

    assertEquals(422, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    assertEquals(FUND_CANNOT_BE_PAID.getCode(), error.getCode());
    assertEquals(Collections.singletonList(budgetId).toString(), error.getParameters().get(0).getValue());

    verify(fundService).getFunds(eq(Collections.singletonList(fundId)), eq(requestContext));
    verify(ledgerService).retrieveRestrictedLedgersByIds(eq(Collections.singletonList(ledgerId)), eq(requestContext));
    verify(restClient).getById(eq(fundId), eq(requestContext), eq(Budget.class));
  }


  @Test
  void checkEnoughMoneyInBudgetShouldPassIfTransactionsAmountDifferenceLessThanBudgetRemainingAmount() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    Transaction existingTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(50d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Transaction newTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(60d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(59d)
      .withAvailable(9d)
      .withTotalFunding(59d)
      .withUnavailable(50d)
      .withAwaitingPayment(50D)
      .withAllowableExpenditure(150d);

    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(true);

    when(fundService.getFunds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(fund)));
    when(ledgerService.retrieveRestrictedLedgersByIds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(ledger)));
    when(restClient.getById(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(budget));
    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    CompletableFuture<Void> future = budgetValidationService.checkEnoughMoneyInBudget(Collections.singletonList(newTransaction), Collections.singletonList(existingTransaction), requestContext);
    future.join();
    assertFalse(future.isCompletedExceptionally());

    verify(fundService).getFunds(eq(Collections.singletonList(fundId)), eq(requestContext));
    verify(ledgerService).retrieveRestrictedLedgersByIds(eq(Collections.singletonList(ledgerId)), eq(requestContext));
    verify(restClient).getById(eq(fundId), eq(requestContext), eq(Budget.class));
  }

  @Test
  void checkEnoughMoneyInBudgetShouldPassIfTransactionsAmountDifferenceGreaterThanBudgetRemainingAmountAndBudgetAllowableExpenditureIsNull() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    Transaction existingTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(50d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Transaction newTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(60d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(59d)
      .withTotalFunding(59d)
      .withAvailable(0d)
      .withUnavailable(50d)
      .withAwaitingPayment(50D)
      .withAllowableExpenditure(null);

    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(true);

    when(fundService.getFunds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(fund)));
    when(ledgerService.retrieveRestrictedLedgersByIds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(ledger)));
    when(restClient.getById(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(budget));
    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    CompletableFuture<Void> future = budgetValidationService.checkEnoughMoneyInBudget(Collections.singletonList(newTransaction), Collections.singletonList(existingTransaction), requestContext);
    future.join();
    assertFalse(future.isCompletedExceptionally());

    verify(fundService).getFunds(eq(Collections.singletonList(fundId)), eq(requestContext));
    verify(ledgerService).retrieveRestrictedLedgersByIds(eq(Collections.singletonList(ledgerId)), eq(requestContext));
    verify(restClient).getById(eq(fundId), eq(requestContext), eq(Budget.class));
  }

  @Test
  void shouldNotRetrieveFiscalYearAndBudgetsIfRestrictedFundsNotFound() {

    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    Invoice invoice = new Invoice();

    FundDistribution fundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withFundId(fundId)
      .withValue(100d);

    InvoiceLine invoiceLine = new InvoiceLine()
      .withSubTotal(200d)
      .withFundDistributions(Collections.singletonList(fundDistribution));


    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    when(fundService.getFunds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(fund)));
    when(ledgerService.retrieveRestrictedLedgersByIds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    CompletableFuture<Void> future = budgetValidationService.checkEnoughMoneyInBudget(Collections.singletonList(invoiceLine), invoice, requestContext);
    future.join();
    assertFalse(future.isCompletedExceptionally());

    verify(fundService).getFunds(eq(Collections.singletonList(fundId)), eq(requestContext));
    verify(ledgerService).retrieveRestrictedLedgersByIds(eq(Collections.singletonList(ledgerId)), eq(requestContext));
    verify(fiscalYearService, never()).getFiscalYear(any(), any());
    verify(restClient, never()).getById(any(), any(), any());
  }

  @Test
  void  shouldCountAdjustmentsFundDistributionsDuringBudgetRemainingAmountValidation() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    FiscalYear fiscalYear = new FiscalYear()
      .withCurrency("USD")
      .withId(fiscalYearId);

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(true);

    FundDistribution adjustmentFundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withFundId(fundId)
      .withValue(100d);

    Adjustment adjustment = new Adjustment()
      .withType(Adjustment.Type.AMOUNT)
      .withProrate(Adjustment.Prorate.NOT_PRORATED)
      .withFundDistributions(Collections.singletonList(adjustmentFundDistribution))
      .withValue(20d);

    Invoice invoice = new Invoice()
      .withCurrency("USD")
      .withExchangeRate(1d)
      .withAdjustments(Collections.singletonList(adjustment));

    FundDistribution fundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withFundId(fundId)
      .withValue(100d);

    InvoiceLine invoiceLine = new InvoiceLine()
      .withSubTotal(200d)
      .withTotal(200d)
      .withFundDistributions(Collections.singletonList(fundDistribution));


    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(260d)
      .withTotalFunding(260d)
      .withAvailable(210d)
      .withUnavailable(50d)
      .withAwaitingPayment(50d)
      .withAllowableExpenditure(100d);

    when(fundService.getFunds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(fund)));
    when(ledgerService.retrieveRestrictedLedgersByIds(anyList(), any()))
      .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(ledger)));
    when(restClient.getById(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(budget));
    when(fiscalYearService.getFiscalYear(any(), any())).thenReturn(CompletableFuture.completedFuture(fiscalYear));
    when(exchangeRateProviderResolver.resolve(any(), any())).thenReturn(new ManualExchangeRateProvider());
    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    CompletableFuture<Void> future = budgetValidationService.checkEnoughMoneyInBudget(Collections.singletonList(invoiceLine), invoice, requestContext);
    ExecutionException executionException = assertThrows(ExecutionException.class, future::get);

    assertThat(executionException.getCause(), IsInstanceOf.instanceOf(HttpException.class));

    HttpException httpException = (HttpException) executionException.getCause();

    assertEquals(422, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    assertEquals(FUND_CANNOT_BE_PAID.getCode(), error.getCode());
    assertEquals(Collections.singletonList(budgetId).toString(), error.getParameters().get(0).getValue());

    verify(fundService).getFunds(eq(Collections.singletonList(fundId)), eq(requestContext));
    verify(ledgerService).retrieveRestrictedLedgersByIds(eq(Collections.singletonList(ledgerId)), eq(requestContext));
    verify(fiscalYearService).getFiscalYear(eq(fiscalYearId), eq(requestContext));
    verify(restClient).getById(eq(fundId),  eq(requestContext), eq(Budget.class));
  }
}
