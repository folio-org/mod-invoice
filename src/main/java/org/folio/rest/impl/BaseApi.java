package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;

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
    final Throwable cause = Optional.ofNullable(throwable.getCause()).orElse(throwable);
    logger.error("Exception encountered", cause);

    if (cause instanceof HttpException) {
      return ((HttpException) cause);
    } else {
      return new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), GENERIC_ERROR_CODE.toError().withAdditionalProperty(ERROR_CAUSE, cause.getMessage()));
    }
  }

  public Response buildErrorResponse(Throwable throwable) {
    return buildErrorResponse(handleProcessingError(throwable));
  }

  public Response buildErrorResponse(HttpException exception) {
    final Response.ResponseBuilder responseBuilder;
    switch (exception.getCode()) {
    case 400:
    case 403:
    case 404:
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
}

