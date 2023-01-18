package org.folio.invoices.rest.exceptions;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;

public class HttpException extends RuntimeException {
  private static final long serialVersionUID = 8109197948434861504L;

  private final int code;
  private final transient Errors errors;

  public HttpException(int code, String message) {
    super(StringUtils.isNotEmpty(message) ? message : ErrorCodes.GENERIC_ERROR_CODE.getDescription());
    this.code = code;
    this.errors = new Errors().withTotalRecords(1);
    errors.getErrors().add(new Error().withCode(ErrorCodes.GENERIC_ERROR_CODE.getCode()).withMessage(message));
  }

  public HttpException(int code, ErrorCodes errorCode) {
    super(errorCode.getDescription());
    this.code = code;
    this.errors = new Errors().withTotalRecords(1);
    this.errors.getErrors().add(errorCode.toError());
  }

  public HttpException(int code, Error error) {
    super(error.getMessage());
    this.code = code;
    this.errors = new Errors().withTotalRecords(1);
    this.errors.getErrors().add(error);
  }


  public HttpException(int code, ErrorCodes errCodes, List<Parameter> parameters) {
    super(errCodes.getDescription());
    this.errors = new Errors()
      .withErrors(Collections.singletonList(new Error()
        .withCode(errCodes.getCode())
        .withMessage(errCodes.getDescription())
        .withParameters(parameters)))
      .withTotalRecords(1);
    this.code = code;
  }

  public HttpException(int code, Errors errors, String message) {
    super(message);
    this.code = code;
    this.errors = errors.withTotalRecords(errors.getErrors().size());
  }

  public int getCode() {
    return code;
  }

  public Errors getErrors() {
    return errors;
  }
}
