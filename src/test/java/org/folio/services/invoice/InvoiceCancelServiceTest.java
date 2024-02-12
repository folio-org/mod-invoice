package org.folio.services.invoice;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.TestMockDataConstants.INVOICE_MOCK_DATA_PATH;
import static org.folio.TestMockDataConstants.VOUCHER_MOCK_DATA_PATH;
import static org.folio.TestMockDataConstants.INVOICE_LINES_LIST_PATH;
import static org.folio.TestMockDataConstants.MOCK_ENCUMBRANCES_LIST;
import static org.folio.TestMockDataConstants.MOCK_PENDING_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.MOCK_CREDITS_LIST;
import static org.folio.TestMockDataConstants.MOCK_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.MOCK_BUDGET_ITEM;
import static org.folio.TestMockDataConstants.MOCK_BUDGETS_LIST;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.PENDING;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_TOKEN;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_USER_ID;
import static org.folio.services.finance.transaction.BaseTransactionServiceTest.X_OKAPI_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import org.folio.rest.acq.model.finance.Batch;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.BudgetCollection;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.acq.model.orders.PurchaseOrder;
import org.folio.rest.acq.model.orders.PurchaseOrder.WorkflowStatus;
import org.folio.rest.acq.model.orders.PurchaseOrderCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherService;
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
  private static final String TRANSACTIONS_ENDPOINT = resourcesPath(FINANCE_TRANSACTIONS);
  private static final String EXISTING_VOUCHER_ID = "a9b99f8a-7100-47f2-9903-6293d44a9905";
  private static final String VOUCHER_SAMPLE_PATH = VOUCHER_MOCK_DATA_PATH + EXISTING_VOUCHER_ID + ".json";

  private InvoiceCancelService cancelService;

  @Mock
  private RestClient restClient;
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);

    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    requestContext = new RequestContext(Vertx.vertx().getOrCreateContext(), okapiHeaders);

    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    EncumbranceService encumbranceService = new EncumbranceService(baseTransactionService);
    VoucherService voucherService = new VoucherService(restClient, new VoucherValidator());
    OrderLineService orderLineService = new OrderLineService(restClient);
    InvoiceLineService invoiceLineService = new InvoiceLineService(restClient);
    OrderService orderService = new OrderService(restClient, invoiceLineService, orderLineService);

    ExchangeRateProviderResolver exchangeRateProviderResolver = new ExchangeRateProviderResolver();
    FiscalYearService fiscalYearService = new FiscalYearService(restClient);
    FundService fundService = new FundService(restClient);
    LedgerService ledgerService = new LedgerService(restClient);
    BudgetService budgetService = new BudgetService(restClient);
    ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(restClient);
    InvoiceWorkflowDataHolderBuilder holderBuilder = new InvoiceWorkflowDataHolderBuilder(exchangeRateProviderResolver,
      fiscalYearService, fundService, ledgerService, baseTransactionService, budgetService, expenseClassRetrieveService);

    cancelService = new InvoiceCancelService(baseTransactionService, encumbranceService,
      voucherService, orderLineService, orderService, holderBuilder);
  }

  @Test
  public void cancelApprovedInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow())
      .onFailure(vertxTestContext::failNow);
  }

  @Test
  public void cancelPaidInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> vertxTestContext.completeNow())
      .onFailure(vertxTestContext::failNow);
  }

  @Test
  public void validateCancelInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(OPENED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    var future =  cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        var exception = (HttpException) result.cause();
        assertEquals(422, exception.getCode());
        assertEquals(CANNOT_CANCEL_INVOICE.getDescription(), exception.getMessage());
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void validateBudgetWhenCancelInvoiceAndDefinedFiscalYearTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    invoice.withFiscalYearId(UUID.randomUUID().toString());
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, true, false);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        var exception = (HttpException) result.cause();
        assertEquals(404, exception.getCode());
        assertEquals(BUDGET_NOT_FOUND_USING_FISCAL_YEAR_ID.getDescription(), exception.getMessage());
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void validateBudgetWhenCancelInvoiceAndUndefinedFiscalYearTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, true, false);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        var exception = (HttpException) result.cause();
        assertEquals(404, exception.getCode());
        assertEquals(BUDGET_NOT_FOUND.getDescription(), exception.getMessage());
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void errorUnreleasingEncumbrances(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, false, true);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        HttpException httpException = (HttpException) result.cause();
        assertEquals(500, httpException.getCode());
        var error = httpException.getErrors().getErrors().get(0);
        assertEquals(ERROR_UNRELEASING_ENCUMBRANCES.getCode(), error.getCode());
        vertxTestContext.completeNow();
      });
  }

  private void setupRestCalls(Invoice invoice) throws IOException {
    setupRestCalls(invoice, false, false);
  }

  private void setupRestCalls(Invoice invoice, boolean withEmptyBudgets, boolean withEncumbranceError) throws IOException {
    setupGetTransactions(invoice);
    setupGetBudget(withEmptyBudgets);
    setupGetBudgets(withEmptyBudgets);
    setupUpdateVoucher();
    setupUnreleaseEncumbrances(withEncumbranceError);
  }

  private void setupGetTransactions(Invoice invoice) throws IOException {
    TransactionCollection creditCollection = getMockAs(MOCK_CREDITS_LIST, TransactionCollection.class);
    TransactionCollection encumbranceCollection = getMockAs(MOCK_ENCUMBRANCES_LIST, TransactionCollection.class);
    TransactionCollection paymentCollection = getMockAs(MOCK_PAYMENTS_LIST, TransactionCollection.class);
    TransactionCollection pendingPaymentCollection = getMockAs(MOCK_PENDING_PAYMENTS_LIST, TransactionCollection.class);
    TransactionCollection trCollection = mergeCollections(List.of(creditCollection, encumbranceCollection,
      paymentCollection, pendingPaymentCollection));
    String query = String.format("sourceInvoiceId==%s", invoice.getId());
    RequestEntry requestEntry = new RequestEntry(TRANSACTIONS_ENDPOINT)
      .withQuery(query)
      .withLimit(Integer.MAX_VALUE)
      .withOffset(0);
    doReturn(succeededFuture(trCollection)).when(restClient).get(requestEntry, TransactionCollection.class, requestContext);
  }

  private void setupGetBudget(boolean withEmptyBudget) {
    when(restClient.get(any(RequestEntry.class), eq(Budget.class), eq(requestContext)))
      .thenAnswer((Answer<Future<Budget>>) invocation -> {
        if (withEmptyBudget) {
          return failedFuture(new HttpException(404, "Current budget doesn't exist"));
        } else {
          Budget budget = getMockAs(MOCK_BUDGET_ITEM, Budget.class);
          return succeededFuture(budget.withFundId(UUID.randomUUID().toString()));
        }
      });
  }

  private void setupGetBudgets(boolean withEmptyBudgets) {
    when(restClient.get(any(RequestEntry.class), eq(BudgetCollection.class), eq(requestContext)))
      .thenAnswer((Answer<Future<BudgetCollection>>) invocation -> {
        BudgetCollection budgetCollection;
        if (withEmptyBudgets) {
          budgetCollection = new BudgetCollection();
        } else {
          budgetCollection = getMockAs(MOCK_BUDGETS_LIST, BudgetCollection.class);
          budgetCollection.getBudgets().forEach(budget ->
            budget.setFundId(UUID.randomUUID().toString()));
        }
        return succeededFuture(budgetCollection);
      });
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

  private void setupUpdateVoucher() throws IOException {
    // GET Voucher
    Voucher voucher = getMockAs(VOUCHER_SAMPLE_PATH, Voucher.class);
    VoucherCollection voucherCollection = new VoucherCollection()
      .withVouchers(singletonList(voucher))
      .withTotalRecords(1);
    doReturn(succeededFuture(voucherCollection)).when(restClient).get(any(RequestEntry.class), eq(VoucherCollection.class), eq(requestContext));
    // PUT Voucher
    doReturn(succeededFuture(null)).when(restClient).put(any(RequestEntry.class), any(Voucher.class), eq(requestContext));
  }

  private void setupUnreleaseEncumbrances(boolean withError) {
    String orderId1 = UUID.randomUUID().toString();
    String orderId2 = UUID.randomUUID().toString();
    List<PurchaseOrder> orders = List.of(
      new PurchaseOrder().withId(orderId1).withWorkflowStatus(WorkflowStatus.PENDING),
      new PurchaseOrder().withId(orderId2).withWorkflowStatus(WorkflowStatus.OPEN)
    );
    List<PoLine> poLines = List.of(
      new PoLine().withId("2bafc9e1-9dd3-4ede-9f23-c4a03f8bb2d5").withPurchaseOrderId(orderId1),
      new PoLine().withId("5a34ae0e-5a11-4337-be95-1a20cfdc3161").withPurchaseOrderId(orderId2),
      new PoLine().withId("0610be6d-0ddd-494b-b867-19f63d8b5d6d").withPurchaseOrderId(orderId2)
    );
    List<Transaction> transactions = List.of(
      new Transaction().withId(UUID.randomUUID().toString())
        .withTransactionType(TransactionType.ENCUMBRANCE)
        .withEncumbrance(new Encumbrance().withStatus(PENDING).withSourcePurchaseOrderId(orderId2)),
      new Transaction().withId(UUID.randomUUID().toString())
        .withTransactionType(TransactionType.ENCUMBRANCE)
        .withEncumbrance(new Encumbrance().withStatus(RELEASED).withSourcePurchaseOrderId(orderId2)),
      new Transaction().withId(UUID.randomUUID().toString())
        .withTransactionType(TransactionType.ENCUMBRANCE)
        .withEncumbrance(new Encumbrance().withStatus(UNRELEASED).withSourcePurchaseOrderId(orderId2))
    );
    setupPoLineQuery(poLines);
    setupOrderQuery(orders);
    setupEncumbranceQuery(transactions);
    if (withError)
      setupEncumbrancePutWithError(transactions.get(1));
    else
      setupUpdateEncumbrance(transactions.get(1));
  }

  private void setupPoLineQuery(List<PoLine> poLines) {
    PoLineCollection poLineCollection = new PoLineCollection()
      .withPoLines(poLines)
      .withTotalRecords(poLines.size());
    doReturn(succeededFuture(poLineCollection)).when(restClient).get(any(RequestEntry.class), eq(PoLineCollection.class), eq(
      requestContext));
  }

  private void setupOrderQuery(List<PurchaseOrder> orders) {
    List<PurchaseOrder> openOrders = List.of(orders.get(1));
    PurchaseOrderCollection orderCollection = new PurchaseOrderCollection()
      .withPurchaseOrders(openOrders)
      .withTotalRecords(openOrders.size());
    doReturn(succeededFuture(orderCollection)).when(restClient).get(any(RequestEntry.class), eq(PurchaseOrderCollection.class), eq(
      requestContext));
  }

  private void setupEncumbranceQuery(List<Transaction> transactions) {
    TransactionCollection transactionCollection = new TransactionCollection()
      .withTransactions(transactions)
      .withTotalRecords(transactions.size());
    doReturn(succeededFuture(transactionCollection)).when(restClient).get(any(RequestEntry.class), eq(TransactionCollection.class), eq(
      requestContext));
  }

  private void setupUpdateEncumbrance(Transaction transaction) {
    String endpoint = "/finance/transactions/batch-all-or-nothing";
    Transaction updatedTransaction = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    updatedTransaction.getEncumbrance().setStatus(UNRELEASED);
    Batch updateBatch = new Batch()
      .withTransactionsToUpdate(List.of(updatedTransaction));
    doReturn(succeededFuture(null)).when(restClient)
      .postEmptyResponse(eq(endpoint), eq(updateBatch), eq(requestContext));
  }

  private void setupEncumbrancePutWithError(Transaction transaction) {
    String endpoint = "/finance/transactions/batch-all-or-nothing";
    Transaction updatedTransaction = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    updatedTransaction.getEncumbrance().setStatus(UNRELEASED);
    Batch updateBatch = new Batch()
      .withTransactionsToUpdate(List.of(updatedTransaction));
    HttpException ex = new HttpException(500, "Error test");
    doReturn(failedFuture(ex)).when(restClient)
      .postEmptyResponse(eq(endpoint), eq(updateBatch), eq(requestContext));
  }

  private <T> T getMockAs(String path, Class<T> c) throws IOException {
    String contents = new String(Files.readAllBytes(Paths.get(RESOURCES_PATH, path)));
    return new JsonObject(contents).mapTo(c);
  }
}
