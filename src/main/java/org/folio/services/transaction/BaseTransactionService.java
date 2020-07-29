package org.folio.services.transaction;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

import one.util.streamex.StreamEx;

public class BaseTransactionService {
  private final RestClient trFinanceRestClient;

  public BaseTransactionService(RestClient trFinanceRestClient) {
    this.trFinanceRestClient = trFinanceRestClient;
  }

  public CompletableFuture<TransactionCollection> getTransactions(String query, int offset, int limit, RequestContext requestContext) {
    return trFinanceRestClient.get(query, offset, limit, requestContext, TransactionCollection.class);
  }

  public CompletableFuture<List<Transaction>> getTransactions(List<String> expenseClassIds, RequestContext requestContext) {
    List<CompletableFuture<TransactionCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(expenseClassIds, MAX_IDS_FOR_GET_RQ)
      .map(ids ->  getTransactionsChunk(expenseClassIds, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
      .thenApply(expenseClassCollections ->
        expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
      );
  }

  private CompletableFuture<TransactionCollection> getTransactionsChunk(List<String> expenseClassIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(expenseClassIds));
    return this.getTransactions(query, 0, expenseClassIds.size(), requestContext);
  }

}
