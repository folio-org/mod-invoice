package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.Transaction;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class TransactionManagingServiceFactory {

  private Map<Transaction.TransactionType, TransactionManagingService> strategies;

  public TransactionManagingServiceFactory(Set<TransactionManagingService> services) {
    createStrategy(services);
  }

  private void createStrategy(Set<TransactionManagingService> strategySet) {
    strategies = new EnumMap<>(Transaction.TransactionType.class);
    strategySet.forEach(
      strategy -> strategies.put(strategy.transactionType(), strategy));
  }

  public TransactionManagingService findStrategy(Transaction.TransactionType transactionType) {
    return strategies.get(transactionType);
  }
}
