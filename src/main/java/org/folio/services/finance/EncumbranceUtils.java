package org.folio.services.finance;

import lombok.experimental.UtilityClass;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Transaction;

@UtilityClass
public class EncumbranceUtils {

  public static boolean allowEncumbranceToUnrelease(Transaction encumbranceTransaction) {
    var encumbrance = encumbranceTransaction.getEncumbrance();
    return encumbrance.getStatus() == Encumbrance.Status.RELEASED &&
      (encumbrance.getAmountExpended() == 0
        && encumbrance.getAmountCredited() == 0
        && encumbrance.getAmountAwaitingPayment() == 0);
  }
}
