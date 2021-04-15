package org.folio.services.configuration;

import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Configs;

import io.vertx.core.json.JsonObject;

public class ConfigurationService {
  private static final Logger logger = LogManager.getLogger();

  private static final String CONFIG_QUERY = "module==%s";
  private static final String CONFIGURATION_ENDPOINT = resourcesPath(TENANT_CONFIGURATION_ENTRIES);
  public static final String SYSTEM_CONFIG_MODULE_NAME = "ORG";
  public static final String CURRENCY_CONFIG = "currency";
  public static final String DEFAULT_CURRENCY = "USD";
  public static final String LOCALE_SETTINGS = "localeSettings";
  public static final String TZ_CONFIG = "timezone";
  public static final String TZ_UTC = "UTC";

  private final RestClient restClient;

  public ConfigurationService(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * Retrieve configuration by moduleName and configName from mod-configuration.
   *
   * @param searchCriteria name of the module for which the configuration is to be retrieved
   * @return CompletableFuture with Configs
   */
  public CompletableFuture<Configs> getConfigurationsEntries(RequestContext requestContext, String... searchCriteria) {
    String query = buildSearchingQuery(searchCriteria);
    RequestEntry requestEntry = new RequestEntry(CONFIGURATION_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(100);
    return restClient.get(requestEntry, requestContext, Configs.class);
  }

  public CompletableFuture<JsonObject> loadConfiguration(String moduleConfig, RequestContext requestContext) {
    String query = String.format(CONFIG_QUERY, moduleConfig);
    RequestEntry requestEntry = new RequestEntry(CONFIGURATION_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(100);

    logger.info("GET request: {}", query);
    return restClient.get(requestEntry, requestContext, Configs.class)
      .thenApply(configs -> {
        if (logger.isDebugEnabled()) {
          logger.debug("The response from mod-configuration: {}", JsonObject.mapFrom(configs).encodePrettily());
        }
        JsonObject config = new JsonObject();
        configs.getConfigs().forEach(entry -> config.put(entry.getConfigName(), entry.getValue()));
        return config;
      });
  }

  public CompletableFuture<String> getSystemCurrency(RequestContext requestContext) {
    CompletableFuture<String> future = new CompletableFuture<>();
    loadConfiguration(SYSTEM_CONFIG_MODULE_NAME, requestContext)
      .thenApply(jsonConfig -> extractLocalSettingConfigValueByName(jsonConfig, CURRENCY_CONFIG, DEFAULT_CURRENCY))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public CompletableFuture<String> getSystemTimeZone(RequestContext requestContext) {
    CompletableFuture<String> future = new CompletableFuture<>();
    loadConfiguration(SYSTEM_CONFIG_MODULE_NAME, requestContext)
      .thenApply(jsonConfig -> extractLocalSettingConfigValueByName(jsonConfig, TZ_CONFIG, TZ_UTC))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  private String extractLocalSettingConfigValueByName(JsonObject config, String name, String defaultValue) {
    String localeSettings = config.getString(LOCALE_SETTINGS);
    String confValue;
    if (StringUtils.isEmpty(localeSettings)) {
      confValue = defaultValue;
    } else {
      confValue = new JsonObject(config.getString(LOCALE_SETTINGS)).getString(name, defaultValue);
    }
    return confValue;
  }

  private String buildSearchingQuery(String[] searchCriteria) {
    return "(" + String.join(") OR (", searchCriteria) + ")";
  }
}
