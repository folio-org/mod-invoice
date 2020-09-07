package org.folio.services.config;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Configs;

public class TenantConfigurationService {

  private final RestClient configEntriesRestClient;

  public TenantConfigurationService(RestClient configEntriesRestClient) {
    this.configEntriesRestClient = configEntriesRestClient;
  }

  /**
   * Retrieve configuration by moduleName and configName from mod-configuration.
   *
   * @param searchCriteria name of the module for which the configuration is to be retrieved
   * @return CompletableFuture with Configs
   */
  public CompletableFuture<Configs> getConfigurationsEntries(RequestContext requestContext, String... searchCriteria) {
    String query = buildSearchingQuery(searchCriteria);
    return configEntriesRestClient.get(query, 0, 100, requestContext, Configs.class);
  }

  private String buildSearchingQuery(String[] searchCriteria) {
    return "(" + String.join(") OR (", searchCriteria) + ")";
  }
}
