package org.folio.services.validator;

import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_FIELD_CHANGING;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Error;

public abstract class BaseValidator {


  protected void verifyThatProtectedFieldsUnchanged(Set<String> fields) {
    if(CollectionUtils.isNotEmpty(fields)) {
      Error error = PROHIBITED_FIELD_CHANGING.toError().withAdditionalProperty(PROTECTED_AND_MODIFIED_FIELDS, fields);
      throw new HttpException(400, error);
    }
  }

  protected Set<String> findChangedProtectedFields(Object newObject, Object existedObject, List<String> protectedFields) {
    Set<String> fields = new HashSet<>();
    for(String field : protectedFields) {
      try {
        if(isFieldNotEmpty(newObject, existedObject, field) && isFieldChanged(newObject, existedObject, field)) {
          fields.add(field);
        }
      } catch(IllegalAccessException e) {
        throw new CompletionException(e);
      }
    }
    return fields;
  }

  private boolean isFieldNotEmpty(Object newObject, Object existedObject, String field) {
    return FieldUtils.getDeclaredField(newObject.getClass(), field, true) != null && FieldUtils.getDeclaredField(existedObject.getClass(), field, true) != null;
  }

  private boolean isFieldChanged(Object newObject, Object existedObject, String field) throws IllegalAccessException {
    return !EqualsBuilder.reflectionEquals(FieldUtils.readDeclaredField(newObject, field, true), FieldUtils.readDeclaredField(existedObject, field, true), true, existedObject.getClass(), true);
  }

}
