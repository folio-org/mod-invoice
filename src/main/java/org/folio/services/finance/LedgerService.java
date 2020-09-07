package org.folio.services.finance;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;


public class LedgerService {
  private final RestClient ledgerRestClient;

  public LedgerService(RestClient ledgerRestClient) {
    this.ledgerRestClient = ledgerRestClient;
  }

  public CompletableFuture<Ledger> retrieveLedgerById(String ledgerId, RequestContext requestContext) {
    return ledgerRestClient.getById(ledgerId, requestContext, Ledger.class);
  }
}
