package org.folio.services.finance.transaction;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public abstract class BaseTransactionCreateUpdateService implements TransactionCreateUpdateService {

  private final RestClient restClient;

  protected BaseTransactionCreateUpdateService(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public CompletableFuture<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    return restClient.post(transaction, requestContext, Transaction.class);
  }

  @Override
  public CompletableFuture<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    return restClient.put(transaction.getId(), transaction, requestContext);
  }
}
