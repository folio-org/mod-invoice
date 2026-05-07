package org.folio.services.invoice;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.TestMockDataConstants.INVOICE_MOCK_DATA_PATH;
import static org.folio.TestMockDataConstants.INVOICE_LINES_LIST_PATH;
import static org.folio.TestMockDataConstants.MOCK_ENCUMBRANCES_LIST;
import static org.folio.TestMockDataConstants.MOCK_PENDING_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.MOCK_CREDITS_LIST;
import static org.folio.TestMockDataConstants.MOCK_PAYMENTS_LIST;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.CANCEL_TRANSACTIONS_ERROR;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.PENDING;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_TOKEN;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_USER_ID;
import static org.folio.services.finance.transaction.BaseTransactionServiceTest.X_OKAPI_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.voucher.VoucherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class InvoiceCancelServiceTest {

  private static final String RESOURCES_PATH = "src/test/resources";
  private static final String APPROVED_INVOICE_ID = "c0d08448-347b-418a-8c2f-5fb50248d67e";
  private static final String APPROVED_INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + APPROVED_INVOICE_ID + ".json";
  private static final String PAID_INVOICE_ID = "c15a6442-ba7d-4198-acf1-940ba99e6929";
  private static final String PAID_INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + PAID_INVOICE_ID + ".json";
  private static final String OPENED_INVOICE_ID = "52fd6ec7-ddc3-4c53-bc26-2779afc27136";
  private static final String OPENED_INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + OPENED_INVOICE_ID + ".json";

  private InvoiceCancelService cancelService;
  private AutoCloseable mockitoMocks;
  private RequestContext requestContext;

  @Mock
  private BaseTransactionService baseTransactionService;
  @Mock
  private BudgetService budgetService;
  @Mock
  private EncumbranceService encumbranceService;
  @Mock
  private InvoiceWorkflowDataHolderBuilder holderBuilder;
  @Mock
  private InvoiceLineService invoiceLineService;
  @Mock
  private OrderLineService orderLineService;
  @Mock
  private PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService;
  @Mock
  private VoucherService voucherService;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);

    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    requestContext = new RequestContext(Vertx.vertx().getOrCreateContext(), okapiHeaders);

    cancelService = new InvoiceCancelService(baseTransactionService, encumbranceService, voucherService,
      orderLineService, invoiceLineService, poLinePaymentStatusUpdateService, holderBuilder, budgetService);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  public void cancelApprovedInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupCalls(invoice);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, null, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow())
      .onFailure(vertxTestContext::failNow);
  }

  @Test
  public void cancelPaidInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupCalls(invoice);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, null, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow())
      .onFailure(vertxTestContext::failNow);
  }

  @Test
  public void validateCancelInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(OPENED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    var future = cancelService.cancelInvoice(invoice, invoiceLines, null, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        var exception = (HttpException) result.cause();
        assertEquals(422, exception.getCode());
        assertEquals(CANNOT_CANCEL_INVOICE.getDescription(), exception.getMessage());
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void validateBudgetWhenCancelInvoiceAndUndefinedFiscalYearTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    List<InvoiceWorkflowDataHolder> dataHolders = List.of();
    when(holderBuilder.buildHoldersSkeleton(anyList(), any(Invoice.class)))
      .thenReturn(dataHolders);
    when(holderBuilder.withBudgets(eq(dataHolders), anyBoolean(), eq(requestContext)))
      .thenAnswer((Answer<Future<List<InvoiceWorkflowDataHolder>>>) invocation -> failedFuture(new HttpException(404, BUDGET_NOT_FOUND.getDescription())));

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, null, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        var exception = (HttpException) result.cause();
        assertEquals(404, exception.getCode());
        assertEquals(BUDGET_NOT_FOUND.getDescription(), exception.getMessage());
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void errorWithInactiveBudgetInLinkedPol(VertxTestContext vertxTestContext) {
    String invoiceId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    Invoice invoice = new Invoice()
      .withId(invoiceId)
      .withFiscalYearId(fiscalYearId)
      .withStatus(Invoice.Status.PAID);
    FundDistribution invoiceLineFundDistribution = new FundDistribution()
      .withFundId(fundId1);
    InvoiceLine invoiceLine = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoiceId)
      .withFundDistributions(List.of(invoiceLineFundDistribution))
      .withPoLineId(poLineId)
      .withReleaseEncumbrance(true);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    var poLineFundDistribution = new org.folio.rest.acq.model.orders.FundDistribution()
      .withFundId(fundId2);
    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withPurchaseOrderId(orderId)
      .withFundDistribution(List.of(poLineFundDistribution));
    List<InvoiceWorkflowDataHolder> dataHolders = List.of();

    when(holderBuilder.buildHoldersSkeleton(anyList(), any(Invoice.class)))
      .thenReturn(dataHolders);
    when(holderBuilder.withBudgets(eq(dataHolders), anyBoolean(), eq(requestContext)))
      .thenReturn(succeededFuture(dataHolders));
    when(budgetService.getBudgetsByFundIds(anyList(), anyString(), anyBoolean(), eq(requestContext)))
      .thenReturn(failedFuture(new HttpException(404, BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID.getDescription())));
    when(orderLineService.getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext)))
      .thenReturn(succeededFuture(List.of(poLine)));

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, null, requestContext);
    assertTrue(future.failed());
    var exception = (HttpException)future.cause();
    assertEquals(404, exception.getCode());
    assertEquals(BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID.getDescription(), exception.getMessage());
    vertxTestContext.completeNow();
  }

  @Test
  public void errorCancellingTransactions(VertxTestContext vertxTestContext) throws IOException {
    String invoiceId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId1 = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    Invoice invoice = new Invoice()
      .withId(invoiceId)
      .withFiscalYearId(fiscalYearId)
      .withStatus(Invoice.Status.PAID);
    FundDistribution invoiceLineFundDistribution = new FundDistribution()
      .withFundId(fundId1);
    InvoiceLine invoiceLine = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoiceId)
      .withFundDistributions(List.of(invoiceLineFundDistribution))
      .withPoLineId(poLineId)
      .withReleaseEncumbrance(true);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    List<PoLine> poLines = List.of(
      new PoLine().withId(UUID.randomUUID().toString()),
      new PoLine().withId(UUID.randomUUID().toString())
    );

    setupHolderBuilderCalls();
    setupPoLineQuery(poLines);
    setupGetTransactions(invoice);
    when(baseTransactionService.batchCancel(anyList(), eq(requestContext)))
      .thenReturn(failedFuture(new HttpException(422, "test")));

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, null, requestContext);
    assertTrue(future.failed());
    var exception = (HttpException)future.cause();
    assertEquals(500, exception.getCode());
    assertEquals(CANCEL_TRANSACTIONS_ERROR.getDescription(), exception.getMessage());
    assertEquals("test", exception.getErrors().getErrors().getFirst().getParameters().get(1).getValue());
    vertxTestContext.completeNow();
  }

  @Test
  public void errorUnreleasingEncumbrances(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();
    setupUnreleaseEncumbrances(invoice, true);

    Future<Void> future = cancelService.updateOrUnreleaseEncumbrances(invoiceLines, invoice, requestContext);

    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        HttpException httpException = (HttpException) result.cause();
        assertEquals(500, httpException.getCode());
        var error = httpException.getErrors().getErrors().getFirst();
        assertEquals(ERROR_UNRELEASING_ENCUMBRANCES.getCode(), error.getCode());
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void testUpdateOrUnreleaseEncumbrances(VertxTestContext vertxTestContext) {
    PoLine poLine = new PoLine()
      .withId(UUID.randomUUID().toString());
    Invoice invoice = new Invoice()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(UUID.randomUUID().toString());
    InvoiceLine invoiceLine = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withReleaseEncumbrance(true)
      .withPoLineId(poLine.getId());
    setupUnreleaseEncumbrances(invoice, false);

    Future<Void> future = cancelService.updateOrUnreleaseEncumbrances(List.of(invoiceLine), invoice, requestContext);

    vertxTestContext.assertComplete(future)
      .onSuccess(result -> {
        verify(encumbranceService, times(1)).getEncumbrancesByPoLineIds(anyList(), anyString(), eq(requestContext));
        verify(baseTransactionService, times(1)).batchUnrelease(anyList(), eq(requestContext));
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);
  }

  private void setupCalls(Invoice invoice) throws IOException {
    List<PoLine> poLines = List.of(
      new PoLine().withId(UUID.randomUUID().toString()),
      new PoLine().withId(UUID.randomUUID().toString())
    );
    setupHolderBuilderCalls();
    setupPoLineQuery(poLines);
    setupGetTransactions(invoice);
    setupCancelTransactions();
    setupUpdateVoucher();
    setupPaymentStatusCalls();
  }

  private void setupHolderBuilderCalls() {
    List<InvoiceWorkflowDataHolder> dataHolders = List.of();
    when(holderBuilder.buildHoldersSkeleton(anyList(), any(Invoice.class)))
      .thenReturn(dataHolders);
    when(holderBuilder.withBudgets(eq(dataHolders), anyBoolean(), eq(requestContext)))
      .thenReturn(succeededFuture(dataHolders));
  }

  private void setupGetTransactions(Invoice invoice) throws IOException {
    TransactionCollection creditCollection = getMockAs(MOCK_CREDITS_LIST, TransactionCollection.class);
    TransactionCollection encumbranceCollection = getMockAs(MOCK_ENCUMBRANCES_LIST, TransactionCollection.class);
    TransactionCollection paymentCollection = getMockAs(MOCK_PAYMENTS_LIST, TransactionCollection.class);
    TransactionCollection pendingPaymentCollection = getMockAs(MOCK_PENDING_PAYMENTS_LIST, TransactionCollection.class);
    TransactionCollection trCollection = mergeCollections(List.of(creditCollection, encumbranceCollection,
      paymentCollection, pendingPaymentCollection));
    String query = String.format("sourceInvoiceId==%s", invoice.getId());
    when(baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext))
      .thenReturn(succeededFuture(trCollection));
  }

  private void setupCancelTransactions() {
    when(baseTransactionService.batchCancel(anyList(), eq(requestContext)))
      .thenReturn(succeededFuture(null));
  }

  private TransactionCollection mergeCollections(List<TransactionCollection> collections) {
    TransactionCollection newCollection = new TransactionCollection();
    int total = 0;
    for (TransactionCollection c : collections) {
      newCollection.getTransactions().addAll(c.getTransactions());
      total += c.getTotalRecords();
    }
    newCollection.setTotalRecords(total);
    return newCollection;
  }

  private void setupUpdateVoucher() {
    when(voucherService.cancelInvoiceVoucher(anyString(), eq(requestContext)))
      .thenReturn(succeededFuture());
  }

  private void setupUnreleaseEncumbrances(Invoice invoice, boolean withError) {
    List<PoLine> poLines = List.of(
      new PoLine().withId(UUID.randomUUID().toString()),
      new PoLine().withId(UUID.randomUUID().toString())
    );
    setupPoLineQuery(poLines);
    List<InvoiceLine> relatedInvoiceLines = List.of(
      new InvoiceLine()
        .withId(UUID.randomUUID().toString())
        .withInvoiceId(UUID.randomUUID().toString())
        .withPoLineId(poLines.getFirst().getId())
        .withReleaseEncumbrance(true)
    );
    setupInvoiceLineQuery(invoice.getId(), invoice.getFiscalYearId(), relatedInvoiceLines);
    String orderId = UUID.randomUUID().toString();
    List<Transaction> transactions = List.of(
      new Transaction().withId(UUID.randomUUID().toString())
        .withTransactionType(TransactionType.ENCUMBRANCE)
        .withEncumbrance(new Encumbrance().withStatus(PENDING).withSourcePurchaseOrderId(orderId)),
      new Transaction().withId(UUID.randomUUID().toString())
        .withTransactionType(TransactionType.ENCUMBRANCE)
        .withEncumbrance(new Encumbrance().withStatus(RELEASED).withSourcePurchaseOrderId(orderId)),
      new Transaction().withId(UUID.randomUUID().toString())
        .withTransactionType(TransactionType.ENCUMBRANCE)
        .withEncumbrance(new Encumbrance().withStatus(UNRELEASED).withSourcePurchaseOrderId(orderId))
    );
    setupEncumbranceQuery(transactions, poLines.get(1).getId(), invoice.getFiscalYearId());
    if (withError)
      setupEncumbrancePutWithError(transactions.get(1));
    else
      setupUpdateEncumbrance(transactions.get(1));
  }

  private void setupPoLineQuery(List<PoLine> poLines) {
    when(orderLineService.getPoLinesByIdAndQuery(anyList(), argThat(fn -> {
      String expectedQuery = "purchaseOrder.workflowStatus=Open AND paymentStatus==(\"Awaiting Payment\" OR " +
      "\"Partially Paid\" OR \"Fully Paid\" OR \"Ongoing\" OR \"Payment Not Required\") AND id==(id)";
      return fn.apply(List.of("id")).equals(expectedQuery);
    }), eq(requestContext)))
      .thenReturn(succeededFuture(poLines));
  }

  private void setupInvoiceLineQuery(String invoiceId, String fiscalYearId, List<InvoiceLine> invoiceLines) {
    when(invoiceLineService.getInvoiceLinesByIdsAndQuery(anyList(), argThat(fn -> {
      String expectedQuery = "invoiceId<>" + invoiceId + " AND invoiceLineStatus==(\"Paid\" OR \"Approved\") AND " +
        "releaseEncumbrance==true AND invoices.fiscalYearId==" + fiscalYearId + " AND poLineId==(id)";
      return fn.apply(List.of("id")).equals(expectedQuery);
    }), eq(requestContext)))
      .thenReturn(succeededFuture(invoiceLines));
  }

  private void setupEncumbranceQuery(List<Transaction> transactions, String poLineId, String fiscalYearId) {
    when(encumbranceService.getEncumbrancesByPoLineIds(List.of(poLineId), fiscalYearId, requestContext))
      .thenReturn(succeededFuture(transactions));
  }

  private void setupEncumbrancePutWithError(Transaction transaction) {
    HttpException ex = new HttpException(500, "Error test");
    when(baseTransactionService.batchUnrelease(List.of(transaction), requestContext))
      .thenReturn(failedFuture(ex));
  }

  private void setupUpdateEncumbrance(Transaction transaction) {
    when(baseTransactionService.batchUnrelease(List.of(transaction), requestContext))
      .thenReturn(succeededFuture(null));
  }

  private void setupPaymentStatusCalls() {
    when(poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToCancelInvoice(any(Invoice.class), anyList(), eq(null), eq(requestContext)))
      .thenReturn(succeededFuture());
  }

  private <T> T getMockAs(String path, Class<T> c) throws IOException {
    String contents = new String(Files.readAllBytes(Paths.get(RESOURCES_PATH, path)));
    return new JsonObject(contents).mapTo(c);
  }
}
