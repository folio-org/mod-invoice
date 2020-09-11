package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.RestClient;

public class PendingPaymentManagingService extends BaseTransactionManagingService implements TransactionManagingService {

  public PendingPaymentManagingService(RestClient pendingPaymentRestClient) {
    super(pendingPaymentRestClient);
  }

  @Override
  public Transaction.TransactionType transactionType() {
    return Transaction.TransactionType.PENDING_PAYMENT;
  }
}
