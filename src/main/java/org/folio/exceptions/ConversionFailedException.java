package org.folio.exceptions;

public class ConversionFailedException extends RuntimeException {
  public ConversionFailedException(Class<?> sourceType, Class<?> targetType, Throwable cause) {
    super("Failed to convert from type [" + sourceType.getName() + "] to type [" + targetType.getName() + "]", cause);
  }
}
