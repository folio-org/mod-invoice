package org.folio.services.finance;

import static java.util.stream.Collectors.toList;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.LEDGERS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.LedgerCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

public class LedgerService {

  private static final String LEDGERS_ENDPOINT = resourcesPath(LEDGERS);

  private final RestClient restClient;

  public LedgerService(RestClient restClient) {
    this.restClient = restClient;
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
    RequestEntry requestEntry = new RequestEntry(LEDGERS_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(MAX_IDS_FOR_GET_RQ);
    return restClient.get(requestEntry, requestContext, LedgerCollection.class)
      .thenApply(LedgerCollection::getLedgers);
  }
}
