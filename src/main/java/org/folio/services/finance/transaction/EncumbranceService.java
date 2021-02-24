package org.folio.services.finance.transaction;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;

import one.util.streamex.StreamEx;

public class EncumbranceService {

  private final BaseTransactionService transactionService;

  public EncumbranceService(BaseTransactionService transactionService) {
    this.transactionService = transactionService;
  }

  public CompletableFuture<List<Transaction>> getEncumbrancesByPoLineIds(List<String> poLineIds, RequestContext requestContext) {
    List<CompletableFuture<TransactionCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(this::buildEncumbranceChunckQueryByPoLineIds)
      .map(queryAndSize -> transactionService.getTransactions(queryAndSize.getLeft(), 0, queryAndSize.getRight(), requestContext))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
      .thenApply(expenseClassCollections ->
        expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
      );
  }

  private Pair<String, Integer> buildEncumbranceChunckQueryByPoLineIds(List<String> poLineIds) {
    String query = "query=transactionType==Encumbrance and " + convertIdsToCqlQuery(poLineIds, "encumbrance.sourcePoLineId", true);
    return Pair.of(query, poLineIds.size());
  }
}
