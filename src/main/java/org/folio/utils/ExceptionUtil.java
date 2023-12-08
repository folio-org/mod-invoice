package org.folio.utils;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.ErrorCodes;

public final class ExceptionUtil {

  private ExceptionUtil() {
  }

  public static boolean matches(Throwable cause, ErrorCodes errorCode) {
    if (cause instanceof HttpException httpException) {
      return httpException.getErrors().getErrors()
        .stream()
        .anyMatch(error -> error.getCode().equals(errorCode.getCode()));
    }
    return false;
  }

}
