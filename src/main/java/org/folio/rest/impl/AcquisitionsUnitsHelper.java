package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.buildQuery;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_MEMBERSHIPS;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_UNITS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.AcquisitionsUnit;
import org.folio.rest.jaxrs.model.AcqUnitCollection;
import org.folio.rest.jaxrs.model.AcquisitionsUnitMembership;
import org.folio.rest.jaxrs.model.AcqUnitMembershipCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;

public class AcquisitionsUnitsHelper extends AbstractHelper {
  static final String ACQUISITIONS_UNIT_IDS = "acqUnitIds";
  static final String NO_ACQ_UNIT_ASSIGNED_CQL = "cql.allRecords=1 not " + ACQUISITIONS_UNIT_IDS + " <> []";
  private static final String GET_UNITS_BY_QUERY = resourcesPath(ACQUISITIONS_UNITS) + SEARCH_PARAMS;
  private static final String GET_UNITS_MEMBERSHIPS_BY_QUERY = resourcesPath(ACQUISITIONS_MEMBERSHIPS) + SEARCH_PARAMS;

  public AcquisitionsUnitsHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  AcquisitionsUnitsHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
  }

  CompletableFuture<AcqUnitCollection> getAcquisitionsUnits(String query, int offset, int limit) {
    CompletableFuture<AcqUnitCollection> future = new VertxCompletableFuture<>(ctx);

    try {
      String endpoint = String.format(GET_UNITS_BY_QUERY, limit, offset, getEndpointWithQuery(query, logger), lang);

      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenApply(jsonUnits -> jsonUnits.mapTo(AcqUnitCollection.class))
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
  
  CompletableFuture<AcqUnitMembershipCollection> getAcquisitionsUnitsMemberships(String query, int offset, int limit) {
    CompletableFuture<AcqUnitMembershipCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String endpoint = String.format(GET_UNITS_MEMBERSHIPS_BY_QUERY, limit, offset, buildQuery(query, logger), lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenApply(jsonUnitsMembership -> jsonUnitsMembership.mapTo(AcqUnitMembershipCollection.class))
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
