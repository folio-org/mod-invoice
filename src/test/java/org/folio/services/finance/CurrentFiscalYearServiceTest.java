package org.folio.services.finance;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.FiscalYearCollection;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.core.models.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CurrentFiscalYearServiceTest {

  @InjectMocks
  private CurrentFiscalYearService currentFiscalYearService;
  @Mock
  private LedgerService ledgerService;
  @Mock
  private FiscalYearService fiscalYearService;
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

    Ledger ledger = new Ledger().withId(ledgerId).withFiscalYearOneId(fiscalYearId);
    FiscalYear fiscalYear = new FiscalYear().withId(fiscalYearId).withPeriodStart(startDate).withPeriodEnd(endDate);
    FiscalYearCollection fiscalYearCollection = new FiscalYearCollection().withFiscalYears(Collections.singletonList(fiscalYear));

    doReturn(completedFuture(ledger)).when(ledgerService).retrieveLedgerById(ledgerId, requestContextMock);
    doReturn(completedFuture(fiscalYear)).when(fiscalYearService).getFiscalYear(fiscalYearId, requestContextMock);
    doReturn(completedFuture(fiscalYearCollection)).when(fiscalYearService).getFiscalYears(eq(2), eq(0), any(String.class), any(RequestContext.class));
    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock).join();
    //Then
    assertEquals(fiscalYearId, currentFiscalYear.getId());
  }

  @Test
  void shouldReturnCurrentFiscalYearIfLedgerAnd2FiscalYearsExist() {
    //Given
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId1 = UUID.randomUUID().toString();
    String fiscalYearId2 = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate1 = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    Date endDate2 = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();

    Ledger ledger = new Ledger().withId(ledgerId).withFiscalYearOneId(fiscalYearId1);
    FiscalYear fiscalYear1 = new FiscalYear().withId(fiscalYearId1).withPeriodStart(startDate).withPeriodEnd(endDate1);
    FiscalYear fiscalYear2 = new FiscalYear().withId(fiscalYearId2).withPeriodStart(startDate).withPeriodEnd(endDate2);
    FiscalYearCollection fiscalYearCollection = new FiscalYearCollection().withFiscalYears(Stream.of(fiscalYear1, fiscalYear2).collect(Collectors.toList()));

    doReturn(completedFuture(ledger)).when(ledgerService).retrieveLedgerById(ledgerId, requestContextMock);
    doReturn(completedFuture(fiscalYear1)).when(fiscalYearService).getFiscalYear(fiscalYearId1, requestContextMock);
    doReturn(completedFuture(fiscalYearCollection)).when(fiscalYearService).getFiscalYears(eq(2), eq(0), any(String.class), any(RequestContext.class));
    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock).join();
    //Then
    assertEquals(fiscalYearId1, currentFiscalYear.getId());
  }

  @Test
  void shouldReturnNullIfFiscalYearsIsNotExist() {
    //Given
    String ledgerId = UUID.randomUUID().toString();
    String fiscalYearId1 = UUID.randomUUID().toString();
    Date startDate = new GregorianCalendar(2019, Calendar.JANUARY, 1).getTime();
    LocalDate localDate =  LocalDate.now().plusDays(1);
    Date endDate1 = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

    Ledger ledger = new Ledger().withId(ledgerId).withFiscalYearOneId(fiscalYearId1);
    FiscalYear fiscalYear1 = new FiscalYear().withId(fiscalYearId1).withPeriodStart(startDate).withPeriodEnd(endDate1);
    FiscalYearCollection fiscalYearCollection = new FiscalYearCollection().withFiscalYears(Collections.EMPTY_LIST);

    doReturn(completedFuture(ledger)).when(ledgerService).retrieveLedgerById(ledgerId, requestContextMock);
    doReturn(completedFuture(fiscalYear1)).when(fiscalYearService).getFiscalYear(fiscalYearId1, requestContextMock);
    doReturn(completedFuture(fiscalYearCollection)).when(fiscalYearService).getFiscalYears(eq(2), eq(0), any(String.class), any(RequestContext.class));
    //When
    FiscalYear currentFiscalYear = currentFiscalYearService.getCurrentFiscalYear(ledgerId, requestContextMock).join();
    //Then
    assertNull(currentFiscalYear);
  }
}
