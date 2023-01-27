package org.folio.services.configuration;

import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Configs;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class ConfigurationService {
  private static final Logger logger = LogManager.getLogger(ConfigurationService.class);

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
  public Future<Configs> getConfigurationsEntries(RequestContext requestContext, String... searchCriteria) {
    String query = buildSearchingQuery(searchCriteria);
    RequestEntry requestEntry = new RequestEntry(CONFIGURATION_ENDPOINT)
        .withQuery(query)
        .withOffset(0)
        .withLimit(Integer.MAX_VALUE);
    return restClient.get(requestEntry, Configs.class, requestContext);
  }

  public Future<JsonObject> loadConfiguration(String moduleConfig, RequestContext requestContext) {
    String query = String.format(CONFIG_QUERY, moduleConfig);
    RequestEntry requestEntry = new RequestEntry(CONFIGURATION_ENDPOINT)
      .withQuery(query)
      .withOffset(0)
      .withLimit(Integer.MAX_VALUE);

    logger.info("GET request: {}", query);
    return restClient.get(requestEntry, Configs.class, requestContext)
      .map(configs -> {
        if (logger.isDebugEnabled()) {
          logger.debug("The response from mod-configuration: {}", JsonObject.mapFrom(configs).encodePrettily());
        }
        JsonObject config = new JsonObject();
        configs.getConfigs().forEach(entry -> config.put(entry.getConfigName(), entry.getValue()));
        return config;
      });
  }

  public Future<String> getSystemCurrency(RequestContext requestContext) {
    return loadConfiguration(SYSTEM_CONFIG_MODULE_NAME, requestContext)
      .map(jsonConfig -> extractLocalSettingConfigValueByName(jsonConfig, CURRENCY_CONFIG, DEFAULT_CURRENCY));
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
