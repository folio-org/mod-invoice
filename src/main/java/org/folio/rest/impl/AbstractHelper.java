package org.folio.rest.impl;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.invoices.utils.ErrorCodes.MOD_CONFIG_ERROR;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_FIELD_CHANGING;
import static org.folio.invoices.utils.HelperUtils.LANG;
import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.verifyAndExtractBody;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.money.convert.ExchangeRateProvider;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public abstract class AbstractHelper {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  public static final String ID = "id";
  public static final String ERROR_CAUSE = "cause";
  public static final String DEFAULT_SYSTEM_CURRENCY = "USD";
  private static final String SYSTEM_CURRENCY_PROPERTY_NAME = "currency";
  static final String LOCALE_SETTINGS = "localeSettings";
  static final String SYSTEM_CONFIG_MODULE_NAME = "ORG";
  static final String INVOICE_CONFIG_MODULE_NAME = "INVOICE";
  public static final String CONFIG_QUERY = "module==%s and configName==%s";
  public static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  static final String SEARCH_PARAMS = "?limit=%s&offset=%s%s&lang=%s";
  static final String SYSTEM_CONFIG_QUERY = String.format(CONFIG_QUERY, SYSTEM_CONFIG_MODULE_NAME, LOCALE_SETTINGS);

  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {}";

  private final Errors processingErrors = new Errors();
  private ExchangeRateProvider exchangeRateProvider;

  protected final HttpClientInterface httpClient;
  protected final Map<String, String> okapiHeaders;
  protected final Context ctx;
  protected final String lang;
  private Configs tenantConfiguration = new Configs().withTotalRecords(0);


  public AbstractHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    this.httpClient = httpClient;
    this.okapiHeaders = okapiHeaders;
    this.ctx = ctx;
    this.lang = lang;
  }

  public AbstractHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  /**
   * Retrieve configuration by moduleName and configName from mod-configuration.
   * @param searchCriteria name of the module for which the configuration is to be retrieved
   * @return CompletableFuture with Configs
   */
  private CompletableFuture<Configs> getConfigurationsEntries(String ... searchCriteria) {

    CompletableFuture<Configs> future = new VertxCompletableFuture<>();
    try {
      String query = buildSearchingQuery(searchCriteria);
      String endpoint = String.format("/configurations/entries?query=%s&limit=%d&lang=%s", encodeQuery(query, logger), 100, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenAccept(entries -> {
          if (logger.isDebugEnabled()) {
            logger.debug("The response from mod-configuration: {}", entries.encodePrettily());
          }
          Configs configs = entries.mapTo(Configs.class);
          future.complete(configs);
        })
        .exceptionally(t -> {
          logger.error("Error happened while getting configs", t);
          future.completeExceptionally(new HttpException(500, MOD_CONFIG_ERROR));
          return null;
        });

    } catch (Exception e) {
      logger.error("Error happened while getting configs", e);
      future.completeExceptionally(new HttpException(500, MOD_CONFIG_ERROR));
    }
    return future;
  }

  private String buildSearchingQuery(String[] searchCriteria) {
    return "(" + String.join(") OR (", searchCriteria) + ")";
  }

  public CompletableFuture<Void> loadTenantConfiguration(String ... searchCriteria) {
    if (isConfigEmpty()) {
      return getConfigurationsEntries(searchCriteria)
        .thenAccept(config -> this.tenantConfiguration = config);
    }
    return completedFuture(null);
  }

  private boolean isConfigEmpty() {
    return CollectionUtils.isEmpty(this.tenantConfiguration.getConfigs());
  }

  public Configs getLoadedTenantConfiguration() {
    return this.tenantConfiguration;
  }

  /**
   *  Retrieves systemCurrency from mod-configuration
   *  if config is empty than use {@link #DEFAULT_SYSTEM_CURRENCY}
   */
  String getSystemCurrency() {
    JsonObject configValue = getLoadedTenantConfiguration().getConfigs()
      .stream()
      .filter(this::isLocaleConfig)
      .map(config -> new JsonObject(config.getValue()))
      .findFirst()
      .orElseGet(JsonObject::new);

    return configValue.getString(SYSTEM_CURRENCY_PROPERTY_NAME, DEFAULT_SYSTEM_CURRENCY);
  }

  private boolean isLocaleConfig(Config config) {
    return SYSTEM_CONFIG_MODULE_NAME.equals(config.getModule()) && LOCALE_SETTINGS.equals(config.getConfigName());
  }

  protected CompletableFuture<String> createRecordInStorage(JsonObject recordData, String endpoint) {
    CompletableFuture<String> future = new VertxCompletableFuture<>(ctx);
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("Sending 'POST {}' with body: {}", endpoint, recordData.encodePrettily());
      }
      httpClient
        .request(HttpMethod.POST, recordData.toBuffer(), endpoint, okapiHeaders)
        .thenApply(this::verifyAndExtractRecordId)
        .thenAccept(id -> {
          future.complete(id);
          logger.debug("'POST {}' request successfully processed. Record with '{}' id has been created", endpoint, id);
        })
        .exceptionally(throwable -> {
          future.completeExceptionally(throwable);
          logger.error("'POST {}' request failed. Request body: {}", throwable, endpoint, recordData.encodePrettily());
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  public CompletableFuture<Void> postRecorderWithoutResponseBody(JsonObject recordData, String endpoint) {
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);
    try {
      httpClient.request(HttpMethod.POST, recordData.toBuffer(), endpoint, okapiHeaders)
        .thenAccept(HelperUtils::verifyResponse)
        .thenApply(future::complete)
        .exceptionally(t -> {
          logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, t, HttpMethod.POST, endpoint);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, e, HttpMethod.POST, endpoint);
      future.completeExceptionally(e);
    }
    return future;
  }

  private String verifyAndExtractRecordId(org.folio.rest.tools.client.Response response) {
    logger.debug("Validating received response");

    JsonObject body = verifyAndExtractBody(response);

    String id;
    if (body != null && !body.isEmpty() && body.containsKey(ID)) {
      id = body.getString(ID);
    } else {
      String location = response.getHeaders().get(LOCATION);
      id = location.substring(location.lastIndexOf('/') + 1);
    }
    return id;
  }

  protected void addProcessingError(Error error) {
    processingErrors.getErrors().add(error);
  }

  protected Errors getProcessingErrors() {
    processingErrors.setTotalRecords(processingErrors.getErrors().size());
    return processingErrors;
  }

  protected void verifyThatProtectedFieldsUnchanged(Set<String> fields) {
    if(CollectionUtils.isNotEmpty(fields)) {
      Error error = PROHIBITED_FIELD_CHANGING.toError().withAdditionalProperty(PROTECTED_AND_MODIFIED_FIELDS, fields);
      throw new HttpException(400, error);
    }
  }

  public Response buildErrorResponse(Throwable throwable) {
    return buildErrorResponse(handleProcessingError(throwable));
  }

  protected int handleProcessingError(Throwable throwable) {
    final Throwable cause = throwable.getCause();
    logger.error("Exception encountered", cause);
    final Error error;
    final int code;

    if (cause instanceof HttpException) {
      code = ((HttpException) cause).getCode();
      error = ((HttpException) cause).getError();
    } else {
      code = INTERNAL_SERVER_ERROR.getStatusCode();
      error = GENERIC_ERROR_CODE.toError().withAdditionalProperty(ERROR_CAUSE, cause.getMessage());
    }

    addProcessingError(error);

    return code;
  }

  public Response buildErrorResponse(int code) {
    final Response.ResponseBuilder responseBuilder;
    switch (code) {
      case 400:
      case 403:
      case 404:
      case 422:
        responseBuilder = Response.status(code);
        break;
      default:
        responseBuilder = Response.status(INTERNAL_SERVER_ERROR);
    }
    closeHttpClient();

    return responseBuilder
      .header(CONTENT_TYPE, APPLICATION_JSON)
      .entity(getProcessingErrors())
      .build();
  }

  public Response buildOkResponse(Object body) {
    closeHttpClient();
    return Response.ok(body, APPLICATION_JSON).build();
  }

  public <T> Response buildSuccessResponse(T body, String contentType) {
    closeHttpClient();
    return Response.ok(body, contentType).build();
  }

  public Response buildNoContentResponse() {
    closeHttpClient();
    return Response.noContent().build();
  }

  public void closeHttpClient() {
    httpClient.closeClient();
  }

  public List<Error> getErrors() {
    return processingErrors.getErrors();
  }

  public ExchangeRateProvider getCurrentExchangeRateProvider() {
    if (Objects.isNull(exchangeRateProvider)) {
      exchangeRateProvider = HelperUtils.getInvoiceExchangeRateProvider();
      logger.debug("Created ExchangeRateProvider name: {}", exchangeRateProvider.getContext().getProviderName());
    }
    return exchangeRateProvider;
  }

  public Response buildResponseWithLocation(String endpoint, Object body) {
    closeHttpClient();
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

    data.put(LANG, lang);

    ctx.owner()
      .eventBus()
      .send(messageAddress.address, data, deliveryOptions);
  }

  protected String getCurrentUserId() {
    return okapiHeaders.get(OKAPI_USERID_HEADER);
  }
}
