package org.folio.services.voucher;

import static org.folio.invoices.utils.HelperUtils.SEARCH_PARAMS;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.ExportConfigCollection;

import io.vertx.core.Future;

public class BatchVoucherExportConfigService {

  public static final String GET_EXPORT_CONFIGS_BY_QUERY = resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS) + SEARCH_PARAMS;
  private final RestClient restClient;
  public BatchVoucherExportConfigService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<ExportConfig> createExportConfig(ExportConfig exportConfig, RequestContext requestContext) {
    return restClient.post(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS), exportConfig, ExportConfig.class, requestContext);
  }

  public Future<ExportConfig> getExportConfig(String id, RequestContext requestContext) {
    return restClient.get(resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, id), ExportConfig.class, requestContext);
  }

  public Future<Credentials> getExportConfigCredentials(String id, RequestContext requestContext) {
    var endpoint = String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id);
    return restClient.get(endpoint, Credentials.class, requestContext);
  }

  public Future<Credentials> createCredentials(String id, Credentials credentials, RequestContext requestContext) {
    return StringUtils.isBlank(credentials.getUsername()) || StringUtils.isBlank(credentials.getPassword()) ?
      Future.succeededFuture(null) :
      restClient.post(String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id), credentials, Credentials.class, requestContext);
  }

  public Future<Void> putExportConfig(ExportConfig exportConfig, RequestContext requestContext) {
    String endpoint = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, exportConfig.getId());
    return restClient.put(endpoint, exportConfig, requestContext);
  }

  public Future<Void> putExportConfigCredentials(String id, Credentials credentials, RequestContext requestContext) {
    String path = String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id);
    return StringUtils.isBlank(credentials.getUsername()) || StringUtils.isBlank(credentials.getPassword()) ?
      restClient.delete(path, requestContext):
      restClient.put(path, credentials, requestContext);
  }

  public Future<Void> deleteExportConfig(String id  , RequestContext requestContext) {
    String path = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, id);
    return restClient.delete(path, requestContext);
  }

  public Future<ExportConfigCollection> getExportConfigs(int limit, int offset, String query, RequestContext requestContext) {
    String queryParam = getEndpointWithQuery(query);
    String endpoint = String.format(GET_EXPORT_CONFIGS_BY_QUERY, limit, offset, queryParam);
    return restClient.get(endpoint, ExportConfigCollection.class, requestContext);
  }

  }
