package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.buildQuery;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_MEMBERSHIPS;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_UNITS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.folio.rest.acq.model.AcquisitionsUnitCollection;
import org.folio.rest.acq.model.AcquisitionsUnitMembershipCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class AcquisitionsUnitsHelper extends AbstractHelper {
  private static final String GET_UNITS_BY_QUERY = resourcesPath(ACQUISITIONS_UNITS) + SEARCH_PARAMS;
  private static final String GET_UNITS_MEMBERSHIPS_BY_QUERY = resourcesPath(ACQUISITIONS_MEMBERSHIPS) + SEARCH_PARAMS;

  public AcquisitionsUnitsHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  CompletableFuture<AcquisitionsUnitCollection> getAcquisitionsUnits(String query, int offset, int limit) {
    CompletableFuture<AcquisitionsUnitCollection> future = new VertxCompletableFuture<>(ctx);

    try {
      String endpoint = String.format(GET_UNITS_BY_QUERY, limit, offset, getEndpointWithQuery(query, logger), lang);

      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenApply(jsonUnits -> jsonUnits.mapTo(AcquisitionsUnitCollection.class))
        .thenAccept(future::complete)
        .exceptionally(t -> {
          future.completeExceptionally(t.getCause());
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  CompletableFuture<AcquisitionsUnitMembershipCollection> getAcquisitionsUnitsMemberships(String query, int offset, int limit) {
    CompletableFuture<AcquisitionsUnitMembershipCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String endpoint = String.format(GET_UNITS_MEMBERSHIPS_BY_QUERY, limit, offset, buildQuery(query, logger), lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenApply(jsonUnitsMembership -> jsonUnitsMembership.mapTo(AcquisitionsUnitMembershipCollection.class))
        .thenAccept(future::complete)
        .exceptionally(t -> {
          future.completeExceptionally(t.getCause());
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }
}
