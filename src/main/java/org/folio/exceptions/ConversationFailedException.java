package org.folio.exceptions;

public class ConversationFailedException extends RuntimeException{
  public ConversationFailedException(Class<?> sourceType, Class<?> targetType, Throwable cause) {
    super("Failed to convert from type [" + sourceType.getName() + "] to type [" + targetType.getName() + "]", cause);
  }
}

