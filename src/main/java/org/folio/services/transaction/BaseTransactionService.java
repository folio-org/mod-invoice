package org.folio.services.transaction;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.CollectionUtils;
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

  public CompletableFuture<List<Transaction>> getTransactions(List<String> transactionIds, RequestContext requestContext) {
    if (!CollectionUtils.isEmpty(transactionIds)) {
      List<CompletableFuture<TransactionCollection>> expenseClassesFutureList = StreamEx
        .ofSubLists(transactionIds, MAX_IDS_FOR_GET_RQ)
        .map(ids -> getTransactionsChunk(transactionIds, requestContext))
        .collect(toList());

      return collectResultsOnSuccess(expenseClassesFutureList)
        .thenApply(expenseClassCollections ->
          expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
        );
    }
    return CompletableFuture.completedFuture(Collections.emptyList());
  }

  private CompletableFuture<TransactionCollection> getTransactionsChunk(List<String> transactionIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(transactionIds));
    return this.getTransactions(query, 0, transactionIds.size(), requestContext);
  }

}
