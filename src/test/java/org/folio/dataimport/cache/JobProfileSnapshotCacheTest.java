package org.folio.dataimport.cache;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.junit5.VertxExtension;
import org.folio.ApiTestSuite;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;

@ExtendWith(VertxExtension.class)
public class JobProfileSnapshotCacheTest extends ApiTestBase {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  private static final String TENANT_ID = "diku";
  private static final String JOB_PROFILE_SNAPSHOTS_MOCK = "jobProfileSnapshots";
  private static final String SNAPSHOT_ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";

  private Vertx vertx = Vertx.vertx();
  private JobProfileSnapshotCache jobProfileSnapshotCache = new JobProfileSnapshotCache(vertx);

  ProfileSnapshotWrapper jobProfileSnapshot = new ProfileSnapshotWrapper()
    .withId(UUID.randomUUID().toString())
    .withContentType(JOB_PROFILE)
    .withChildSnapshotWrappers(List.of(new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withContentType(ACTION_PROFILE)));

  private Map<String, String> okapiHeaders;

  @BeforeEach
  public void setUp() {
    this.okapiHeaders = Map.of(
      RestVerticle.OKAPI_HEADER_TENANT, TENANT_ID,
      RestVerticle.OKAPI_HEADER_TOKEN, "token",
      RestConstants.OKAPI_URL, "http://localhost:" + ApiTestSuite.mockPort);
  }

  @Test
  public void shouldReturnProfileSnapshot() throws InterruptedException, ExecutionException, TimeoutException {
    addMockEntry(JOB_PROFILE_SNAPSHOTS_MOCK, jobProfileSnapshot);

    CompletableFuture<Optional<ProfileSnapshotWrapper>> optionalFuture = jobProfileSnapshotCache.get(jobProfileSnapshot.getId(), this.okapiHeaders);

    Optional<ProfileSnapshotWrapper> profileOptional = optionalFuture.get(5, TimeUnit.SECONDS);
    Assertions.assertTrue(profileOptional.isPresent());
    ProfileSnapshotWrapper actualProfileSnapshot = profileOptional.get();
    Assertions.assertEquals(jobProfileSnapshot.getId(), actualProfileSnapshot.getId());
    Assertions.assertFalse(actualProfileSnapshot.getChildSnapshotWrappers().isEmpty());
    Assertions.assertEquals(jobProfileSnapshot.getChildSnapshotWrappers().get(0).getId(),
      actualProfileSnapshot.getChildSnapshotWrappers().get(0).getId());
  }

  @Test
  public void shouldReturnEmptyOptionalWhenGetNotFoundOnSnapshotLoading() throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<Optional<ProfileSnapshotWrapper>> optionalFuture = jobProfileSnapshotCache.get(jobProfileSnapshot.getId(), this.okapiHeaders);

    Optional<ProfileSnapshotWrapper> profileOptional = optionalFuture.get(5, TimeUnit.SECONDS);
    Assertions.assertTrue(profileOptional.isEmpty());
  }

  @Test
  public void shouldReturnFailedFutureWhenGetServerErrorOnSnapshotLoading() {
    CompletableFuture<Optional<ProfileSnapshotWrapper>> optionalFuture = jobProfileSnapshotCache.get(SNAPSHOT_ID_FOR_INTERNAL_SERVER_ERROR, this.okapiHeaders);
    Assertions.assertThrows(ExecutionException.class, () -> optionalFuture.get(5, TimeUnit.SECONDS));
  }

  @Test
  public void shouldReturnFailedFutureWhenSpecifiedProfileSnapshotIdIsNull() {
    CompletableFuture<Optional<ProfileSnapshotWrapper>> optionalFuture = jobProfileSnapshotCache.get(null, this.okapiHeaders);
    Assertions.assertThrows(ExecutionException.class, () -> optionalFuture.get(5, TimeUnit.SECONDS));
  }

}
