package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.Transaction;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class TransactionManagingServiceFactory {

  private Map<Transaction.TransactionType, TransactionCreateUpdateService> strategies;

  public TransactionManagingServiceFactory(Set<TransactionCreateUpdateService> services) {
    createStrategy(services);
  }

  private void createStrategy(Set<TransactionCreateUpdateService> strategySet) {
    strategies = new EnumMap<>(Transaction.TransactionType.class);
    strategySet.forEach(
      strategy -> strategies.put(strategy.transactionType(), strategy));
  }

  public TransactionCreateUpdateService findStrategy(Transaction.TransactionType transactionType) {
    return strategies.get(transactionType);
  }
}
