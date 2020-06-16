package org.folio.models;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.jaxrs.model.FundDistribution;

public class FundDistributionTransactionHolder {
  private FundDistribution fundDistribution;
  private Transaction transaction;

  public FundDistribution getFundDistribution() {
    return fundDistribution;
  }

  public void setFundDistribution(FundDistribution fundDistribution) {
    this.fundDistribution = fundDistribution;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public FundDistributionTransactionHolder withTransaction(Transaction transaction) {
    setTransaction(transaction);
    return this;
  }

  public FundDistributionTransactionHolder withFundDistribution(FundDistribution fundDistribution) {
    setFundDistribution(fundDistribution);
    return this;
  }
}
