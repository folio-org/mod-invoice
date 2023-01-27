package org.folio.services.invoice;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.TestMockDataConstants.INVOICE_LINES_LIST_PATH;
import static org.folio.TestMockDataConstants.INVOICE_MOCK_DATA_PATH;
import static org.folio.TestMockDataConstants.MOCK_CREDITS_LIST;
import static org.folio.TestMockDataConstants.MOCK_ENCUMBRANCES_LIST;
import static org.folio.TestMockDataConstants.MOCK_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.MOCK_PENDING_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.VOUCHER_MOCK_DATA_PATH;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.OrderTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
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
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.InvoiceTransactionSummaryService;
import org.folio.services.finance.transaction.OrderTransactionSummaryService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.VoucherValidator;
import org.folio.services.voucher.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
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
  private static final String INVOICE_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT = resourcesPath(INVOICE_TRANSACTION_SUMMARIES) + "/{id}";
  private static final String VOUCHER_ENDPOINT = resourcesPath(VOUCHERS_STORAGE);
  private static final String EXISTING_VOUCHER_ID = "a9b99f8a-7100-47f2-9903-6293d44a9905";
  private static final String VOUCHER_SAMPLE_PATH = VOUCHER_MOCK_DATA_PATH + EXISTING_VOUCHER_ID + ".json";
  private static final String VOUCHER_BY_ID_ENDPOINT = resourcesPath(VOUCHERS_STORAGE) + "/{id}";
  private static final String ORDER_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT = resourcesPath(ORDER_TRANSACTION_SUMMARIES) + "/{id}";

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
    OrderTransactionSummaryService orderTransactionSummaryService = new OrderTransactionSummaryService(restClient);
    EncumbranceService encumbranceService = new EncumbranceService(baseTransactionService, orderTransactionSummaryService);
    InvoiceTransactionSummaryService invoiceTransactionSummaryService = new InvoiceTransactionSummaryService(restClient);
    VoucherService voucherService = new VoucherService(restClient, new VoucherValidator());
    OrderLineService orderLineService = new OrderLineService(restClient);
    InvoiceLineService invoiceLineService = new InvoiceLineService(restClient);
    OrderService orderService = new OrderService(restClient, invoiceLineService, orderLineService);
    cancelService = new InvoiceCancelService(baseTransactionService, encumbranceService,
      invoiceTransactionSummaryService, voucherService, orderLineService,
      orderService);
  }

  @Test
  public void cancelApprovedInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, false);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> {
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);

  }

  @Test
  public void cancelPaidInvoiceTest(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, false);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertComplete(future)
      .onSuccess(result -> {
        vertxTestContext.completeNow();
      })
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
  public void errorUnreleasingEncumbrances(VertxTestContext vertxTestContext) throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, true);

    Future<Void> future = cancelService.cancelInvoice(invoice, invoiceLines, requestContext);
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        HttpException httpException = (HttpException) result.cause();
        assertEquals(500, httpException.getCode());
        assertEquals(ERROR_UNRELEASING_ENCUMBRANCES.getDescription(), httpException.getMessage());
        vertxTestContext.completeNow();
      });

  }

  private void setupRestCalls(Invoice invoice, boolean withError) throws IOException {
    setupGetTransactions(invoice);
    setupUpdateTransactionSummary(invoice);
    setupUpdateTransactions();
    setupUpdateVoucher(invoice);
    setupUnreleaseEncumbrances(withError);
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

  private boolean sameRequestEntry(RequestEntry entry1, RequestEntry entry2) {
    return entry1.buildEndpoint().equals(entry2.buildEndpoint());
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

  private void setupUpdateTransactionSummary(Invoice invoice) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT)
      .withPathParameter("id", invoice.getId());
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary().withId(invoice.getId())
      .withNumPaymentsCredits(3)
      .withNumPendingPayments(1);
    doReturn(succeededFuture(null)).when(restClient).put(requestEntry, summary, requestContext);
  }

  private void setupUpdateTransactions() {
    List<TransactionType> matchedTypes = List.of(TransactionType.PAYMENT, TransactionType.PENDING_PAYMENT,
      TransactionType.CREDIT);
  //  doReturn(succeededFuture(null)).when(restClient).put(any(RequestEntry.class), any(Transaction.class), eq(requestContext));
  }

  private void setupUpdateVoucher(Invoice invoice) throws IOException {
    // GET Voucher
    Voucher voucher = getMockAs(VOUCHER_SAMPLE_PATH, Voucher.class);
    VoucherCollection voucherCollection = new VoucherCollection()
      .withVouchers(singletonList(voucher))
      .withTotalRecords(1);
    RequestEntry getRequestEntry = new RequestEntry(VOUCHER_ENDPOINT)
      .withQuery(String.format("invoiceId==%s", invoice.getId()))
      .withLimit(1)
      .withOffset(0);
    doReturn(succeededFuture(voucherCollection)).when(restClient).get(any(RequestEntry.class), eq(VoucherCollection.class), eq(requestContext));
    // PUT Voucher
    RequestEntry putRequestEntry = new RequestEntry(VOUCHER_BY_ID_ENDPOINT)
      .withId(voucher.getId());
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
    setupEncumbranceQuery(orders, poLines, transactions);
    setupUpdateOrderTransactionSummary(orders.get(1));
    if (withError)
      setupEncumbrancePutWithError(transactions.get(1));
    else
      setupEncumbrancePut(transactions.get(1));
  }

  private void setupPoLineQuery(List<PoLine> poLines) {
    String poLineIdsQuery = poLines.stream()
      .map(PoLine::getId)
      .collect(joining(" or "));
    String poLineQuery = "paymentStatus==(\"Awaiting Payment\" OR \"Partially Paid\" OR \"Fully Paid\" OR \"Ongoing\") AND id==(" +
      poLineIdsQuery + ")";
    PoLineCollection poLineCollection = new PoLineCollection()
      .withPoLines(poLines)
      .withTotalRecords(poLines.size());
    RequestEntry requestEntry = new RequestEntry("/orders/order-lines")
      .withQuery(poLineQuery)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    doReturn(succeededFuture(poLineCollection)).when(restClient).get(any(RequestEntry.class), eq(PoLineCollection.class), eq(
      requestContext));
  }

  private void setupOrderQuery(List<PurchaseOrder> orders) {
    List<String> orderIds = orders.stream().map(PurchaseOrder::getId).collect(toList());
    List<PurchaseOrder> openOrders = List.of(orders.get(1));
    String orderIdsQuery = String.join(" or ", orderIds);
    String orderQuery = "workflowStatus==\"Open\" AND id==(" + orderIdsQuery + ")";
    PurchaseOrderCollection orderCollection = new PurchaseOrderCollection()
      .withPurchaseOrders(openOrders)
      .withTotalRecords(openOrders.size());
    RequestEntry requestEntry = new RequestEntry("/orders/composite-orders")
      .withQuery(orderQuery)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    doReturn(succeededFuture(orderCollection)).when(restClient).get(any(RequestEntry.class), eq(PurchaseOrderCollection.class), eq(
      requestContext));
  }

  private void setupEncumbranceQuery(List<PurchaseOrder> orders, List<PoLine> poLines,
      List<Transaction> transactions) {
    List<PoLine> selectedPoLines = poLines.stream()
      .filter(line -> orders.stream().anyMatch(order -> order.getId().equals(line.getPurchaseOrderId()) &&
        order.getWorkflowStatus().equals(WorkflowStatus.OPEN)))
      .collect(toList());
    String poLineIdsQuery = selectedPoLines.stream()
      .map(PoLine::getId)
      .collect(joining(" or "));
    String transactionQuery = "transactionType==Encumbrance and encumbrance.sourcePoLineId==(" + poLineIdsQuery + ")";
    TransactionCollection transactionCollection = new TransactionCollection()
      .withTransactions(transactions)
      .withTotalRecords(transactions.size());
    RequestEntry requestEntry = new RequestEntry(TRANSACTIONS_ENDPOINT)
      .withQuery(transactionQuery)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);
    doReturn(succeededFuture(transactionCollection)).when(restClient).get(any(RequestEntry.class), eq(TransactionCollection.class), eq(
      requestContext));
  }

  private void setupUpdateOrderTransactionSummary(PurchaseOrder order) {
    RequestEntry requestEntry = new RequestEntry(ORDER_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT)
      .withPathParameter("id", order.getId());
    OrderTransactionSummary summary = new OrderTransactionSummary().withId(order.getId())
        .withNumTransactions(1);
    doReturn(succeededFuture(null)).when(restClient).put(any(RequestEntry.class), eq(summary), eq(requestContext));
  }

  private void setupEncumbrancePut(Transaction transaction) {
    RequestEntry requestEntry = new RequestEntry("/finance/encumbrances/{id}")
      .withPathParameter("id", transaction.getId());
    Transaction updatedTransaction = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    updatedTransaction.getEncumbrance().setStatus(UNRELEASED);
    doReturn(succeededFuture(null)).when(restClient).put(any(RequestEntry.class), eq(updatedTransaction), any(RequestContext.class));
  }

  private void setupEncumbrancePutWithError(Transaction transaction) {
    RequestEntry requestEntry = new RequestEntry("/finance/encumbrances/{id}")
      .withPathParameter("id", transaction.getId());
    Transaction updatedTransaction = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    updatedTransaction.getEncumbrance().setStatus(UNRELEASED);
    HttpException ex = new HttpException(500, "Error test");
    doReturn(failedFuture(ex)).when(restClient).put(requestEntry, updatedTransaction, requestContext);
  }

  private void checkInvoiceLines(List<InvoiceLine> invoiceLines) {
    assertTrue(invoiceLines.stream().allMatch(line -> line.getInvoiceLineStatus() == InvoiceLine.InvoiceLineStatus.CANCELLED));
  }

  private <T> T getMockAs(String path, Class<T> c) throws IOException {
    String contents = new String(Files.readAllBytes(Paths.get(RESOURCES_PATH, path)));
    return new JsonObject(contents).mapTo(c);
  }

  private static class RuntimeExceptionAnswer implements Answer<Object> {
    // This is useful to check a null result with a given stub.
    // NOTE: doReturn() must be used with this.
    public Object answer(InvocationOnMock invocation) {
      Method method = invocation.getMethod();
      throw new RuntimeException(method.getDeclaringClass().getName() + "." + method.getName() +
        "() invocation was not stubbed");
    }
  }
}
