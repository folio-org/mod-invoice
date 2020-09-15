package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;

public class PendingPaymentCreateUpdateService extends BaseTransactionCreateUpdateService implements TransactionCreateUpdateService {

  public PendingPaymentCreateUpdateService(RestClient pendingPaymentRestClient) {
    super(pendingPaymentRestClient);
  }

  @Override
  public Transaction.TransactionType transactionType() {
    return Transaction.TransactionType.PENDING_PAYMENT;
  }
}
