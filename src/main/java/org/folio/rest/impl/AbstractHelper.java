package org.folio.rest.impl;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.CREATED;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import io.vertx.core.Context;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.utils.TenantTool;

public abstract class AbstractHelper {
  public static final String ID = "id";
  public static final String ERROR_CAUSE = "cause";
  public static final String OKAPI_URL = "X-Okapi-Url";
  
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final Errors processingErrors = new Errors();

  protected final HttpClientInterface httpClient;
  protected final Map<String, String> okapiHeaders;
  protected final Context ctx;
  protected final String lang;
  private JsonObject tenantConfiguration;

  AbstractHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    this.httpClient = httpClient;
    this.okapiHeaders = okapiHeaders;
    this.ctx = ctx;
    this.lang = lang;
    setDefaultHeaders();
  }

  AbstractHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this.httpClient = getHttpClient(okapiHeaders);
    this.okapiHeaders = okapiHeaders;
    this.ctx = ctx;
    this.lang = lang;
    setDefaultHeaders();
  }

  /**
   * Some requests do not have body and in happy flow do not produce response body. The Accept header is required for calls to storage
   */
  private void setDefaultHeaders() {
    Map<String,String> customHeader = new HashMap<>();
    customHeader.put(HttpHeaders.ACCEPT.toString(), APPLICATION_JSON  + ", " + TEXT_PLAIN);
    httpClient.setDefaultHeaders(customHeader);
  }
  
  public static HttpClientInterface getHttpClient(Map<String, String> okapiHeaders) {
    final String okapiURL = okapiHeaders.getOrDefault(OKAPI_URL, "");
    final String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(OKAPI_HEADER_TENANT));

    return HttpClientFactory.getHttpClient(okapiURL, tenantId);
  }
  
  public Response buildOkResponse(Object body) {
    closeHttpClient();
    return Response.ok(body, APPLICATION_JSON).build();
  }

  public void closeHttpClient() {
    httpClient.closeClient();
  }

  public List<Error> getErrors() {
    return processingErrors.getErrors();
  }

}
