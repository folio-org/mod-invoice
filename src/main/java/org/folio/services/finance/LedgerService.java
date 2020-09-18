package org.folio.services.finance;

import static java.util.stream.Collectors.toList;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.LedgerCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;


public class LedgerService {

  private final RestClient ledgerRestClient;

  public LedgerService(RestClient ledgerRestClient) {
    this.ledgerRestClient = ledgerRestClient;
  }

  public CompletableFuture<List<Ledger>> retrieveRestrictedLedgersByIds(List<String> ledgerIds, RequestContext requestContext) {

    return collectResultsOnSuccess(ofSubLists(ledgerIds, MAX_IDS_FOR_GET_RQ)
      .map(ids1 -> getRestrictedLedgersChunk(ids1, requestContext)).toList())
      .thenApply(lists -> lists.stream()
        .flatMap(Collection::stream)
        .collect(toList()));
  }

  public CompletableFuture<List<Ledger>> getRestrictedLedgersChunk(List<String> ids, RequestContext requestContext) {

    String query = convertIdsToCqlQuery(ids) + " AND restrictExpenditures==true";
    return ledgerRestClient.get(query,0, MAX_IDS_FOR_GET_RQ, requestContext, LedgerCollection.class)
      .thenApply(LedgerCollection::getLedgers);
  }
}
