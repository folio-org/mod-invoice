package org.folio.dataimport.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.DataImportProfilesClient;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;

@Component
public class JobProfileSnapshotCache {

  private static final Logger logger = LogManager.getLogger(JobProfileSnapshotCache.class);

  @Value("${mod.invoice.profile-snapshot-cache.expiration.time.seconds:3600}")
  private long cacheExpirationTime;

  private AsyncCache<String, Optional<ProfileSnapshotWrapper>> cache;

  @Autowired
  public JobProfileSnapshotCache(Vertx vertx) {
    cache = Caffeine.newBuilder()
      .expireAfterAccess(cacheExpirationTime, TimeUnit.SECONDS)
      .executor(task -> vertx.runOnContext(v -> task.run()))
      .buildAsync();
  }

  public CompletableFuture<Optional<ProfileSnapshotWrapper>> get(String profileSnapshotId, Map<String, String> okapiHeaders) {
    try {
      return cache.get(profileSnapshotId, (key, executor) -> loadJobProfileSnapshot(key, okapiHeaders));
    } catch (Exception e) {
      logger.warn("Error loading ProfileSnapshotWrapper by id: '{}'", profileSnapshotId, e);
      return CompletableFuture.failedFuture(e);
    }
  }

  private CompletableFuture<Optional<ProfileSnapshotWrapper>> loadJobProfileSnapshot(String profileSnapshotId, Map<String, String> okapiHeaders) {
    String okapiUrl = okapiHeaders.get(RestConstants.OKAPI_URL);
    String tenant = okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT);
    logger.debug("Trying to load jobProfileSnapshot by id  '{}' for cache, okapi url: {}, tenantId: {}", profileSnapshotId, okapiUrl, tenant);
    DataImportProfilesClient client = new DataImportProfilesClient(okapiUrl, tenant, okapiHeaders.get(OKAPI_HEADER_TOKEN));

    return client.getDataImportProfilesJobProfileSnapshotsById(profileSnapshotId)
      .toCompletionStage()
      .toCompletableFuture()
      .thenCompose(httpResponse -> {
        if (httpResponse.statusCode() == HttpStatus.HTTP_OK.toInt()) {
          logger.info("JobProfileSnapshot was loaded by id '{}'", profileSnapshotId);
          return CompletableFuture.completedFuture(Optional.of(httpResponse.bodyAsJson(ProfileSnapshotWrapper.class)));
        } else if (httpResponse.statusCode() == HttpStatus.HTTP_NOT_FOUND.toInt()) {
          logger.warn("JobProfileSnapshot was not found by id '{}'", profileSnapshotId);
          return CompletableFuture.completedFuture(Optional.empty());
        } else {
          String message = String.format("Error loading jobProfileSnapshot by id: '%s', status code: %s, response message: %s",
            profileSnapshotId, httpResponse.statusCode(), httpResponse.bodyAsString());
          logger.warn(message);
          return CompletableFuture.failedFuture(new CacheLoadingException(message));
        }
      });
  }

}
