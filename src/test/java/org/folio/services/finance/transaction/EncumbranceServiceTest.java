package org.folio.services.finance.transaction;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@DisplayName("EncumbranceService should :")
@ExtendWith(VertxExtension.class)
public class EncumbranceServiceTest {

  @Mock
  RequestContext requestContext;

  @Mock
  RestClient restClient;

  private EncumbranceService encumbranceService;
  private AutoCloseable mockitoMocks;


  @BeforeEach
  void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    BaseTransactionService transactionService = new BaseTransactionService(restClient);
    encumbranceService = new EncumbranceService(transactionService);
  }

  @AfterEach
  void resetMocks() throws Exception {
    mockitoMocks.close();
  }

  @Test
  @DisplayName("update encumbrance links if needed when an invoice line is created")
  void shouldUpdateEncumbranceLinksWhenAnInvoiceLineIsCreated(VertxTestContext vertxTestContext) {
    String currentFiscalYearId = UUID.randomUUID().toString();
    String pastFiscalYearId = UUID.randomUUID().toString();
    String poLineId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    Invoice invoice = new Invoice()
      .withFiscalYearId(pastFiscalYearId);
    Transaction currentFYEncumbrance = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(currentFiscalYearId)
      .withFromFundId(fundId)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId));
    Transaction pastFYEncumbrance = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(pastFiscalYearId)
      .withFromFundId(fundId)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId));
    FundDistribution fd = new FundDistribution()
      .withFundId(fundId)
      .withEncumbrance(currentFYEncumbrance.getId());
    InvoiceLine line = new InvoiceLine()
      .withPoLineId(poLineId)
      .withFundDistributions(List.of(fd));
    List<InvoiceWorkflowDataHolder> holders = List.of(new InvoiceWorkflowDataHolder()
      .withInvoiceLine(line)
      .withFundDistribution(fd)
      .withEncumbrance(currentFYEncumbrance));
    TransactionCollection transactionCollection = new TransactionCollection();
    transactionCollection.setTotalRecords(1);
    transactionCollection.setTransactions(List.of(pastFYEncumbrance));

    ArgumentCaptor<RequestEntry> requestEntryCaptor = ArgumentCaptor.forClass(RequestEntry.class);
    when(restClient.get(requestEntryCaptor.capture(), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(transactionCollection));

    Future<List<InvoiceWorkflowDataHolder>> future = encumbranceService.updateEncumbranceLinksForFiscalYear(invoice,
      holders, requestContext);

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        assertEquals(pastFYEncumbrance, holders.get(0).getEncumbrance());
        assertEquals(pastFYEncumbrance.getId(), holders.get(0).getFundDistribution().getEncumbrance());
        assertEquals(pastFYEncumbrance.getId(), holders.get(0).getInvoiceLine().getFundDistributions().get(0).getEncumbrance());

        verify(restClient, times(1))
          .get(any(RequestEntry.class), any(), eq(requestContext));

        List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
        assertEquals("/finance/transactions", requestEntries.get(0).getBaseEndpoint());
        String expectedQuery = String.format(
          "transactionType==Encumbrance AND fiscalYearId==%s AND encumbrance.sourcePoLineId==(%s)", pastFiscalYearId,
          poLineId);
        assertEquals(encodeQuery(expectedQuery), requestEntries.get(0).getQueryParams().get("query"));

        vertxTestContext.completeNow();
      });
  }

  @Test
  @DisplayName("update encumbrance links when invoice fiscal year is changed")
  void updateEncumbranceLinksWhenInvoiceFiscalYearIsChanged(VertxTestContext vertxTestContext) {
    String currentFiscalYearId = UUID.randomUUID().toString();
    String pastFiscalYearId = UUID.randomUUID().toString();
    String poLineId1 = UUID.randomUUID().toString();
    String poLineId2 = UUID.randomUUID().toString();
    String poLineId3 = UUID.randomUUID().toString();
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String fundId3 = UUID.randomUUID().toString();
    String fundId4 = UUID.randomUUID().toString();
    Invoice newInvoice = new Invoice()
      .withFiscalYearId(currentFiscalYearId);
    Transaction currentFYEncumbrance1 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(currentFiscalYearId)
      .withFromFundId(fundId1)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId1));
    Transaction currentFYEncumbrance2 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(currentFiscalYearId)
      .withFromFundId(fundId2)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId2));
    Transaction currentFYEncumbrance3 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(currentFiscalYearId)
      .withFromFundId(fundId3)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId3));
    Transaction pastFYEncumbrance1 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(pastFiscalYearId)
      .withFromFundId(fundId1)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId1));
    Transaction pastFYEncumbrance2 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(pastFiscalYearId)
      .withFromFundId(fundId2)
      .withEncumbrance(new Encumbrance().withSourcePoLineId(poLineId2));
    FundDistribution fd1 = new FundDistribution()
      .withFundId(fundId1)
      .withEncumbrance(currentFYEncumbrance1.getId());
    FundDistribution fd2 = new FundDistribution()
      .withFundId(fundId2)
      .withEncumbrance(currentFYEncumbrance2.getId());
    FundDistribution fd3 = new FundDistribution()
      .withFundId(fundId3)
      .withEncumbrance(currentFYEncumbrance3.getId());
    FundDistribution fd4 = new FundDistribution()
      .withFundId(fundId4);
    InvoiceLine line1 = new InvoiceLine()
      .withPoLineId(poLineId1)
      .withFundDistributions(List.of(fd1));
    InvoiceLine line2 = new InvoiceLine()
      .withPoLineId(poLineId2)
      .withFundDistributions(List.of(fd2));
    InvoiceLine line3 = new InvoiceLine()
      .withPoLineId(poLineId3)
      .withFundDistributions(List.of(fd3));
    InvoiceLine line4 = new InvoiceLine()
      .withFundDistributions(List.of(fd4));
    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
      .withInvoice(newInvoice)
      .withInvoiceLine(line1)
      .withFundDistribution(fd1)
      .withEncumbrance(currentFYEncumbrance1);
    InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
      .withInvoice(newInvoice)
      .withInvoiceLine(line2)
      .withFundDistribution(fd2)
      .withEncumbrance(currentFYEncumbrance2);
    InvoiceWorkflowDataHolder holder3 = new InvoiceWorkflowDataHolder()
      .withInvoice(newInvoice)
      .withInvoiceLine(line3)
      .withFundDistribution(fd3)
      .withEncumbrance(currentFYEncumbrance3);
    InvoiceWorkflowDataHolder holder4 = new InvoiceWorkflowDataHolder()
      .withInvoice(newInvoice)
      .withInvoiceLine(line4)
      .withFundDistribution(fd4);
    List<InvoiceWorkflowDataHolder> holders = List.of(holder1, holder2, holder3, holder4);
    TransactionCollection transactionCollection = new TransactionCollection();
    transactionCollection.setTotalRecords(2);
    transactionCollection.setTransactions(List.of(pastFYEncumbrance1, pastFYEncumbrance2));

    ArgumentCaptor<RequestEntry> requestEntryCaptor = ArgumentCaptor.forClass(RequestEntry.class);
    when(restClient.get(requestEntryCaptor.capture(), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(transactionCollection));

    Future<List<InvoiceLine>> future = encumbranceService.updateInvoiceLinesEncumbranceLinks(holders,
      pastFiscalYearId, requestContext);

    vertxTestContext.assertComplete(future)
      .onComplete(ar -> {
        List<InvoiceLine> updatedLines = ar.result();
        assertThat(updatedLines, hasSize(3));

        assertEquals(pastFYEncumbrance1.getId(), updatedLines.get(0).getFundDistributions().get(0).getEncumbrance());
        assertEquals(pastFYEncumbrance2.getId(), updatedLines.get(1).getFundDistributions().get(0).getEncumbrance());
        assertNull(updatedLines.get(2).getFundDistributions().get(0).getEncumbrance());

        verify(restClient, times(1))
          .get(any(RequestEntry.class), any(), eq(requestContext));

        List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
        assertEquals("/finance/transactions", requestEntries.get(0).getBaseEndpoint());
        String expectedQuery = String.format(
          "transactionType==Encumbrance AND fiscalYearId==%s AND encumbrance.sourcePoLineId==(%s or %s or %s)",
          pastFiscalYearId, poLineId1, poLineId2, poLineId3);
        assertEquals(encodeQuery(expectedQuery), requestEntries.get(0).getQueryParams().get("query"));

        vertxTestContext.completeNow();
      });

  }
}
