package org.folio.services.finance.transaction;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public class PaymentCreateUpdateService extends BaseTransactionCreateUpdateService {

  public PaymentCreateUpdateService(RestClient paymentRestClient) {
    super(paymentRestClient);
  }

  @Override
  public CompletableFuture<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    future.completeExceptionally(new UnsupportedOperationException());
    return future;
  }

  @Override
  public Transaction.TransactionType transactionType() {
    return Transaction.TransactionType.PAYMENT;
  }
}
