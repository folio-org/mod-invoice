package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.utils.ExceptionUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class BaseApi {

  private final Errors processingErrors = new Errors();

  public Response buildOkResponse(Object body) {
    return Response.ok(body, APPLICATION_JSON)
      .build();
  }

  public Response buildNoContentResponse() {
    return Response.noContent()
      .build();
  }

  public Void handleErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, Throwable t) {
    asyncResultHandler.handle(succeededFuture(ExceptionUtil.buildErrorResponse(t)));
    return null;
  }

  public List<Error> getErrors() {
    return processingErrors.getErrors();
  }

}
