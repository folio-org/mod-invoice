package org.folio.exceptions;

public class BatchVoucherGenerationException extends RuntimeException {
  public BatchVoucherGenerationException(String message) {
    super(message);
  }

  public BatchVoucherGenerationException(String message, Throwable cause) {
    super(message, cause);
  }

}
