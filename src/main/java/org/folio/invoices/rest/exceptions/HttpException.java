package org.folio.invoices.rest.exceptions;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.rest.jaxrs.model.Error;

public class HttpException extends RuntimeException {
  private static final long serialVersionUID = 8109197948434861504L;

  private final int code;
  private final transient Error error;

  public HttpException(int code, String message) {
    super(StringUtils.isNotEmpty(message) ? message : ErrorCodes.GENERIC_ERROR_CODE.getDescription());
    this.code = code;
    this.error = new Error().withCode(ErrorCodes.GENERIC_ERROR_CODE.getCode()).withMessage(message);
  }

  public HttpException(int code, ErrorCodes errorCode) {
    this.code = code;
    this.error = new Error().withCode(errorCode.getCode()).withMessage(errorCode.getDescription());
  }

  public HttpException(int code, Error error) {
    this.code = code;
    this.error = error;
  }

  public int getCode() {
    return code;
  }

  public Error getError() {
    return error;
  }
}
