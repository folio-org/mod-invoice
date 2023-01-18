package org.folio.services.invoice;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.folio.TestMockDataConstants.MOCK_CREDITS_LIST;
import static org.folio.TestMockDataConstants.MOCK_ENCUMBRANCES_LIST;
import static org.folio.TestMockDataConstants.MOCK_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.MOCK_PENDING_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.INVOICE_MOCK_DATA_PATH;
import static org.folio.TestMockDataConstants.INVOICE_LINES_LIST_PATH;
import static org.folio.TestMockDataConstants.VOUCHER_MOCK_DATA_PATH;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ErrorCodes.ERROR_UNRELEASING_ENCUMBRANCES;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.PENDING;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
  private RestClient restClient;
  private RequestContext requestContextMock;

  @BeforeEach
  public void initMocks() {
    requestContextMock = Mockito.mock(RequestContext.class, new RuntimeExceptionAnswer());
    restClient = Mockito.mock(RestClient.class, new RuntimeExceptionAnswer());
    doReturn(Vertx.vertx().getOrCreateContext()).when(requestContextMock).getContext();

    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    OrderTransactionSummaryService orderTransactionSummaryService = new OrderTransactionSummaryService(restClient);
    EncumbranceService encumbranceService = new EncumbranceService(baseTransactionService, orderTransactionSummaryService);
    InvoiceTransactionSummaryService invoiceTransactionSummaryService = new InvoiceTransactionSummaryService(restClient);
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(restClient);
    VoucherCommandService voucherCommandService = new VoucherCommandService(restClient, null,
      voucherRetrieveService, null, null, null);
    OrderLineService orderLineService = new OrderLineService(restClient);
    InvoiceLineService invoiceLineService = new InvoiceLineService(restClient);
    OrderService orderService = new OrderService(restClient, invoiceLineService, orderLineService);
    cancelService = new InvoiceCancelService(baseTransactionService, encumbranceService,
      invoiceTransactionSummaryService, voucherCommandService, orderLineService,
      orderService);
  }

  @Test
  public void cancelApprovedInvoiceTest() throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, false);

    CompletableFuture<Void> result = cancelService.cancelInvoice(invoice, invoiceLines, requestContextMock);
    assertFalse(result.isCompletedExceptionally());

    verifyCalls(invoiceLines);
  }

  @Test
  public void cancelPaidInvoiceTest() throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, false);

    CompletableFuture<Void> result =cancelService.cancelInvoice(invoice, invoiceLines, requestContextMock);
    assertFalse(result.isCompletedExceptionally());

    verifyCalls(invoiceLines);
  }

  @Test
  public void validateCancelInvoiceTest() throws IOException {
    Invoice invoice = getMockAs(OPENED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    HttpException exception = assertThrows(HttpException.class,
      () -> cancelService.cancelInvoice(invoice, invoiceLines, requestContextMock));
    assertEquals(422, exception.getCode());
    assertEquals(CANNOT_CANCEL_INVOICE.getDescription(), exception.getMessage());
  }

  @Test
  public void errorUnreleasingEncumbrances() throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice, true);

    CompletionException expectedException = assertThrows(CompletionException.class, () -> {
      CompletableFuture<Void> result = cancelService.cancelInvoice(invoice, invoiceLines, requestContextMock);
      result.join();
    });
    HttpException httpException = (HttpException)expectedException.getCause();
    assertEquals(500, httpException.getCode());
    assertEquals(ERROR_UNRELEASING_ENCUMBRANCES.getDescription(), httpException.getMessage());
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
    doReturn(completedFuture(trCollection)).when(restClient).get(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(requestContextMock), eq(TransactionCollection.class));
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
    doReturn(completedFuture(null)).when(restClient).put(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(summary), eq(requestContextMock));
  }

  private void setupUpdateTransactions() {
    List<TransactionType> matchedTypes = List.of(TransactionType.PAYMENT, TransactionType.PENDING_PAYMENT,
      TransactionType.CREDIT);
    doReturn(completedFuture(null)).when(restClient).put(any(),
      argThat(entity -> entity instanceof Transaction && matchedTypes.contains(((Transaction)entity).getTransactionType())),
      eq(requestContextMock));
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
    doReturn(completedFuture(voucherCollection)).when(restClient).get(argThat(re -> sameRequestEntry(getRequestEntry, re)),
      eq(requestContextMock), eq(VoucherCollection.class));
    // PUT Voucher
    RequestEntry putRequestEntry = new RequestEntry(VOUCHER_BY_ID_ENDPOINT)
      .withId(voucher.getId());
    doReturn(completedFuture(null)).when(restClient).put(argThat(re -> sameRequestEntry(putRequestEntry, re)),
      any(Voucher.class), eq(requestContextMock));
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
    doReturn(completedFuture(poLineCollection)).when(restClient).get(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(requestContextMock), eq(PoLineCollection.class));
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
    doReturn(completedFuture(orderCollection)).when(restClient).get(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(requestContextMock), eq(PurchaseOrderCollection.class));
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
    doReturn(completedFuture(transactionCollection)).when(restClient).get(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(requestContextMock), eq(TransactionCollection.class));
  }

  private void setupUpdateOrderTransactionSummary(PurchaseOrder order) {
    RequestEntry requestEntry = new RequestEntry(ORDER_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT)
      .withPathParameter("id", order.getId());
    OrderTransactionSummary summary = new OrderTransactionSummary().withId(order.getId())
        .withNumTransactions(1);
    doReturn(completedFuture(null)).when(restClient).put(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(summary), eq(requestContextMock));
  }

  private void setupEncumbrancePut(Transaction transaction) {
    RequestEntry requestEntry = new RequestEntry("/finance/encumbrances/{id}")
      .withPathParameter("id", transaction.getId());
    Transaction updatedTransaction = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    updatedTransaction.getEncumbrance().setStatus(UNRELEASED);
    doReturn(completedFuture(null)).when(restClient).put(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(updatedTransaction), eq(requestContextMock));
  }

  private void setupEncumbrancePutWithError(Transaction transaction) {
    RequestEntry requestEntry = new RequestEntry("/finance/encumbrances/{id}")
      .withPathParameter("id", transaction.getId());
    Transaction updatedTransaction = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    updatedTransaction.getEncumbrance().setStatus(UNRELEASED);
    HttpException ex = new HttpException(500, "Error test");
    doReturn(failedFuture(ex)).when(restClient).put(argThat(re -> sameRequestEntry(requestEntry, re)),
      eq(updatedTransaction), eq(requestContextMock));
  }

  private void verifyCalls(List<InvoiceLine> invoiceLines) {
    verify(restClient, times(1))
      .get(argThat(requestEntry -> ((String)requestEntry.getQueryParams().get("query")).startsWith("sourceInvoiceId")),
        eq(requestContextMock),
        eq(TransactionCollection.class));

    verify(restClient, times(1))
      .put(any(RequestEntry.class), any(InvoiceTransactionSummary.class), eq(requestContextMock));

    verify(restClient, times(3))
      .put(any(RequestEntry.class),
        argThat(entity -> entity instanceof Transaction && ((Transaction)entity).getInvoiceCancelled() != null &&
          ((Transaction)entity).getInvoiceCancelled()),
        eq(requestContextMock));

    checkInvoiceLines(invoiceLines);

    verify(restClient, times(1))
      .get(any(RequestEntry.class), eq(requestContextMock), eq(VoucherCollection.class));

    verify(restClient, times(1))
      .put(any(RequestEntry.class),
        argThat(entity -> entity instanceof Voucher && ((Voucher)entity).getStatus() == Voucher.Status.CANCELLED),
        eq(requestContextMock));

    verify(restClient, times(1))
      .get(any(RequestEntry.class), eq(requestContextMock), eq(PoLineCollection.class));

    verify(restClient, times(1))
      .get(any(RequestEntry.class), eq(requestContextMock), eq(PurchaseOrderCollection.class));

    verify(restClient, times(1))
      .get(argThat(requestEntry -> ((String)requestEntry.getQueryParams().get("query")).startsWith("transactionType")),
        eq(requestContextMock),
        eq(TransactionCollection.class));

    verify(restClient, times(1))
      .put(any(RequestEntry.class), any(OrderTransactionSummary.class), eq(requestContextMock));

    verify(restClient, times(1))
      .put(any(RequestEntry.class),
        argThat(entity -> entity instanceof Transaction &&
          ((Transaction)entity).getTransactionType().equals(TransactionType.ENCUMBRANCE)),
        eq(requestContextMock));

    verifyNoMoreInteractions(restClient);
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
