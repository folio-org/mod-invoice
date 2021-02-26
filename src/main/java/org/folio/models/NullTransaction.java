package org.folio.models;

import org.folio.rest.acq.model.finance.Transaction;

public class NullTransaction extends Transaction {

    public NullTransaction(String currency) {
        setCurrency(currency);
    }

    @Override
    public Double getAmount() {
        return 0d;
    }
}
