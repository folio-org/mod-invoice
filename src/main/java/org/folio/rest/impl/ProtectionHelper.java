package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.AcqDesiredPermissions.BYPASS_ACQ_UNITS;
import static org.folio.invoices.utils.ErrorCodes.ACQ_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.invoices.utils.HelperUtils.ALL_UNITS_CQL;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.services.AcquisitionsUnitsService.ACQUISITIONS_UNIT_ID;
import static org.folio.utils.UserPermissionsUtil.userHasDesiredPermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.rest.acq.model.units.AcquisitionsUnit;
import org.folio.rest.acq.model.units.AcquisitionsUnitMembership;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.services.AcquisitionsUnitsService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;
import one.util.streamex.StreamEx;

public class ProtectionHelper extends AbstractHelper {

  public static final String ACQUISITIONS_UNIT_IDS = "acqUnitIds";

  public static final String NO_ACQ_UNIT_ASSIGNED_CQL = "cql.allRecords=1 not %s <> []";

  @Autowired
  AcquisitionsUnitsService acquisitionsUnitsService;
  private List<AcquisitionsUnit> fetchedUnits = new ArrayList<>();

  public ProtectionHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  /**
   * This method determines status of operation restriction based on unit IDs from {@link org.folio.rest.jaxrs.model.Invoice}.
   *
   * @param unitIds   list of unit IDs.
   * @param operation type of operation
   * @return completable future completed exceptionally if user does not have rights to perform operation or any unit does not
   * exist; successfully otherwise
   */
  public Future<Void> isOperationRestricted(List<String> unitIds, ProtectedOperationType operation) {
    if (userHasDesiredPermission(BYPASS_ACQ_UNITS, okapiHeaders)) {
      return Future.succeededFuture();
    }
    if (CollectionUtils.isNotEmpty(unitIds)) {
      return getUnitsByIds(unitIds).compose(units -> {
        if (unitIds.size() == units.size()) {
          // In case any unit is "soft deleted", just skip it (refer to MODINVOICE-89)
          List<AcquisitionsUnit> activeUnits = units.stream()
            .filter(unit -> !unit.getIsDeleted())
            .collect(Collectors.toList());

          if (!activeUnits.isEmpty() && Boolean.TRUE.equals(applyMergingStrategy(activeUnits, operation))) {
            return verifyUserIsMemberOfAcqUnits(extractUnitIds(activeUnits), buildRequestContext());
          }
          return succeededFuture(null);
        } else {
          // In case any unit "hard deleted" or never existed by specified uuid
          throw new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(),
            buildUnitsNotFoundError(unitIds, extractUnitIds(units)));
        }
      });
    } else {
      return succeededFuture(null);
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
  public Future<Void> verifyIfUnitsAreActive(List<String> acqUnitIds) {
    if (acqUnitIds.isEmpty()) {
      return succeededFuture(null);
    }

    return getUnitsByIds(acqUnitIds).map(units -> {
      List<String> activeUnitIds = units.stream()
        .filter(unit -> !unit.getIsDeleted())
        .map(AcquisitionsUnit::getId)
        .collect(Collectors.toList());

      if (acqUnitIds.size() != activeUnitIds.size()) {
        throw new HttpException(HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt(), buildUnitsNotFoundError(acqUnitIds, activeUnitIds));
      }
      return null;
    });
  }

  private Error buildUnitsNotFoundError(List<String> expectedUnitIds, List<String> availableUnitIds) {
    List<String> missingUnitIds = ListUtils.subtract(expectedUnitIds, availableUnitIds);
    return ACQ_UNITS_NOT_FOUND.toError().withAdditionalProperty(ACQUISITIONS_UNIT_IDS, missingUnitIds);
  }


  /**
   * This method returns list of {@link AcquisitionsUnit} based on list of unit ids
   *
   * @param unitIds list of unit ids
   * @return list of {@link AcquisitionsUnit}
   */
  private Future<List<AcquisitionsUnit>> getUnitsByIds(List<String> unitIds) {
    // Check if all required units are already available
    List<AcquisitionsUnit> units = fetchedUnits.stream()
      .filter(unit -> unitIds.contains(unit.getId()))
      .distinct()
      .collect(Collectors.toList());

    if (units.size() == unitIds.size()) {
      return succeededFuture(units);
    }

    String query = ALL_UNITS_CQL + " and " + convertIdsToCqlQuery(unitIds);
    return acquisitionsUnitsService.getAcquisitionsUnits(query, 0, Integer.MAX_VALUE, buildRequestContext())
      .map(acquisitionsUnitCollection -> {
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


  Future<String> buildAcqUnitsCqlExprToSearchRecords(String entity) {
    return getAcqUnitIdsForSearch(getCurrentUserId()).map(ids -> {
      if (ids.isEmpty()) {
        return HelperUtils.getNoAcqUnitCQL(entity);
      }

      return String.format("%s or (%s)", convertIdsToCqlQuery(ids, HelperUtils.getAcqUnitIdsQueryParamName(entity), false), HelperUtils.getNoAcqUnitCQL(entity));
    });
  }

  Future<List<String>> getAcqUnitIdsForSearch(String userId) {
    var unitsForUser = getAcqUnitIdsForUser(userId);
    var unitsAllowRead = getOpenForReadAcqUnitIds();
    return Future.join(unitsForUser, unitsAllowRead)
      .map(rcf -> StreamEx.of(unitsForUser.result(), unitsAllowRead.result())
        .flatCollection(strings -> strings)
        .distinct()
        .toList());
  }


  Future<List<String>> getAcqUnitIdsForUser(String userId) {
    return acquisitionsUnitsService.getAcquisitionsUnitsMemberships("userId==" + userId, 0, Integer.MAX_VALUE, buildRequestContext())
      .map(memberships -> {
        List<String> ids = memberships.getAcquisitionsUnitMemberships()
          .stream()
          .map(AcquisitionsUnitMembership::getAcquisitionsUnitId)
          .collect(Collectors.toList());

        logger.debug("getAcqUnitIdsForUser:: User belongs to {} acq units: {}", ids.size(), StringUtils.join(ids, ", "));

        return ids;
      });
  }

  private Future<List<String>> getOpenForReadAcqUnitIds() {
    return acquisitionsUnitsService.getAcquisitionsUnits("protectRead==false", 0, Integer.MAX_VALUE, buildRequestContext())
      .map(units -> {
        List<String> ids = units.getAcquisitionsUnits()
          .stream()
          .map(AcquisitionsUnit::getId)
          .collect(Collectors.toList());

        logger.debug("getOpenForReadAcqUnitIds:: {} acq units with 'protectRead==false' are found: {}", ids.size(), StringUtils.join(ids, ", "));

        return ids;
      });
  }

  private Future<Void> verifyUserIsMemberOfAcqUnits(List<String> unitIds, RequestContext requestContext) {
    String query = String.format("userId==%s AND %s", getCurrentUserId(),
      convertIdsToCqlQuery(unitIds, ACQUISITIONS_UNIT_ID, true));
    return acquisitionsUnitsService.getAcquisitionsUnitsMemberships(query, 0, 0, requestContext)
      .map(unit -> {
        if (unit.getTotalRecords() == 0) {
          throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_PERMISSIONS);
        }
        return null;
      });
  }
}
