package org.folio.rest.impl;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.rest.core.models.RequestContext;
import org.folio.utils.ExceptionUtil;

import io.vertx.core.Context;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

public abstract class AbstractHelper {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  public static final String ID = "id";
  public static final String SEARCH_PARAMS = "?limit=%s&offset=%s%s";

  protected final Map<String, String> okapiHeaders;
  protected final Context ctx;

  public AbstractHelper(Map<String, String> okapiHeaders, Context ctx) {
    this.okapiHeaders = okapiHeaders;
    this.ctx = ctx;
  }

  public Response buildErrorResponse(Throwable throwable) {
    return ExceptionUtil.buildErrorResponse(throwable);
  }

  public Response buildOkResponse(Object body) {
    return Response.ok(body, APPLICATION_JSON).build();
  }

  public <T> Response buildSuccessResponse(T body, String contentType) {
    return Response.ok(body, contentType).build();
  }

  public Response buildNoContentResponse() {
    return Response.noContent().build();
  }


  public Response buildResponseWithLocation(String endpoint, Object body) {
    try {
      return Response.created(new URI(okapiHeaders.get(OKAPI_URL) + endpoint))
        .header(CONTENT_TYPE, APPLICATION_JSON).entity(body).build();
    } catch (URISyntaxException e) {
      return Response.status(CREATED).location(URI.create(endpoint))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .header(LOCATION, endpoint).entity(body).build();
    }
  }

  protected void sendEvent(MessageAddress messageAddress, JsonObject data) {
    DeliveryOptions deliveryOptions = new DeliveryOptions();

    // Add okapi headers
    okapiHeaders.forEach(deliveryOptions::addHeader);

    ctx.owner()
      .eventBus()
      .send(messageAddress.address, data, deliveryOptions);
  }

  protected String getCurrentUserId() {
    return okapiHeaders.get(OKAPI_USERID_HEADER);
  }

  protected RequestContext buildRequestContext() {
    return new RequestContext(ctx, okapiHeaders);
  }

}
