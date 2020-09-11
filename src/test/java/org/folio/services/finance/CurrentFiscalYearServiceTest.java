package org.folio.services.finance;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.CURRENT_FISCAL_YEAR_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
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
  private RequestContext requestContextMock;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  void shouldReturnCurrentFiscalYearIfLedgerAndFiscalYearsExist() {
    //Given
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    FiscalYear fiscalYear = new FiscalYear().withId(fiscalYearId).withPeriodStart(startDate).withPeriodEnd(endDate);

    doReturn(completedFuture(fiscalYear)).when(currentFiscalYearRestClient).getById(ledgerId, requestContextMock, FiscalYear.class);

    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock).join();
    //Then
    assertEquals(fiscalYearId, currentFiscalYear.getId());
  }

  @Test
  void shouldReturnCurrentFiscalYearIfLedgerAnd2FiscalYearsExist() {
    //Given
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());


    FiscalYear fiscalYear = new FiscalYear().withId(fiscalYearId).withPeriodStart(startDate).withPeriodEnd(endDate);


    doReturn(completedFuture(fiscalYear)).when(currentFiscalYearRestClient).getById(ledgerId, requestContextMock, FiscalYear.class);
    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock).join();
    //Then
    assertEquals(fiscalYearId, currentFiscalYear.getId());
  }

  @Test
  void shouldReturnNullIfFiscalYearsIsNotExist() {
    //Given
    String ledgerId = UUID.randomUUID().toString();

    CompletableFuture<FiscalYear> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new HttpException(404, NOT_FOUND.getReasonPhrase()));

    doReturn(failedFuture).when(currentFiscalYearRestClient).getById(ledgerId, requestContextMock, FiscalYear.class);
    CompletableFuture<FiscalYear> resultFuture = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock);
      //When
    ExecutionException e = assertThrows(ExecutionException.class, resultFuture::get);
    //Then
    assertThat(e.getCause(), instanceOf(HttpException.class));
    HttpException httpException = (HttpException) e.getCause();
    assertEquals(400, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    assertEquals(CURRENT_FISCAL_YEAR_NOT_FOUND.toError().getMessage(), error.getMessage());
    assertEquals(CURRENT_FISCAL_YEAR_NOT_FOUND.toError().getCode(), error.getCode());
    assertEquals(ledgerId, error.getParameters().get(0).getValue());
  }
}
