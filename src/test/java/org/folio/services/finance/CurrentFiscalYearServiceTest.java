package org.folio.services.finance;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CurrentFiscalYearServiceTest {

  @InjectMocks
  private CurrentFiscalYearService currentFiscalYearService;
  @Mock
  private RestClient currentFiscalYearRestClient;
  @Mock
  private FundService fundService;
  @Mock
  private RequestContext requestContextMock;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldReturnCurrentFiscalYear() {
    //Given
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    FiscalYear fiscalYear = new FiscalYear().withId(fiscalYearId).withPeriodStart(startDate).withPeriodEnd(endDate);

    doReturn(completedFuture(fiscalYear)).when(currentFiscalYearRestClient).get(any(), any(), any());

    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock).join();
    //Then
    Assertions.assertEquals(fiscalYearId, currentFiscalYear.getId());
  }

  @Test
  void shouldReturnCurrentFiscalYearByFundId() {
    //Given
    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);


    FiscalYear fiscalYear = new FiscalYear().withId(fiscalYearId).withPeriodStart(startDate).withPeriodEnd(endDate);

    doReturn(completedFuture(fund)).when(fundService).getFundById(fundId, requestContextMock);
    doReturn(completedFuture(fiscalYear)).when(currentFiscalYearRestClient).get(any(), any(), any());
    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContextMock).join();
    //Then
    Assertions.assertEquals(fiscalYearId, currentFiscalYear.getId());

  }

  @Test
  void shouldReturnErrorResponseIfFundDoesNotExist() {
    //Given

    String fundId = UUID.randomUUID().toString();
    CompletableFuture<Fund> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new HttpException(404, FUNDS_NOT_FOUND));


    doReturn(failedFuture).when(fundService).getFundById(fundId, requestContextMock);

    CompletableFuture<FiscalYear> resultFuture = currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContextMock);
      //When
    ExecutionException e = assertThrows(ExecutionException.class, resultFuture::get);
    //Then
    assertThat(e.getCause(), instanceOf(HttpException.class));
    HttpException httpException = (HttpException) e.getCause();
    Assertions.assertEquals(404, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    Assertions.assertEquals(FUNDS_NOT_FOUND.toError().getMessage(), error.getMessage());
    Assertions.assertEquals(FUNDS_NOT_FOUND.toError().getCode(), error.getCode());
  }
}
