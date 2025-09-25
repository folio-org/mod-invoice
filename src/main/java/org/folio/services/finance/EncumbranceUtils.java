package org.folio.services.finance;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Transaction;

import java.util.Objects;

@Log4j2
@UtilityClass
public class EncumbranceUtils {

  public static boolean allowEncumbranceToUnrelease(Transaction encumbranceTransaction) {
    if (Objects.isNull(encumbranceTransaction) || Objects.isNull(encumbranceTransaction.getEncumbrance())) {
      log.warn("allowEncumbranceToUnrelease:: Invalid transaction or encumbrance");
      return false;
    }
    var encumbrance = encumbranceTransaction.getEncumbrance();
    return encumbrance.getStatus() == Encumbrance.Status.RELEASED &&
      (encumbrance.getAmountExpended() == 0
        && encumbrance.getAmountCredited() == 0
        && encumbrance.getAmountAwaitingPayment() == 0);
  }
}
