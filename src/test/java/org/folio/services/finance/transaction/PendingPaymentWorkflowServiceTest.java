package org.folio.services.finance.transaction;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_BATCH_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.acq.model.finance.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.services.exchange.CustomExchangeRateProvider.RATE_KEY;
import static org.folio.services.settings.CommonSettingsService.CURRENCY_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Batch;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Metadata;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.CustomExchangeRateProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Vertx;

public class PendingPaymentWorkflowServiceTest {

  @Mock
  private RestClient restClient;
  @Mock
  private EncumbranceService encumbranceService;
  @Mock
  private RequestContext requestContext;

  private PendingPaymentWorkflowService pendingPaymentWorkflowService;
  private AutoCloseable mockitoMocks;

  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    pendingPaymentWorkflowService = new PendingPaymentWorkflowService(baseTransactionService, encumbranceService);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  @SuppressWarnings("unchecked")
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

    ConversionQuery conversionQuery = ConversionQueryBuilder.of().setTermCurrency(CURRENCY_DEFAULT).set(RATE_KEY, exchangeRate).build();
    ExchangeRateProvider exchangeRateProvider = new CustomExchangeRateProvider();
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

    when(restClient.postEmptyResponse(any(), any(), any())).thenReturn(succeededFuture());

    when(requestContext.getContext()).thenReturn(Vertx.vertx().getOrCreateContext());

    pendingPaymentWorkflowService.handlePendingPaymentsUpdate(holders, requestContext);

    ArgumentCaptor<Batch> transactionArgumentCaptor = ArgumentCaptor.forClass(Batch.class);
    verify(restClient, times(1))
      .postEmptyResponse(anyString(), transactionArgumentCaptor.capture(), eq(requestContext));
    Batch batch = transactionArgumentCaptor.getValue();
    List<Transaction> transactions = batch.getTransactionsToUpdate();
    assertThat(transactions, hasSize(2));

    Transaction newInvoiceTransaction = transactions.stream()
      .filter(transaction -> Objects.isNull(transaction.getSourceInvoiceLineId())).findFirst().orElseThrow();
    Transaction newInvoiceLineTransaction = transactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getSourceInvoiceLineId())).findFirst().orElseThrow();

    double expectedInvoiceLineTransactionAmount = BigDecimal.valueOf(60).multiply(BigDecimal.valueOf(exchangeRate)).doubleValue();
    assertEquals(existingInvoiceLineTransaction.getId(), newInvoiceLineTransaction.getId());
    assertEquals(expectedInvoiceLineTransactionAmount, newInvoiceLineTransaction.getAmount());
    assertEquals(fundId, newInvoiceLineTransaction.getFromFundId());
    assertEquals(fiscalYearId, newInvoiceLineTransaction.getFiscalYearId());
    assertEquals(invoiceId, newInvoiceLineTransaction.getSourceInvoiceId());
    assertEquals(invoiceLineId, newInvoiceLineTransaction.getSourceInvoiceLineId());
    assertEquals(PENDING_PAYMENT, newInvoiceLineTransaction.getTransactionType());
    assertEquals(Transaction.Source.INVOICE, newInvoiceLineTransaction.getSource());

    double expectedInvoiceTransactionAmount = BigDecimal.valueOf(30.5).multiply(BigDecimal.valueOf(exchangeRate)).doubleValue();
    assertEquals(existingInvoiceTransaction.getId(), newInvoiceTransaction.getId());
    assertEquals(expectedInvoiceTransactionAmount, newInvoiceTransaction.getAmount());
    assertEquals(fundId, newInvoiceTransaction.getFromFundId());
    assertEquals(fiscalYearId, newInvoiceTransaction.getFiscalYearId());
    assertEquals(invoiceId, newInvoiceTransaction.getSourceInvoiceId());
    assertNull(newInvoiceTransaction.getSourceInvoiceLineId());
    assertEquals(PENDING_PAYMENT, newInvoiceTransaction.getTransactionType());
    assertEquals(Transaction.Source.INVOICE, newInvoiceTransaction.getSource());
  }

  @Test
  void forwardExceptionWhenUpdatingPendingPayments() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String invoiceLineId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    FiscalYear fiscalYear = new FiscalYear()
      .withId(fiscalYearId)
      .withCurrency("USD");
    FundDistribution fundDistribution = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.AMOUNT)
      .withFundId(fundId)
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
      .setTermCurrency(CURRENCY_DEFAULT).set(RATE_KEY, exchangeRate).build();
    ExchangeRateProvider exchangeRateProvider = new CustomExchangeRateProvider();
    CurrencyConversion conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery);
    InvoiceWorkflowDataHolder holder = new InvoiceWorkflowDataHolder()
      .withFundDistribution(fundDistribution)
      .withInvoice(invoice)
      .withInvoiceLine(invoiceLine)
      .withConversion(conversion)
      .withFiscalYear(fiscalYear);
    List<InvoiceWorkflowDataHolder> holders = List.of(holder);

    when(restClient.postEmptyResponse(eq(resourcesPath(FINANCE_BATCH_TRANSACTIONS)), any(), eq(requestContext)))
      .thenAnswer(invocation -> {
        // simulate an error coming from mod-finance-storage
        Error error = new Error()
          .withCode("budgetRestrictedExpendituresError")
          .withMessage("Expenditure restriction does not allow this operation");
        return failedFuture(new HttpException(422, error));
      });

    Future<Void> future = pendingPaymentWorkflowService.handlePendingPaymentsUpdate(holders, requestContext);

    assertTrue(future.failed());
    HttpException httpException = (HttpException) future.cause();
    assertEquals(422, httpException.getCode());
    Error error = httpException.getErrors().getErrors().getFirst();
    assertEquals("budgetRestrictedExpendituresError", error.getCode());
    assertEquals("Expenditure restriction does not allow this operation", error.getMessage());
  }

  @Test
  void errorInHandlePendingPaymentsCreation() {
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
      .setTermCurrency(CURRENCY_DEFAULT).set(RATE_KEY, exchangeRate).build();
    ExchangeRateProvider exchangeRateProvider = new CustomExchangeRateProvider();
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
          .withStatus(UNRELEASED));

    when(restClient.postEmptyResponse(eq(resourcesPath(FINANCE_BATCH_TRANSACTIONS)), any(), eq(requestContext)))
      .thenAnswer(invocation -> {
        // fail when creating pending payments and updating encumbrances
        return failedFuture(new Exception("test"));
      });
    when(encumbranceService.getEncumbrancesByPoLineIds(anyList(), eq(fiscalYearId), eq(requestContext)))
      .thenReturn(succeededFuture(List.of(encumbrance)));

    Future<List<InvoiceWorkflowDataHolder>> future = pendingPaymentWorkflowService.handlePendingPaymentsCreation(
      dataHolders, invoice, requestContext);

    assertTrue(future.failed());
    assertEquals("Failed to create pending payments", future.cause().getMessage());
    HttpException httpException = (HttpException) future.cause();
    Error error = httpException.getErrors().getErrors().getFirst();
    assertEquals("cause", error.getParameters().getFirst().getKey());
    assertEquals("test", error.getParameters().getFirst().getValue());
  }

  @Test
  void testRollbackCreationOfPendingPayments() {
    Transaction pendingPayment = new Transaction()
      .withTransactionType(PENDING_PAYMENT);
    Transaction encumbrance = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(ENCUMBRANCE)
      .withEncumbrance(new Encumbrance().withStatus(UNRELEASED));
    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
      .withNewTransaction(pendingPayment)
      .withEncumbrance(encumbrance);
    List<InvoiceWorkflowDataHolder> holders = List.of(holder1);
    Transaction newEncumbrance = new Transaction()
      .withId(encumbrance.getId())
      .withTransactionType(ENCUMBRANCE)
      .withEncumbrance(new Encumbrance().withStatus(RELEASED));
    TransactionCollection transactionCollection = new TransactionCollection()
      .withTransactions(List.of(newEncumbrance));

    ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
    when(restClient.postEmptyResponse(anyString(), any(Batch.class), eq(requestContext)))
      .thenReturn(succeededFuture());
    when(restClient.get(any(RequestEntry.class), eq(TransactionCollection.class), eq(requestContext)))
      .thenReturn(succeededFuture(transactionCollection));

    Future<Void> future = pendingPaymentWorkflowService.rollbackCreationOfPendingPayments(holders, requestContext);

    assertTrue(future.succeeded());
    verify(restClient, times(2))
      .postEmptyResponse(anyString(), batchCaptor.capture(), eq(requestContext));
    verify(restClient, times(1))
      .get(any(RequestEntry.class), eq(TransactionCollection.class), eq(requestContext));
    List<Batch> batches = batchCaptor.getAllValues();
    assertThat(batches, hasSize(2));
    Batch firstBatch = batches.getFirst();
    assertThat(firstBatch.getIdsOfTransactionsToDelete(), hasSize(1));
    Batch secondBatch = batches.get(1);
    assertThat(secondBatch.getTransactionsToUpdate(), hasSize(1));
    assertEquals(UNRELEASED, secondBatch.getTransactionsToUpdate().getFirst().getEncumbrance().getStatus());
  }
}
