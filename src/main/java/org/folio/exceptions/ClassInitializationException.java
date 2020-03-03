package org.folio.exceptions;

public class ClassInitializationException extends RuntimeException {
  public ClassInitializationException(String message, Throwable cause){
    super(message, cause);
  }
}
