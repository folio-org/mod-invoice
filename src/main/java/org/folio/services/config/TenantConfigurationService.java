package org.folio.services.config;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Configs;

import static org.folio.invoices.utils.ResourcePathResolver.TENANT_CONFIGURATION_ENTRIES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class TenantConfigurationService {

  private static final String CONFIGURATION_ENDPOINT = resourcesPath(TENANT_CONFIGURATION_ENTRIES);

  private final RestClient restClient;

  public TenantConfigurationService(RestClient restClient) {
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

  private String buildSearchingQuery(String[] searchCriteria) {
    return "(" + String.join(") OR (", searchCriteria) + ")";
  }
}
