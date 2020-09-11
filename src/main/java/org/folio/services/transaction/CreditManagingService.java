package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public class CreditManagingService extends BaseTransactionManagingService {

  public CreditManagingService(RestClient creditRestClient) {
    super(creditRestClient);
  }

  @Override
  public CompletableFuture<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    future.completeExceptionally(new UnsupportedOperationException());
    return future;
  }

  @Override
  public Transaction.TransactionType transactionType() {
    return Transaction.TransactionType.CREDIT;
  }
}
