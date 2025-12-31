package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.exceptions.FtpException;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.ExportConfigCollection;
import org.folio.services.ftp.FtpUploadService;
import org.folio.services.ftp.SftpUploadService;
import org.folio.services.voucher.BatchVoucherExportConfigService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class BatchVoucherExportConfigHelper extends AbstractHelper {

  private static final Logger log = LogManager.getLogger();
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
    log.debug("createCredentials:: Trying to create export config credentials for configId={}", id);
    if (StringUtils.isBlank(credentials.getUsername()) || StringUtils.isBlank(credentials.getPassword())) {
      log.info("createCredentials:: new username and/or password is empty, so new record '{}' is not being saved to db", id);
      return Future.succeededFuture(new Credentials());
    }
    return restClient.post(String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id),
      credentials, Credentials.class, buildRequestContext());
  }

  public Future<Void> putExportConfig(ExportConfig exportConfig) {
    String endpoint = resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS, exportConfig.getId());
    return restClient.put(endpoint, exportConfig, buildRequestContext());
  }

  public Future<Void> putExportConfigCredentials(String id, Credentials credentials) {
    String path = String.format(resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS), id);
    log.debug("putExportConfigCredentials:: Trying to update export config credentials for configId={} with path={}", id, path);
    if (StringUtils.isBlank(credentials.getUsername()) || StringUtils.isBlank(credentials.getPassword())) {
      log.info("putExportConfigCredentials:: new username and/or password is empty, so deleting existing record");
      return restClient.delete(path, buildRequestContext());
    }
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

    return Future.join(exportConfigFuture, credentialsFuture)
      .map(cf -> {
        try {
          ExportConfig config = exportConfigFuture.result();
          return config.getFtpFormat() == ExportConfig.FtpFormat.FTP ?
            new FtpUploadService(ctx, config.getUploadURI(), config.getFtpPort()) :
            new SftpUploadService(config.getUploadURI(), config.getFtpPort());
        } catch (URISyntaxException e) {
          throw new CompletionException(e);
        }
      })
      .compose(helper -> {
        Credentials credentials = credentialsFuture.result();
        String username = credentials.getUsername();
        ExportConfig.FtpFormat exchangeConnectionFormat = helper.getExchangeConnectionFormat();
        return helper.testConnection(username, credentials.getPassword())
          .compose(aVoid -> Future.succeededFuture(String.format("Successfully logged in to %s with username: %s", exchangeConnectionFormat, username)))
          .recover(throwable -> {
            logger.error("Could not login to {} with username: {}", exchangeConnectionFormat, username, throwable);
            return Future.failedFuture(new FtpException(HttpStatus.HTTP_FORBIDDEN.toInt(), username));
          });
      });
  }
}
