package org.folio.rest.impl;

import static org.folio.invoices.utils.ErrorCodes.ACQ_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.ALL_UNITS_CQL;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_MEMBERSHIPS;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_UNITS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.rest.acq.model.units.AcquisitionsUnit;
import org.folio.rest.acq.model.units.AcquisitionsUnitCollection;
import org.folio.rest.acq.model.units.AcquisitionsUnitMembership;
import org.folio.rest.acq.model.units.AcquisitionsUnitMembershipCollection;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.Context;

public class ProtectionHelper extends AbstractHelper {

  public static final String ACQUISITIONS_UNIT_ID = "acquisitionsUnitId";
  public static final String ACQUISITIONS_UNIT_IDS = "acqUnitIds";
  public static final String NO_ACQ_UNIT_ASSIGNED_CQL = "cql.allRecords=1 not %s <> []";
  static final String GET_UNITS_BY_QUERY = resourcesPath(ACQUISITIONS_UNITS) + SEARCH_PARAMS;
  static final String GET_UNITS_MEMBERSHIPS_BY_QUERY = resourcesPath(ACQUISITIONS_MEMBERSHIPS) + SEARCH_PARAMS;

  private List<AcquisitionsUnit> fetchedUnits = new ArrayList<>();

  public ProtectionHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
}

  /**
   * This method determines status of operation restriction based on unit IDs from {@link Invoice}.
   *
   * @param unitIds   list of unit IDs.
   * @param operation type of operation
   * @return completable future completed exceptionally if user does not have rights to perform operation or any unit does not
   *         exist; successfully otherwise
   */
  public CompletableFuture<Void> isOperationRestricted(List<String> unitIds, ProtectedOperationType operation) {
    if (CollectionUtils.isNotEmpty(unitIds)) {
      return getUnitsByIds(unitIds).thenCompose(units -> {
        if (unitIds.size() == units.size()) {
          // In case any unit is "soft deleted", just skip it (refer to MODINVOICE-89)
          List<AcquisitionsUnit> activeUnits = units.stream()
            .filter(unit -> !unit.getIsDeleted())
            .collect(Collectors.toList());

          if (!activeUnits.isEmpty() && Boolean.TRUE.equals(applyMergingStrategy(activeUnits, operation))) {
            return verifyUserIsMemberOfAcqUnits(extractUnitIds(activeUnits));
          }
          return CompletableFuture.completedFuture(null);
        } else {
          // In case any unit "hard deleted" or never existed by specified uuid
          throw new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(),
              buildUnitsNotFoundError(unitIds, extractUnitIds(units)));
        }
      });
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  private List<String> extractUnitIds(List<AcquisitionsUnit> activeUnits) {
    return activeUnits.stream().map(AcquisitionsUnit::getId).collect(Collectors.toList());
  }

  /**
   * Verifies if all acquisition units exist and active based on passed ids
   *
   * @param acqUnitIds list of unit IDs.
   * @return completable future completed successfully if all units exist and active or exceptionally otherwise
   */
  public CompletableFuture<Void> verifyIfUnitsAreActive(List<String> acqUnitIds) {
    if (acqUnitIds.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    return getUnitsByIds(acqUnitIds).thenAccept(units -> {
      List<String> activeUnitIds = units.stream()
        .filter(unit -> !unit.getIsDeleted())
        .map(AcquisitionsUnit::getId)
        .collect(Collectors.toList());

      if (acqUnitIds.size() != activeUnitIds.size()) {
        throw new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(), buildUnitsNotFoundError(acqUnitIds, activeUnitIds));
      }
    });
  }

  private Error buildUnitsNotFoundError(List<String> expectedUnitIds, List<String> availableUnitIds) {
    List<String> missingUnitIds = ListUtils.subtract(expectedUnitIds, availableUnitIds);
    return ACQ_UNITS_NOT_FOUND.toError().withAdditionalProperty(ACQUISITIONS_UNIT_IDS, missingUnitIds);
  }
  
  /**
   * Check whether the user is a member of at least one group from which the related invoice belongs.
   *
   * @return list of unit ids associated with user.
   */
  private CompletableFuture<Void> verifyUserIsMemberOfAcqUnits(List<String> unitIds) {
    String query = String.format("userId==%s AND %s", getCurrentUserId(),
        convertIdsToCqlQuery(unitIds, ACQUISITIONS_UNIT_ID, true));
    return getAcquisitionsUnitsMemberships(query, 0, 0)
      .thenAccept(unit -> {
        if (unit.getTotalRecords() == 0) {
          throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_PERMISSIONS);
        }
      });
  }

  /**
   * This method returns list of {@link AcquisitionsUnit} based on list of unit ids
   * 
   * @param unitIds list of unit ids
   *
   * @return list of {@link AcquisitionsUnit}
   */
  private CompletableFuture<List<AcquisitionsUnit>> getUnitsByIds(List<String> unitIds) {
    // Check if all required units are already available
    List<AcquisitionsUnit> units = fetchedUnits.stream()
      .filter(unit -> unitIds.contains(unit.getId()))
      .distinct()
      .collect(Collectors.toList());

    if (units.size() == unitIds.size()) {
      return CompletableFuture.completedFuture(units);
    }

    String query = ALL_UNITS_CQL + " and " + convertIdsToCqlQuery(unitIds);
    return getAcquisitionsUnits(query, 0, Integer.MAX_VALUE).thenApply(acquisitionsUnitCollection -> {
      List<AcquisitionsUnit> acquisitionsUnits = acquisitionsUnitCollection.getAcquisitionsUnits();
      fetchedUnits.addAll(acquisitionsUnits);
      return acquisitionsUnits;
    });
  }

  /**
   * This method returns operation protection resulted status based on list of units with using least restrictive wins strategy.
   *
   * @param units list of {@link AcquisitionsUnit}.
   * @return true if operation is protected, otherwise - false.
   */
  private Boolean applyMergingStrategy(List<AcquisitionsUnit> units, ProtectedOperationType operation) {
    return units.stream().allMatch(operation::isProtected);
  }

  CompletableFuture<AcquisitionsUnitCollection> getAcquisitionsUnits(String query, int offset, int limit) {
    CompletableFuture<AcquisitionsUnitCollection> future = new VertxCompletableFuture<>(ctx);

    try {
      String endpoint = String.format(ProtectionHelper.GET_UNITS_BY_QUERY, limit, offset, getEndpointWithQuery(query, logger), lang);

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

  CompletableFuture<String> buildAcqUnitsCqlExprToSearchRecords(String entity) {
    return getAcqUnitIdsForSearch().thenApply(ids -> {
      if (ids.isEmpty()) {
        return HelperUtils.getNoAcqUnitCQL(entity);
      }

      return String.format("%s or (%s)", convertIdsToCqlQuery(ids, HelperUtils.getAcqUnitIdsQueryParamName(entity), false), HelperUtils.getNoAcqUnitCQL(entity));
    });
  }

  CompletableFuture<List<String>> getAcqUnitIdsForSearch() {
    return getAcqUnitIdsForUser(getCurrentUserId())
      .thenCombine(getOpenForReadAcqUnitIds(), (unitsForUser, unitsAllowRead) -> StreamEx.of(unitsForUser, unitsAllowRead)
        .flatCollection(strings -> strings)
        .distinct()
        .toList());
  }

  CompletableFuture<AcquisitionsUnitMembershipCollection> getAcquisitionsUnitsMemberships(String query, int offset, int limit) {
    CompletableFuture<AcquisitionsUnitMembershipCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String endpoint = String.format(ProtectionHelper.GET_UNITS_MEMBERSHIPS_BY_QUERY, limit, offset, getEndpointWithQuery(query, logger), lang);
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

  CompletableFuture<List<String>> getAcqUnitIdsForUser(String userId) {
    return getAcquisitionsUnitsMemberships("userId==" + userId, 0, Integer.MAX_VALUE)
      .thenApply(memberships -> {
        List<String> ids = memberships.getAcquisitionsUnitMemberships()
          .stream()
          .map(AcquisitionsUnitMembership::getAcquisitionsUnitId)
          .collect(Collectors.toList());

        if (logger.isDebugEnabled()) {
          logger.debug("User belongs to {} acq units: {}", ids.size(), StreamEx.of(ids).joining(", "));
        }

        return ids;
      });
  }

  private CompletableFuture<List<String>> getOpenForReadAcqUnitIds() {
    return getAcquisitionsUnits("protectRead==false", 0, Integer.MAX_VALUE).thenApply(units -> {
      List<String> ids = units.getAcquisitionsUnits()
        .stream()
        .map(AcquisitionsUnit::getId)
        .collect(Collectors.toList());

      if (logger.isDebugEnabled()) {
        logger.debug("{} acq units with 'protectRead==false' are found: {}", ids.size(), StreamEx.of(ids).joining(", "));
      }

      return ids;
    });
  }
}
