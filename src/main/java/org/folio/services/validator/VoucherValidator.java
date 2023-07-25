package org.folio.services.validator;

import org.folio.invoices.utils.VoucherProtectedFields;
import org.folio.rest.jaxrs.model.Voucher;

public class VoucherValidator {

  public void validateProtectedFields(Voucher voucher1, Voucher voucher2) {
    ProtectedFieldsValidator.validate(voucher1, voucher2, VoucherProtectedFields.values());
  }

}
