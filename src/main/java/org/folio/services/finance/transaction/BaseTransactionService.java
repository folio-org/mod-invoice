package org.folio.services.finance.transaction;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_RELEASE_ENCUMBRANCE;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertxconcurrent.Semaphore;
import one.util.streamex.StreamEx;

public class BaseTransactionService {

  private static final String TRANSACTIONS_ENDPOINT = resourcesPath(FINANCE_TRANSACTIONS);

  private static final Map<TransactionType, String> TRANSACTION_ENDPOINTS = Map.of(
    TransactionType.PAYMENT, "/finance/payments",
    TransactionType.CREDIT, "/finance/credits",
    TransactionType.PENDING_PAYMENT, "/finance/pending-payments",
    TransactionType.ENCUMBRANCE, "/finance/encumbrances"
  );

  private final RestClient restClient;

  public BaseTransactionService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<TransactionCollection> getTransactions(String query, int offset, int limit, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(TRANSACTIONS_ENDPOINT)
        .withQuery(query)
        .withOffset(offset)
        .withLimit(limit);
    return restClient.get(requestEntry, TransactionCollection.class, requestContext);
  }

  public Future<List<Transaction>> getTransactions(List<String> transactionIds, RequestContext requestContext) {
    if (!CollectionUtils.isEmpty(transactionIds)) {
      List<Future<TransactionCollection>> expenseClassesFutureList = StreamEx
        .ofSubLists(transactionIds, MAX_IDS_FOR_GET_RQ)
        .map(ids -> getTransactionsChunk(ids, requestContext))
        .collect(toList());

      return collectResultsOnSuccess(expenseClassesFutureList)
        .map(expenseClassCollections ->
          expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
        );
    }
    return succeededFuture(Collections.emptyList());
  }

  private Future<TransactionCollection> getTransactionsChunk(List<String> transactionIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(transactionIds));
    return this.getTransactions(query, 0, transactionIds.size(), requestContext);
  }

  public Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    return Optional.ofNullable(getEndpoint(transaction))
        .map(RequestEntry::new)
        .map(requestEntry -> restClient.post(requestEntry, transaction, Transaction.class, requestContext))
        .orElseGet(this::unsupportedOperation);
  }

  public Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    return Optional.ofNullable(getByIdEndpoint(transaction))
        .map(RequestEntry::new)
        .map(requestEntry -> requestEntry.withId(transaction.getId()))
        .map(requestEntry -> restClient.put(requestEntry, transaction, requestContext))
        .orElseGet(this::unsupportedOperation);
  }

  public Future<Void> updateTransactions(List<Transaction> transactions, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(transactions)) {
      return Future.succeededFuture();
    }
    return requestContext.getContext()
      .<List<Future<Void>>>executeBlocking(promise -> {
        List<Future<Void>> futures = new ArrayList<>();
        var semaphore = new Semaphore(1, requestContext.getContext().owner());
        for (Transaction tr : transactions) {
          semaphore.acquire(() -> {
            var future = updateTransaction(tr, requestContext)
              .onComplete(asyncResult -> semaphore.release());

            futures.add(future);
            if (futures.size() == transactions.size()) {
              promise.complete(futures);
            }
          });
        }
      })
      .compose(GenericCompositeFuture::join)
      .mapEmpty();
  }

  public <T> Future<T> unsupportedOperation() {
    Promise<T> promise = Promise.promise();
    promise.fail(new UnsupportedOperationException());
    return promise.future();
  }


  public String getEndpoint(Transaction transaction) {
    return TRANSACTION_ENDPOINTS.get(transaction.getTransactionType());
  }

  public String getByIdEndpoint(Transaction transaction) {
    return TRANSACTION_ENDPOINTS.get(transaction.getTransactionType()) + "/{id}";
  }

  public Future<Void> releaseEncumbrance(Transaction transaction, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(FINANCE_RELEASE_ENCUMBRANCE) + "/{id}").withId(transaction.getId());
    return restClient.post(requestEntry, null, Void.class, requestContext);
  }

}
