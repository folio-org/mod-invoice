package org.folio.builders;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.exchange.CacheableExchangeRateService;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.ApiTestSuite.mockPort;
import static org.folio.TestMockDataConstants.MOCK_PENDING_PAYMENTS_LIST;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_TOKEN;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_USER_ID;
import static org.folio.rest.impl.ApiTestBase.getMockData;
import static org.folio.services.finance.transaction.BaseTransactionServiceTest.X_OKAPI_TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public class InvoiceWorkFlowDataHolderBuilderTest {

  private InvoiceWorkflowDataHolderBuilder invoiceWorkflowDataHolderBuilder;

  @Mock
  private RestClient restClient;
  @Mock
  private Context ctxMock;

  private Map<String, String> okapiHeaders;
  private List<String> transactionIds;
  private AutoCloseable openMocks;

  @BeforeEach
  public void initMocks() {
    openMocks = MockitoAnnotations.openMocks(this);
    transactionIds = new ArrayList<>();
    transactionIds.add("c5732efb-9536-4a49-a22e-1ec6ca8a7922");
    transactionIds.add("c6732efb-9536-4a49-a22e-1ec6ca8a7922");
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    FiscalYearService fiscalYearService = new FiscalYearService(restClient);
    FundService fundService = new FundService(restClient);
    LedgerService ledgerService = new LedgerService(restClient);
    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    BudgetService budgetService = new BudgetService(restClient);
    ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(restClient);
    CacheableExchangeRateService cacheableExchangeRateService = new CacheableExchangeRateService(restClient, 1L);
    invoiceWorkflowDataHolderBuilder = new InvoiceWorkflowDataHolderBuilder(
      fiscalYearService, fundService, ledgerService, baseTransactionService,
      budgetService, expenseClassRetrieveService, cacheableExchangeRateService);
  }

  @AfterEach
  public void closeMocks() throws Exception {
    if (openMocks != null) {
      openMocks.close();
    }
  }

  @Test
  void updatePendingPaymentsWithExchangeRateAfterInvoiceApproval() throws IOException {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String invoiceLineId = UUID.randomUUID().toString();

    FiscalYear fiscalYear = new FiscalYear()
      .withId(fiscalYearId);

    FundDistribution invoiceFundDistribution = new FundDistribution()
      .withFundId(fundId);

    Adjustment adjustment = new Adjustment()
      .withFundDistributions(Collections.singletonList(invoiceFundDistribution));

    Invoice invoice = new Invoice()
      .withAdjustments(Collections.singletonList(adjustment))
      .withId(invoiceId)
      .withSubTotal(50d)
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

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();

    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
      .withInvoice(invoice)
      .withInvoiceLine(invoiceLine)
      .withFundDistribution(invoiceLineFundDistribution)
      .withFiscalYear(fiscalYear);

    InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
      .withInvoice(invoice)
      .withAdjustment(adjustment)
      .withFundDistribution(invoiceFundDistribution)
      .withFiscalYear(fiscalYear);

    holders.add(holder1);
    holders.add(holder2);

    JsonObject pendingPaymentList = new JsonObject(getMockData(MOCK_PENDING_PAYMENTS_LIST));

    List<Transaction> encumbrances = pendingPaymentList.getJsonArray("transactions").stream()
      .map(obj -> ((JsonObject) obj).mapTo(Transaction.class)).collect(toList());
    TransactionCollection trCollection = new TransactionCollection().withTransactions(encumbrances);
    RequestContext requestContext = new RequestContext(ctxMock, okapiHeaders);
    doReturn(succeededFuture(trCollection)).when(restClient).get(any(RequestEntry.class), eq(TransactionCollection.class), any(RequestContext.class));

    List<InvoiceWorkflowDataHolder> listFuture = invoiceWorkflowDataHolderBuilder.withExistingTransactions(holders, requestContext).result();
    List<String> holderTransactionList = listFuture.stream().map(holder -> holder.getExistingTransaction().getId()).collect(toList());
    Assertions.assertEquals(holderTransactionList, transactionIds);
  }
}
