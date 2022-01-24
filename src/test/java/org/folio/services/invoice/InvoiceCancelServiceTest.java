package org.folio.services.invoice;

import io.vertx.core.json.JsonObject;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.folio.services.finance.transaction.InvoiceTransactionSummaryService;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.TestMockDataConstants.MOCK_CREDITS_LIST;
import static org.folio.TestMockDataConstants.MOCK_ENCUMBRANCES_LIST;
import static org.folio.TestMockDataConstants.MOCK_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.MOCK_PENDING_PAYMENTS_LIST;
import static org.folio.TestMockDataConstants.INVOICE_MOCK_DATA_PATH;
import static org.folio.TestMockDataConstants.INVOICE_LINES_LIST_PATH;
import static org.folio.TestMockDataConstants.VOUCHER_MOCK_DATA_PATH;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_CANCEL_INVOICE;
import static org.folio.invoices.utils.ResourcePathResolver.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

  private InvoiceCancelService cancelService;
  @Mock
  private RestClient restClient;
  @Mock
  private RequestContext requestContextMock;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    InvoiceTransactionSummaryService invoiceTransactionSummaryService = new InvoiceTransactionSummaryService(restClient);
    VoucherRetrieveService voucherRetrieveService = new VoucherRetrieveService(restClient);
    VoucherCommandService voucherCommandService = new VoucherCommandService(restClient, null,
      voucherRetrieveService, null, null, null);
    cancelService = new InvoiceCancelService(baseTransactionService, invoiceTransactionSummaryService,
      voucherCommandService);
  }

  @Test
  public void cancelApprovedInvoiceTest() throws IOException {
    Invoice invoice = getMockAs(APPROVED_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice);

    cancelService.cancelInvoice(invoice, invoiceLines, requestContextMock);

    verifyCalls(invoiceLines);
  }

  @Test
  public void cancelPaidInvoiceTest() throws IOException {
    Invoice invoice = getMockAs(PAID_INVOICE_SAMPLE_PATH, Invoice.class);
    List<InvoiceLine> invoiceLines = getMockAs(INVOICE_LINES_LIST_PATH, InvoiceLineCollection.class).getInvoiceLines();

    setupRestCalls(invoice);

    cancelService.cancelInvoice(invoice, invoiceLines, requestContextMock);

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

  private void setupRestCalls(Invoice invoice) throws IOException {
    setupGetTransactions(invoice);
    setupUpdateTransactionSummary(invoice);
    setupUpdateTransactions();
    setupUpdateVoucher(invoice);
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
    doReturn(completedFuture(null)).when(restClient).put(any(), any(Transaction.class), eq(requestContextMock));
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

  private void verifyCalls(List<InvoiceLine> invoiceLines) {
    verify(restClient, times(1))
      .get(any(RequestEntry.class), eq(requestContextMock), eq(TransactionCollection.class));

    verify(restClient, times(1))
      .put(any(RequestEntry.class), any(InvoiceTransactionSummary.class), eq(requestContextMock));

    verify(restClient, times(3))
      .put(any(RequestEntry.class),
        argThat(entity -> entity instanceof Transaction && ((Transaction)entity).getInvoiceCancelled()),
        eq(requestContextMock));

    checkInvoiceLines(invoiceLines);

    verify(restClient, times(1))
      .get(any(RequestEntry.class), eq(requestContextMock), eq(VoucherCollection.class));

    verify(restClient, times(1))
      .put(any(RequestEntry.class),
        argThat(entity -> entity instanceof Voucher && ((Voucher)entity).getStatus() == Voucher.Status.CANCELLED),
        eq(requestContextMock));
  }

  private void checkInvoiceLines(List<InvoiceLine> invoiceLines) {
    assertTrue(invoiceLines.stream().allMatch(line -> line.getInvoiceLineStatus() == InvoiceLine.InvoiceLineStatus.CANCELLED));
  }

  private <T> T getMockAs(String path, Class<T> c) throws IOException {
    String contents = new String(Files.readAllBytes(Paths.get(RESOURCES_PATH, path)));
    return new JsonObject(contents).mapTo(c);
  }
}
