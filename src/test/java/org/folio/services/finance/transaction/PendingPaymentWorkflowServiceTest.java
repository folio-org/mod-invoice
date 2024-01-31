package org.folio.services.finance.transaction;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.AbstractHelper.DEFAULT_SYSTEM_CURRENCY;
import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;

import io.vertx.core.Future;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Batch;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Metadata;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.exchange.ManualCurrencyConversion;
import org.folio.services.validator.FundAvailabilityHolderValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Vertx;

public class PendingPaymentWorkflowServiceTest {

  private PendingPaymentWorkflowService pendingPaymentWorkflowService;

  @Mock
  private RestClient restClient;
  @Mock
  private FundAvailabilityHolderValidator fundAvailabilityValidator;
  @Mock
  private EncumbranceService encumbranceService;
  @Mock
  private RequestContext requestContext;
  @Mock
  private ManualCurrencyConversion conversion;
  private AutoCloseable mockitoMocks;



  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    pendingPaymentWorkflowService = new PendingPaymentWorkflowService(baseTransactionService, encumbranceService,
      fundAvailabilityValidator);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
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
      .withAmount(50d)
      .withMetadata(new Metadata()
        .withCreatedDate(new Date()));

    Transaction existingInvoiceTransaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withSourceInvoiceId(invoiceId)
      .withAmount(10d)
      .withMetadata(new Metadata()
        .withCreatedDate(new Date()));


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
    when(restClient.postEmptyResponse(any(), any(), any())).thenReturn(succeededFuture());

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


    ArgumentCaptor<Batch> transactionArgumentCaptor = ArgumentCaptor.forClass(Batch.class);
    verify(restClient, times(1))
      .postEmptyResponse(anyString(), transactionArgumentCaptor.capture(), eq(requestContext));
    Batch batch = transactionArgumentCaptor.getValue();
    List<Transaction> transactions = batch.getTransactionsToUpdate();
    assertThat(transactions, hasSize(2));

    Transaction updateArgumentInvoiceTransaction = transactions.stream()
      .filter(transaction -> Objects.isNull(transaction.getSourceInvoiceLineId())).findFirst().get();

    assertEquals(existingInvoiceTransaction.getId(), updateArgumentInvoiceTransaction.getId());
    assertEquals(expectedInvoiceTransactionAmount, updateArgumentInvoiceTransaction.getAmount());

    Transaction updateArgumentInvoiceLineTransaction = transactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getSourceInvoiceLineId())).findFirst().get();

    assertEquals(existingInvoiceLineTransaction.getId(), updateArgumentInvoiceLineTransaction.getId());
    assertEquals(expectedInvoiceLineTransactionAmount, updateArgumentInvoiceLineTransaction.getAmount());
  }

  @Test
  void errorInCleanupOldEncumbrances() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String invoiceLineId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    FiscalYear fiscalYear = new FiscalYear()
      .withId(fiscalYearId)
      .withCurrency("USD");
    FundDistribution fundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.AMOUNT)
      .withFundId(fundId1)
      .withValue(1.0);
    Invoice invoice = new Invoice()
      .withId(invoiceId)
      .withSubTotal(50d)
      .withCurrency("EUR")
      .withFiscalYearId(fiscalYearId);
    InvoiceLine invoiceLine = new InvoiceLine()
      .withSubTotal(60d)
      .withTotal(60d)
      .withId(invoiceLineId)
      .withPoLineId(poLineId);
    double exchangeRate = 1.3;
    ConversionQuery conversionQuery = ConversionQueryBuilder.of()
      .setTermCurrency(DEFAULT_SYSTEM_CURRENCY).set(RATE_KEY, exchangeRate).build();
    ExchangeRateProvider exchangeRateProvider = new ExchangeRateProviderResolver().resolve(conversionQuery,
      new RequestContext(Vertx.currentContext(), Collections.emptyMap()));
    CurrencyConversion conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery);
    InvoiceWorkflowDataHolder holder = new InvoiceWorkflowDataHolder()
      .withFundDistribution(fundDistribution)
      .withInvoice(invoice)
      .withInvoiceLine(invoiceLine)
      .withConversion(conversion)
      .withFiscalYear(fiscalYear);
    List<InvoiceWorkflowDataHolder> dataHolders = List.of(holder);
    Transaction encumbrance = new Transaction()
      .withFromFundId(fundId2)
        .withEncumbrance(new Encumbrance()
          .withStatus(Encumbrance.Status.UNRELEASED));

    doNothing().when(fundAvailabilityValidator).validate(anyList());
    when(restClient.postEmptyResponse(anyString(), any(), eq(requestContext)))
      .thenAnswer(invocation -> {
        if (!((Batch)invocation.getArgument(1)).getTransactionsToCreate().isEmpty()) {
          // successful creation of pending payments
          return succeededFuture();
        } else {
          // fail when updating encumbrances
          return failedFuture(new Exception("test"));
        }
      });
    when(encumbranceService.getEncumbrancesByPoLineIds(anyList(), eq(fiscalYearId), eq(requestContext)))
      .thenReturn(succeededFuture(List.of(encumbrance)));

    Future<Void> future = pendingPaymentWorkflowService.handlePendingPaymentsCreation(dataHolders, invoice, requestContext);
    assertEquals("Failed to create pending payments: test", future.cause().getMessage());
  }
}
