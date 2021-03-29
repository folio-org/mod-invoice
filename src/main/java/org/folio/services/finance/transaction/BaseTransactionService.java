package org.folio.services.finance.transaction;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import one.util.streamex.StreamEx;

public class BaseTransactionService {

  private static final String TRANSACTIONS_ENDPOINT = resourcesPath(FINANCE_TRANSACTIONS);

  private static final Map<Transaction.TransactionType, String> TRANSACTION_ENDPOINTS = Collections.unmodifiableMap(Map.of(
      Transaction.TransactionType.PAYMENT, "/finance/payments",
      Transaction.TransactionType.CREDIT, "/finance/credits",
      Transaction.TransactionType.PENDING_PAYMENT, "/finance/pending-payments"
  ));

  private final RestClient restClient;

  public BaseTransactionService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<TransactionCollection> getTransactions(String query, int offset, int limit, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(TRANSACTIONS_ENDPOINT)
        .withQuery(query)
        .withOffset(offset)
        .withLimit(limit);
    return restClient.get(requestEntry, requestContext, TransactionCollection.class);
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

  public CompletableFuture<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    return Optional.ofNullable(getByIdEndpoint(transaction))
        .map(RequestEntry::new)
        .map(requestEntry -> restClient.post(requestEntry, transaction, requestContext, Transaction.class))
        .orElseGet(this::unsupportedOperation);
  }

  public CompletableFuture<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    return Optional.ofNullable(getByIdEndpoint(transaction))
        .map(RequestEntry::new)
        .map(requestEntry -> requestEntry.withId(transaction.getId()))
        .map(requestEntry -> restClient.put(requestEntry, transaction, requestContext))
        .orElseGet(this::unsupportedOperation);
  }

  public <T> CompletableFuture<T> unsupportedOperation() {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(new UnsupportedOperationException());
    return future;
  }


  public String getEndpoint(Transaction transaction) {
    return TRANSACTION_ENDPOINTS.get(transaction.getTransactionType());
  }

  public String getByIdEndpoint(Transaction transaction) {
    return TRANSACTION_ENDPOINTS.get(transaction.getTransactionType()) + "/{id}";
  }

}
