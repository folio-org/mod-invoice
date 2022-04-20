package org.folio.services.validator;

import java.util.Set;

import org.folio.invoices.utils.VoucherProtectedFields;
import org.folio.rest.jaxrs.model.Voucher;

public class VoucherValidator extends BaseValidator {

  public void validateProtectedFields(Voucher updatedVoucher, Voucher voucherFromStorage) {
    Set<String> fields = findChangedFields(updatedVoucher, voucherFromStorage, VoucherProtectedFields.getProtectedFields());
    verifyThatProtectedFieldsUnchanged(fields);
  }

}
