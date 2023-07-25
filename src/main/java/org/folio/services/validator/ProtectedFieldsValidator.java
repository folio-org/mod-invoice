package org.folio.services.validator;

import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_FIELD_CHANGING;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;

import java.util.HashSet;
import java.util.Objects;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.ProtectedField;

public final class ProtectedFieldsValidator {

  public static <T> void validate(T t1, T t2, ProtectedField<T>[] protectedFields) {
    var changedFields = new HashSet<String>();
    for (var field : protectedFields) {
      var getter = field.getGetter();
      var o1 = getter.apply(t1);
      var o2 = getter.apply(t2);
      if (! Objects.deepEquals(o1,  o2)) {
        changedFields.add(field.getFieldName());
      }
    }
    if (changedFields.isEmpty()) {
      return;
    }
    var error = PROHIBITED_FIELD_CHANGING.toError()
        .withAdditionalProperty(PROTECTED_AND_MODIFIED_FIELDS, changedFields);
    throw new HttpException(400, error);
  }

}
