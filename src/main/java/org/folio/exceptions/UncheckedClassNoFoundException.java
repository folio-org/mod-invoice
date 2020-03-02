package org.folio.exceptions;

public class UncheckedClassNoFoundException extends RuntimeException {
  public UncheckedClassNoFoundException(String message, Throwable cause){
    super(message, cause);
  }
}
