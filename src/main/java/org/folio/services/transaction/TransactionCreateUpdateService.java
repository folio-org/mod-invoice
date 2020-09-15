package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public interface TransactionCreateUpdateService {

  CompletableFuture<Transaction> createTransaction(Transaction transaction, RequestContext requestContext);
  CompletableFuture<Void> updateTransaction(Transaction transaction, RequestContext requestContext);
  Transaction.TransactionType transactionType();
}
