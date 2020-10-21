package org.folio.services.transaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.ManualExchangeRateProvider;
import org.folio.services.finance.BudgetValidationService;
import org.folio.services.finance.CurrentFiscalYearService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Vertx;

public class PendingPaymentWorkflowServiceTest {

  @InjectMocks
  private PendingPaymentWorkflowService pendingPaymentWorkflowService;

  @Mock
  private BaseTransactionService baseTransactionService;
  @Mock
  private CurrentFiscalYearService currentFiscalYearService;
  @Mock
  private InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  @Mock
  private BudgetValidationService budgetValidationService;
  @Mock
  private RequestContext requestContext;
  @Mock
  private ExchangeRateProviderResolver exchangeRateProviderResolver;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void updatePendingPayments() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String invoiceLineId = UUID.randomUUID().toString();
    double exchangeRate = 1.3;

    FiscalYear fiscalYear = new FiscalYear()
      .withId(fiscalYearId)
      .withCurrency("USD");

    Transaction existingInvoiceLineTransaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withSourceInvoiceId(invoiceId)
      .withSourceInvoiceLineId(invoiceLineId)
      .withAmount(50d);

    Transaction existingInvoiceTransaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withSourceInvoiceId(invoiceId)
      .withAmount(10d);

    TransactionCollection existingTransactionCollection = new TransactionCollection()
      .withTotalRecords(1);
    existingTransactionCollection.getTransactions().add(existingInvoiceLineTransaction);
    existingTransactionCollection.getTransactions().add(existingInvoiceTransaction);

    FundDistribution invoiceFundDistribution = new FundDistribution()
        .withDistributionType(FundDistribution.DistributionType.AMOUNT)
        .withFundId(fundId)
        .withValue(30.5);

    Adjustment adjustment = new Adjustment()
      .withFundDistributions(Collections.singletonList(invoiceFundDistribution))
      .withProrate(Adjustment.Prorate.NOT_PRORATED)
      .withValue(30.5)
      .withType(Adjustment.Type.AMOUNT);

    Invoice invoice = new Invoice()
      .withAdjustments(Collections.singletonList(adjustment))
      .withId(invoiceId)
      .withSubTotal(50d)
      .withExchangeRate(exchangeRate)
      .withCurrency("EUR");

    InvoiceLine invoiceLine = new InvoiceLine()
      .withTotal(60d)
      .withId(invoiceLineId);

    FundDistribution invoiceLineFundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.AMOUNT)
      .withFundId(fundId)
      .withValue(60d);

    invoiceLine.getFundDistributions().add(invoiceLineFundDistribution);

    when(baseTransactionService.getTransactions(anyString(), anyInt(), anyInt(), any()))
      .thenReturn(CompletableFuture.completedFuture(existingTransactionCollection));
    when(currentFiscalYearService.getCurrentFiscalYearByFund(anyString(), any())).thenReturn(CompletableFuture.completedFuture(fiscalYear));

    when(budgetValidationService.checkEnoughMoneyInBudget(anyList(), anyList(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(invoiceTransactionSummaryService.updateInvoiceTransactionSummary(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(baseTransactionService.updateTransaction(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(exchangeRateProviderResolver.resolve(any(), any())).thenReturn(new ManualExchangeRateProvider());
    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    pendingPaymentWorkflowService.handlePendingPaymentsUpdate(invoice, Collections.singletonList(invoiceLine), requestContext);

    String expectedQuery = String.format("sourceInvoiceId==%s AND transactionType==Pending payment", invoice.getId());
    verify(baseTransactionService).getTransactions(eq(expectedQuery), eq(0), eq(Integer.MAX_VALUE), eq(requestContext));

    verify(currentFiscalYearService).getCurrentFiscalYearByFund(eq(fundId), eq(requestContext));

    ArgumentCaptor<List<Transaction>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(budgetValidationService).checkEnoughMoneyInBudget(argumentCaptor.capture(), eq(existingTransactionCollection.getTransactions()), eq(requestContext));
    assertThat(argumentCaptor.getValue(), hasSize(2));
    List<Transaction> newTransactions = argumentCaptor.getValue();
    Transaction newInvoiceTransaction = newTransactions.stream()
      .filter(transaction -> Objects.isNull(transaction.getSourceInvoiceLineId())).findFirst().get();
    Transaction newInvoiceLineTransaction = newTransactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getSourceInvoiceLineId())).findFirst().get();

    double expectedInvoiceLineTransactionAmount = BigDecimal.valueOf(60).multiply(BigDecimal.valueOf(exchangeRate)).doubleValue();
    assertEquals(expectedInvoiceLineTransactionAmount, newInvoiceLineTransaction.getAmount());
    assertEquals(fundId, newInvoiceLineTransaction.getFromFundId());
    assertEquals(fiscalYearId, newInvoiceLineTransaction.getFiscalYearId());
    assertEquals(invoiceId, newInvoiceLineTransaction.getSourceInvoiceId());
    assertEquals(invoiceLineId, newInvoiceLineTransaction.getSourceInvoiceLineId());
    assertEquals(Transaction.TransactionType.PENDING_PAYMENT, newInvoiceLineTransaction.getTransactionType());
    assertEquals(Transaction.Source.INVOICE, newInvoiceLineTransaction.getSource());

    double expectedInvoiceTransactionAmount = BigDecimal.valueOf(30.5).multiply(BigDecimal.valueOf(exchangeRate)).doubleValue();
    assertEquals(expectedInvoiceTransactionAmount, newInvoiceTransaction.getAmount());
    assertEquals(fundId, newInvoiceTransaction.getFromFundId());
    assertEquals(fiscalYearId, newInvoiceTransaction.getFiscalYearId());
    assertEquals(invoiceId, newInvoiceTransaction.getSourceInvoiceId());
    assertNull(newInvoiceTransaction.getSourceInvoiceLineId());
    assertEquals(Transaction.TransactionType.PENDING_PAYMENT, newInvoiceTransaction.getTransactionType());
    assertEquals(Transaction.Source.INVOICE, newInvoiceTransaction.getSource());

    InvoiceTransactionSummary expectedSummary = new InvoiceTransactionSummary().withId(invoiceId)
      .withNumPaymentsCredits(2)
      .withNumPendingPayments(2);
    verify(invoiceTransactionSummaryService).updateInvoiceTransactionSummary(eq(expectedSummary), eq(requestContext));


    ArgumentCaptor<Transaction> transactionArgumentCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(baseTransactionService, times(2)).updateTransaction(transactionArgumentCaptor.capture(), eq(requestContext));

    Transaction updateArgumentInvoiceTransaction = transactionArgumentCaptor.getAllValues().stream()
      .filter(transaction -> Objects.isNull(transaction.getSourceInvoiceLineId())).findFirst().get();;

    assertEquals(existingInvoiceTransaction.getId(), updateArgumentInvoiceTransaction.getId());
    assertEquals(expectedInvoiceTransactionAmount, updateArgumentInvoiceTransaction.getAmount());

    Transaction updateArgumentInvoiceLineTransaction = transactionArgumentCaptor.getAllValues().stream()
      .filter(transaction -> Objects.nonNull(transaction.getSourceInvoiceLineId())).findFirst().get();

    assertEquals(existingInvoiceLineTransaction.getId(), updateArgumentInvoiceLineTransaction.getId());
    assertEquals(expectedInvoiceLineTransactionAmount, updateArgumentInvoiceLineTransaction.getAmount());

  }
}
