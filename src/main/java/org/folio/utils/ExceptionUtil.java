package org.folio.utils;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.ErrorCodes;

public final class ExceptionUtil {

  private static final Logger logger = LogManager.getLogger();

  private ExceptionUtil() {
  }

  public static boolean matches(Throwable cause, ErrorCodes errorCode) {
    if (cause instanceof HttpException httpException) {
      var errors = httpException.getErrors();
      return errors != null && errors.getErrors() != null && errors.getErrors()
        .stream()
        .anyMatch(error -> errorCode.getCode().equals(error.getCode()));
    }
    return false;
  }

  public static Response buildErrorResponse(Throwable throwable) {
    return buildErrorResponse(ExceptionUtil.handleProcessingError(throwable));
  }

  private static Response buildErrorResponse(HttpException exception) {
    final Response.ResponseBuilder responseBuilder;
    switch (exception.getCode()) {
      case 400:
      case 403:
      case 404:
      case 409:
      case 413:
      case 422:
        responseBuilder = Response.status(exception.getCode());
        break;
      default:
        responseBuilder = Response.status(INTERNAL_SERVER_ERROR);
    }

    return responseBuilder
      .header(CONTENT_TYPE, APPLICATION_JSON)
      .entity(exception.getErrors())
      .build();
  }

  private static HttpException handleProcessingError(Throwable throwable) {
    logger.error("Exception encountered", throwable);

    if (throwable instanceof HttpException) {
      return ((HttpException) throwable);
    }

    return new HttpException(
      INTERNAL_SERVER_ERROR.getStatusCode(),
      GENERIC_ERROR_CODE.toError().withAdditionalProperty("cause", throwable.getMessage())
    );
  }

}
