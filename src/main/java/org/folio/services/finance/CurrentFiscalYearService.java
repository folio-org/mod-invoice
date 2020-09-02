package org.folio.services.finance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.FiscalYearCollection;
import org.folio.rest.core.models.RequestContext;

public class CurrentFiscalYearService {

  public static final String SEARCH_CURRENT_FISCAL_YEAR_QUERY = "series==\"%s\" AND periodEnd>=%s sortBy periodStart";

  private final FiscalYearService fiscalYearService;
  private final LedgerService ledgerService;

  public CurrentFiscalYearService(FiscalYearService fiscalYearService, LedgerService ledgerService) {
    this.fiscalYearService = fiscalYearService;
    this.ledgerService = ledgerService;
  }

  public CompletableFuture<FiscalYear> getCurrentFiscalYear(String ledgerId, RequestContext requestContext) {
    return getFirstTwoFiscalYears(ledgerId, requestContext)
      .thenApply(firstTwoFiscalYears -> {
        if(CollectionUtils.isNotEmpty(firstTwoFiscalYears)) {
          if(firstTwoFiscalYears.size() > 1 && isOverlapped(firstTwoFiscalYears.get(0), firstTwoFiscalYears.get(1))) {
            return firstTwoFiscalYears.get(1);
          } else {
            return firstTwoFiscalYears.get(0);
          }
        } else {
          return null;
        }
      });
  }

  private CompletableFuture<List<FiscalYear>> getFirstTwoFiscalYears(String ledgerId, RequestContext requestContext) {
    return ledgerService.retrieveLedgerById(ledgerId, requestContext)
      .thenCompose(ledger -> fiscalYearService.getFiscalYear(ledger.getFiscalYearOneId(), requestContext))
      .thenApply(this::buildCurrentFYQuery)
      .thenCompose(query -> fiscalYearService.getFiscalYears(2, 0, query, requestContext))
      .thenApply(FiscalYearCollection::getFiscalYears);
  }

  private boolean isOverlapped(FiscalYear firstYear, FiscalYear secondYear) {
    Date now = new Date();
    return firstYear.getPeriodStart().before(now) && firstYear.getPeriodEnd().after(now)
      && secondYear.getPeriodStart().before(now) && secondYear.getPeriodEnd().after(now)
      && firstYear.getPeriodEnd().after(secondYear.getPeriodStart());
  }

  private String buildCurrentFYQuery(FiscalYear fiscalYearOne) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
    return String.format(SEARCH_CURRENT_FISCAL_YEAR_QUERY, fiscalYearOne.getSeries(), now);
  }
}
