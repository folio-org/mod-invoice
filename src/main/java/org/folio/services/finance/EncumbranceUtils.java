package org.folio.services.finance;

import lombok.experimental.UtilityClass;
import org.folio.rest.acq.model.finance.Transaction;

@UtilityClass
public class EncumbranceUtils {

  public static boolean allowTransactionToUnrelease(Transaction transaction) {
    var encumbrance = transaction.getEncumbrance();
    return encumbrance.getAmountExpended() == 0
      && encumbrance.getAmountCredited() == 0
      && encumbrance.getAmountAwaitingPayment() == 0;
  }
}
