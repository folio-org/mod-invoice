package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.exceptions.ExceptionUtil.convertToErrors;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class BaseApi {

  private static final String ERROR_CAUSE = "cause";
  private final Logger logger = LogManager.getLogger();
  private final Errors processingErrors = new Errors();

  public Response buildOkResponse(Object body) {
    return Response.ok(body, APPLICATION_JSON)
      .build();
  }

  public Response buildNoContentResponse() {
    return Response.noContent()
      .build();
  }

  public Response buildResponseWithLocation(String okapi, String endpoint, Object body) {
    try {
      return Response.created(new URI(okapi + endpoint))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .entity(body)
        .build();
    } catch (URISyntaxException e) {
      return Response.created(URI.create(endpoint))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .header(LOCATION, endpoint)
        .entity(body)
        .build();
    }
  }

  public Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, Throwable t) {
    asyncResultHandler.handle(succeededFuture(buildErrorResponse(t)));
    return null;
  }

  public List<Error> getErrors() {
    return processingErrors.getErrors();
  }

  protected Errors getProcessingErrors() {
    processingErrors.setTotalRecords(processingErrors.getErrors()
      .size());
    return processingErrors;
  }

  public void addProcessingError(Error error) {
    processingErrors.getErrors()
      .add(error);
  }

  protected HttpException handleProcessingError(Throwable throwable) {
    final Throwable cause = throwable.getCause();
    logger.error("Exception encountered", cause);
    if (cause instanceof HttpException) {
      return ((HttpException) cause);
    } else {
      return new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), GENERIC_ERROR_CODE.toError().withAdditionalProperty(ERROR_CAUSE, cause.getMessage()));
    }
  }

  public Response buildErrorResponse(Throwable throwable) {
  logger.error("Exception encountered", throwable.getCause());
    final int code = defineErrorCode(throwable);
    final Errors errors = convertToErrors(throwable);
    final Response.ResponseBuilder responseBuilder = createResponseBuilder(code);
    return responseBuilder.header(CONTENT_TYPE, APPLICATION_JSON)
      .entity(errors)
      .build();
    //return buildErrorResponse(handleProcessingError(throwable));
  }

  public static int defineErrorCode(Throwable throwable) {
    final Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
    if (cause instanceof HttpException) {
      return ((HttpException) cause).getCode();
    }
    return INTERNAL_SERVER_ERROR.getStatusCode();
  }

  public static javax.ws.rs.core.Response.ResponseBuilder createResponseBuilder(int code) {
    final javax.ws.rs.core.Response.ResponseBuilder responseBuilder;
    switch (code) {
    case 400:
    case 403:
    case 404:
    case 409:
    case 422:
      responseBuilder = javax.ws.rs.core.Response.status(code);
      break;
    default:
      responseBuilder = javax.ws.rs.core.Response.status(INTERNAL_SERVER_ERROR);
    }
    return responseBuilder;
  }
}

