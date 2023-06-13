package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.ExportConfigCollection;
import org.folio.services.ftp.FtpUploadService;
import org.folio.services.voucher.BatchVoucherExportConfigService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;

public class BatchVoucherExportConfigHelper extends AbstractHelper {

  public static final String GET_EXPORT_CONFIGS_BY_QUERY = resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS) + SEARCH_PARAMS;
  @Autowired
  BatchVoucherExportConfigService batchVoucherExportConfigService;
  private final RequestContext requestContext;
  RestClient restClient;
  public BatchVoucherExportConfigHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.requestContext = new RequestContext(ctx,okapiHeaders);
    SpringContextUtil.autowireDependencies(this, ctx);
    restClient = new RestClient();
  }

  public Future<ExportConfig> createExportConfig(ExportConfig exportConfig) {
    return batchVoucherExportConfigService.createExportConfig(exportConfig, requestContext);
  }

  public Future<ExportConfig> getExportConfig(String id) {
    return restClient.get(resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, id), ExportConfig.class, buildRequestContext());
  }

  public Future<Credentials> getExportConfigCredentials(String id) {
    var endpoint = String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id);
    return restClient.get(endpoint, Credentials.class, buildRequestContext());
  }

  public Future<Credentials> createCredentials(String id, Credentials credentials) {
    return restClient.post(String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id), credentials, Credentials.class, buildRequestContext());
  }

  public Future<Void> putExportConfig(ExportConfig exportConfig) {
    String endpoint = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, exportConfig.getId());
    return restClient.put(endpoint, exportConfig, buildRequestContext());
  }

  public Future<Void> putExportConfigCredentials(String id, Credentials credentials) {
    String path = String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id);
    return restClient.put(path, credentials, buildRequestContext());
  }

  public Future<Void> deleteExportConfig(String id) {
    String path = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, id);
    return restClient.delete(path, buildRequestContext());
  }

  public Future<ExportConfigCollection> getExportConfigs(int limit, int offset, String query) {
    String queryParam = getEndpointWithQuery(query);
    String endpoint = String.format(GET_EXPORT_CONFIGS_BY_QUERY, limit, offset, queryParam);
    return restClient.get(endpoint, ExportConfigCollection.class, buildRequestContext());
  }

  public Future<String> testUploadUri(String id) {
    Future<ExportConfig> exportConfigFuture = getExportConfig(id);
    Future<Credentials> credentialsFuture = getExportConfigCredentials(id);

    return CompositeFuture.join(exportConfigFuture, credentialsFuture)
      .map(cf -> {
        try {
          ExportConfig config = exportConfigFuture.result();
          return new FtpUploadService(ctx, config.getUploadURI(), config.getFtpPort());
        } catch (URISyntaxException e) {
          throw new CompletionException(e);
        }
      })
      .compose(helper -> {
        Credentials creds = credentialsFuture.result();
        return helper.login(creds.getUsername(), creds.getPassword())
          .compose(ftpClient -> {
            try {
              return Future.succeededFuture(ftpClient.getStatus());
            } catch (IOException e) {
              throw new IllegalArgumentException(e);
            }
          });
      });
  }
}
