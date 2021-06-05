package org.folio.services.finance.transaction;

import static org.folio.rest.impl.AbstractHelper.DEFAULT_SYSTEM_CURRENCY;
import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;

import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.ManualCurrencyConversion;
import org.folio.services.validator.FundAvailabilityHolderValidator;
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
  private InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  @Mock
  private FundAvailabilityHolderValidator fundAvailabilityValidator;
  @Mock
  private RequestContext requestContext;
  @Mock
  private ManualCurrencyConversion conversion;



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
      .withSubTotal(60d)
      .withTotal(60d)
      .withId(invoiceLineId);

    FundDistribution invoiceLineFundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.AMOUNT)
      .withFundId(fundId)
      .withValue(60d);

    invoiceLine.getFundDistributions().add(invoiceLineFundDistribution);

    ConversionQuery conversionQuery = ConversionQueryBuilder.of().setTermCurrency(DEFAULT_SYSTEM_CURRENCY).set(RATE_KEY, exchangeRate).build();
    ExchangeRateProvider exchangeRateProvider = new ExchangeRateProviderResolver().resolve(conversionQuery, new RequestContext(
      Vertx.currentContext(), Collections.emptyMap()));
    CurrencyConversion conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();

    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
            .withInvoice(invoice)
            .withInvoiceLine(invoiceLine)
            .withFundDistribution(invoiceLineFundDistribution)
            .withFiscalYear(fiscalYear)
            .withExistingTransaction(existingInvoiceLineTransaction)
            .withConversion(conversion);

    InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
            .withInvoice(invoice)
            .withAdjustment(adjustment)
            .withFundDistribution(invoiceFundDistribution)
            .withFiscalYear(fiscalYear)
            .withExistingTransaction(existingInvoiceTransaction)
            .withConversion(conversion);

    holders.add(holder1);
    holders.add(holder2);


    doNothing().when(fundAvailabilityValidator).validate(anyList());
    when(invoiceTransactionSummaryService.updateInvoiceTransactionSummary(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(baseTransactionService.updateTransaction(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    pendingPaymentWorkflowService.handlePendingPaymentsUpdate(holders, requestContext);


    ArgumentCaptor<List<InvoiceWorkflowDataHolder>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(fundAvailabilityValidator).validate(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue(), hasSize(2));
    List<InvoiceWorkflowDataHolder> holdersWithNewTransactions = argumentCaptor.getValue();
    Transaction newInvoiceTransaction = holdersWithNewTransactions.stream()
            .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .filter(transaction -> Objects.isNull(transaction.getSourceInvoiceLineId())).findFirst().get();
    Transaction newInvoiceLineTransaction = holdersWithNewTransactions.stream().map(InvoiceWorkflowDataHolder::getNewTransaction)
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
