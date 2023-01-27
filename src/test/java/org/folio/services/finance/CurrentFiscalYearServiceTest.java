package org.folio.services.finance;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.UUID;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Error;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
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
  void shouldReturnCurrentFiscalYear(VertxTestContext vertxTestContext) {
    //Given
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    FiscalYear fiscalYear = new FiscalYear().withId(fiscalYearId).withPeriodStart(startDate).withPeriodEnd(endDate);

    doReturn(succeededFuture(fiscalYear)).when(currentFiscalYearRestClient).get(any(RequestEntry.class), any(), any());

    //When
    var future = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock);
    vertxTestContext.assertComplete(future)
      .onComplete(result -> {
        Assertions.assertEquals(fiscalYearId, result.result().getId());

        vertxTestContext.completeNow();
      });
    //Then
  }

  @Test
  void shouldReturnCurrentFiscalYearByFundId(VertxTestContext vertxTestContext) {
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

    doReturn(succeededFuture(fund)).when(fundService).getFundById(fundId, requestContextMock);
    doReturn(succeededFuture(fiscalYear)).when(currentFiscalYearRestClient).get(any(RequestEntry.class), any(), any());
    //When
    var future = currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContextMock);
    //Then
    vertxTestContext.assertComplete(future)
      .onSuccess(currentFiscalYear -> {
        Assertions.assertEquals(fiscalYearId, currentFiscalYear.getId());
        vertxTestContext.completeNow();
      })
      .onFailure(vertxTestContext::failNow);

  }

  @Test
  void shouldReturnErrorResponseIfFundDoesNotExist(VertxTestContext vertxTestContext) {
    //Given

    String fundId = UUID.randomUUID().toString();

    doReturn(Future.failedFuture(new HttpException(404, FUNDS_NOT_FOUND)))
      .when(fundService).getFundById(fundId, requestContextMock);

    Future<FiscalYear> future = currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContextMock);
    //When
    vertxTestContext.assertFailure(future)
      .onComplete(result -> {
        assertThat(result.cause(), instanceOf(HttpException.class));
        HttpException httpException = (HttpException) result.cause();
        Assertions.assertEquals(404, httpException.getCode());
        Error error = httpException.getErrors()
          .getErrors()
          .get(0);
        Assertions.assertEquals(FUNDS_NOT_FOUND.toError().getMessage(), error.getMessage());
        Assertions.assertEquals(FUNDS_NOT_FOUND.toError().getCode(), error.getCode());
        vertxTestContext.completeNow();
      });
  }
}
