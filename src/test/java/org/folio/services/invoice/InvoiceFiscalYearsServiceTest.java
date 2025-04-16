package org.folio.services.invoice;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.BudgetCollection;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.services.exchange.CacheableExchangeRateService;
import org.folio.services.finance.FundService;
import org.folio.services.finance.LedgerService;
import org.folio.services.finance.budget.BudgetService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.finance.transaction.BaseTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.verification.Times;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.folio.invoices.utils.ErrorCodes.COULD_NOT_FIND_VALID_FISCAL_YEAR;
import static org.folio.invoices.utils.ErrorCodes.MORE_THAN_ONE_FISCAL_YEAR_SERIES;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
@DisplayName("InvoiceFiscalYearsService should :")
public class InvoiceFiscalYearsServiceTest {

  private AutoCloseable closeable;
  private InvoiceFiscalYearsService invoiceFiscalYearsService;

  @Mock
  private RestClient restClient;
  @Mock
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    closeable = MockitoAnnotations.openMocks(this);
    FiscalYearService fiscalYearService = new FiscalYearService(restClient);
    FundService fundService = new FundService(restClient);
    LedgerService ledgerService = new LedgerService(restClient);
    BaseTransactionService baseTransactionService = new BaseTransactionService(restClient);
    BudgetService budgetService = new BudgetService(restClient);
    ExpenseClassRetrieveService expenseClassRetrieveService = new ExpenseClassRetrieveService(restClient);
    CacheableExchangeRateService cacheableExchangeRateService = new CacheableExchangeRateService(restClient);
    InvoiceWorkflowDataHolderBuilder invoiceWorkflowDataHolderBuilder = new InvoiceWorkflowDataHolderBuilder(
      fiscalYearService, fundService, ledgerService, baseTransactionService,
      budgetService, expenseClassRetrieveService, cacheableExchangeRateService);
    invoiceFiscalYearsService = new InvoiceFiscalYearsService(invoiceWorkflowDataHolderBuilder, budgetService, fiscalYearService);
  }

  @AfterEach
  void closeService() throws Exception {
    closeable.close();
  }

  @Test
  @DisplayName("return only fiscal years with budgets for all the funds")
  void shouldReturnOnlyFiscalYearsWithBudgetsForAllTheFunds(VertxTestContext vertxTestContext) {
    FiscalYear fy1 = new FiscalYear()
      .withId(UUID.randomUUID().toString())
      .withSeries("FY");
    FiscalYear fy2 = new FiscalYear()
      .withId(UUID.randomUUID().toString())
      .withSeries("FY");
    FiscalYear fy3 = new FiscalYear()
      .withId(UUID.randomUUID().toString())
      .withSeries("FY");

    Fund fund1 = new Fund()
      .withId(UUID.randomUUID().toString());
    Fund fund2 = new Fund()
      .withId(UUID.randomUUID().toString());

    Invoice invoice = new Invoice()
      .withId(UUID.randomUUID().toString())
      .withAcqUnitIds(emptyList());

    FundDistribution fd1 = new FundDistribution()
      .withFundId(fund1.getId());
    InvoiceLine invoiceLine1 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd1));
    FundDistribution fd2 = new FundDistribution()
      .withFundId(fund2.getId());
    InvoiceLine invoiceLine2 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd2));
    List<InvoiceLine> invoiceLines = List.of(invoiceLine1, invoiceLine2);

    // fy1 has budgets for funds 1 and 2
    // fy2 has only a budget for fund 1
    // fy3 has no budget
    Budget fy1Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy1.getId())
      .withFundId(fund1.getId());
    Budget fy1Budget2 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy1.getId())
      .withFundId(fund2.getId());
    Budget fy2Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy2.getId())
      .withFundId(fund1.getId());
    Budget fy3Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy3.getId())
      .withFundId(fund1.getId());
    BudgetCollection budgetCollection = new BudgetCollection()
      .withBudgets(List.of(fy1Budget1, fy1Budget2, fy2Budget1, fy3Budget1))
      .withTotalRecords(3);

    FiscalYearCollection fiscalYearCollection = new FiscalYearCollection()
      .withFiscalYears(List.of(fy1))
      .withTotalRecords(1);

    // The query that should be used to get the budgets, using the two fund ids
    String budgetQueryIds = convertIdsToCqlQuery(List.of(fund1.getId(), fund2.getId()), "fundId", true);
    String budgetQueryActive = "budgetStatus==Active";
    String budgetQuery = String.format("%s AND %s", budgetQueryIds, budgetQueryActive);

    // The query that should be used to get the fiscal years, using only fy1
    String queryIds = convertIdsToCqlQuery(List.of(fy1.getId()));
    LocalDate now = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate();
    String queryDate = "periodStart<=\"" + now + "\" sortby periodStart/sort.descending";
    String fyQuery = String.format("%s AND %s", queryIds, queryDate);

    doReturn(succeededFuture(budgetCollection))
      .when(restClient).get(any(RequestEntry.class), eq(BudgetCollection.class), eq(requestContext));
    doReturn(succeededFuture(fiscalYearCollection))
      .when(restClient).get(any(RequestEntry.class), eq(FiscalYearCollection.class), eq(requestContext));

    Future<FiscalYearCollection> future = invoiceFiscalYearsService.getFiscalYearsByInvoiceAndLines(invoice,
      invoiceLines, requestContext);

    vertxTestContext.assertComplete(future)
      .onSuccess(fyCollection -> {
        assertEquals(fyCollection, fiscalYearCollection);
        ArgumentCaptor<RequestEntry> requestEntryCaptor = ArgumentCaptor.forClass(RequestEntry.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Class<Object>> responseTypeCaptor = ArgumentCaptor.forClass(Class.class);
        verify(restClient, new Times(2))
          .get(requestEntryCaptor.capture(), responseTypeCaptor.capture(), eq(requestContext));
        List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
        assertEquals(requestEntries.get(0).getQueryParams().get("query"), encodeQuery(budgetQuery));
        assertEquals(requestEntries.get(1).getQueryParams().get("query"), encodeQuery(fyQuery));
        List<Class<Object>> responseTypes = responseTypeCaptor.getAllValues();
        assertEquals(responseTypes, List.of(BudgetCollection.class, FiscalYearCollection.class));
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);
  }

  @Test
  @DisplayName("fail when no valid fiscal year was found")
  void shouldFailWhenNoValidFiscalYearWasFound(VertxTestContext vertxTestContext) {
    FiscalYear fy1 = new FiscalYear()
      .withId(UUID.randomUUID().toString())
      .withSeries("FY");

    Fund fund1 = new Fund()
      .withId(UUID.randomUUID().toString());
    Fund fund2 = new Fund()
      .withId(UUID.randomUUID().toString());

    Invoice invoice = new Invoice()
      .withId(UUID.randomUUID().toString())
      .withAcqUnitIds(emptyList());

    FundDistribution fd1 = new FundDistribution()
      .withFundId(fund1.getId());
    InvoiceLine invoiceLine1 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd1));
    FundDistribution fd2 = new FundDistribution()
      .withFundId(fund2.getId());
    InvoiceLine invoiceLine2 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd2));
    List<InvoiceLine> invoiceLines = List.of(invoiceLine1, invoiceLine2);

    // fy1 has budgets for fund 1
    // fy2 has no budget
    Budget fy1Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy1.getId())
      .withFundId(fund1.getId());
    BudgetCollection budgetCollection = new BudgetCollection()
      .withBudgets(List.of(fy1Budget1))
      .withTotalRecords(1);

    doReturn(succeededFuture(budgetCollection))
      .when(restClient).get(any(RequestEntry.class), eq(BudgetCollection.class), eq(requestContext));

    Future<FiscalYearCollection> future = invoiceFiscalYearsService.getFiscalYearsByInvoiceAndLines(invoice,
      invoiceLines, requestContext);

    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        assertThat(result.cause(), instanceOf(HttpException.class));
        HttpException exception = (HttpException) result.cause();
        assertEquals(422, exception.getCode());

        Errors errors = exception.getErrors();
        Error error = errors.getErrors().getFirst();
        assertEquals(COULD_NOT_FIND_VALID_FISCAL_YEAR.getCode(), error.getCode());
        assertEquals(error.getParameters().get(0).getValue(), invoice.getId());
        assertEquals(error.getParameters().get(1).getValue(), List.of(fund1.getId(), fund2.getId()).toString());
        vertxTestContext.completeNow();
      });
  }

  @Test
  @DisplayName("fail when only a future fiscal year matches")
  void shouldFailWhenOnlyAFutureFiscalYearMatches(VertxTestContext vertxTestContext) {
    FiscalYear fy1 = new FiscalYear()
      .withId(UUID.randomUUID().toString())
      .withSeries("FY");

    Fund fund1 = new Fund()
      .withId(UUID.randomUUID().toString());
    Fund fund2 = new Fund()
      .withId(UUID.randomUUID().toString());

    Invoice invoice = new Invoice()
      .withId(UUID.randomUUID().toString())
      .withAcqUnitIds(emptyList());

    FundDistribution fd1 = new FundDistribution()
      .withFundId(fund1.getId());
    InvoiceLine invoiceLine1 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd1));
    FundDistribution fd2 = new FundDistribution()
      .withFundId(fund2.getId());
    InvoiceLine invoiceLine2 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd2));
    List<InvoiceLine> invoiceLines = List.of(invoiceLine1, invoiceLine2);

    // fy1 has budgets for funds 1 and 2
    // fy2 has no budget
    Budget fy1Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy1.getId())
      .withFundId(fund1.getId());
    Budget fy1Budget2 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy1.getId());
    BudgetCollection budgetCollection = new BudgetCollection()
      .withBudgets(List.of(fy1Budget1, fy1Budget2))
      .withTotalRecords(2);

    FiscalYearCollection fiscalYearCollection = new FiscalYearCollection()
      .withFiscalYears(emptyList())
      .withTotalRecords(0);

    doReturn(succeededFuture(budgetCollection))
      .when(restClient).get(any(RequestEntry.class), eq(BudgetCollection.class), eq(requestContext));
    doReturn(succeededFuture(fiscalYearCollection))
      .when(restClient).get(any(RequestEntry.class), eq(FiscalYearCollection.class), eq(requestContext));

    Future<FiscalYearCollection> future = invoiceFiscalYearsService.getFiscalYearsByInvoiceAndLines(invoice,
      invoiceLines, requestContext);

    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        assertThat(result.cause(), instanceOf(HttpException.class));
        HttpException exception = (HttpException) result.cause();
        assertEquals(422, exception.getCode());

        Errors errors = exception.getErrors();
        Error error = errors.getErrors().getFirst();
        assertEquals(COULD_NOT_FIND_VALID_FISCAL_YEAR.getCode(), error.getCode());
        assertEquals(error.getParameters().get(0).getValue(), invoice.getId());
        assertEquals(error.getParameters().get(1).getValue(), List.of(fund1.getId(), fund2.getId()).toString());
        vertxTestContext.completeNow();
      });
  }

  @Test
  @DisplayName("fail with multiple fiscal year series")
  void shouldFailWithMultipleFiscalYearSeries(VertxTestContext vertxTestContext) {
    FiscalYear fy1 = new FiscalYear()
      .withSeries("FY")
      .withId(UUID.randomUUID().toString());
    FiscalYear fy2 = new FiscalYear()
      .withSeries("FYTEST")
      .withId(UUID.randomUUID().toString());

    Fund fund1 = new Fund()
      .withId(UUID.randomUUID().toString());

    Invoice invoice = new Invoice()
      .withId(UUID.randomUUID().toString())
      .withAcqUnitIds(emptyList());

    FundDistribution fd1 = new FundDistribution()
      .withFundId(fund1.getId());
    InvoiceLine invoiceLine1 = new InvoiceLine()
      .withId(UUID.randomUUID().toString())
      .withInvoiceId(invoice.getId())
      .withFundDistributions(List.of(fd1));
    List<InvoiceLine> invoiceLines = List.of(invoiceLine1);

    // fy1 has budgets for fund 1
    // fy2 has budget for fund 1
    Budget fy1Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy1.getId())
      .withFundId(fund1.getId());
    Budget fy2Budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fy2.getId());
    BudgetCollection budgetCollection = new BudgetCollection()
      .withBudgets(List.of(fy1Budget1, fy2Budget1))
      .withTotalRecords(2);

    FiscalYearCollection fiscalYearCollection = new FiscalYearCollection()
      .withFiscalYears(List.of(fy1, fy2))
      .withTotalRecords(2);

    doReturn(succeededFuture(budgetCollection))
      .when(restClient).get(any(RequestEntry.class), eq(BudgetCollection.class), eq(requestContext));
    doReturn(succeededFuture(fiscalYearCollection))
      .when(restClient).get(any(RequestEntry.class), eq(FiscalYearCollection.class), eq(requestContext));

    Future<FiscalYearCollection> future = invoiceFiscalYearsService.getFiscalYearsByInvoiceAndLines(invoice,
      invoiceLines, requestContext);

    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        assertThat(result.cause(), instanceOf(HttpException.class));
        HttpException exception = (HttpException) result.cause();
        assertEquals(422, exception.getCode());

        Errors errors = exception.getErrors();
        Error error = errors.getErrors().getFirst();
        assertEquals(MORE_THAN_ONE_FISCAL_YEAR_SERIES.getCode(), error.getCode());
        assertEquals(error.getParameters().get(0).getValue(), invoice.getId());
        assertEquals(error.getParameters().get(1).getValue(), List.of("FY", "FYTEST").toString());
        vertxTestContext.completeNow();
      });
  }
}
