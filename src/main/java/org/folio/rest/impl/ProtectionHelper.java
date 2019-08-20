package org.folio.rest.impl;

import static org.folio.invoices.utils.ErrorCodes.ACQ_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.rest.acq.model.AcquisitionsUnit;
import org.folio.rest.acq.model.AcquisitionsUnitCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;

import io.vertx.core.Context;

public class ProtectionHelper extends AbstractHelper {

  public static final String ACQUISITIONS_UNIT_ID = "acquisitionsUnitId";
  public static final String ACQUISITIONS_UNIT_IDS = "acqUnitIds";
  private AcquisitionsUnitsHelper acquisitionsUnitsHelper;

  public ProtectionHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
    acquisitionsUnitsHelper = new AcquisitionsUnitsHelper(httpClient, okapiHeaders, ctx, lang);
  }

  /**
   * This method determines status of operation restriction based on unit IDs from {@link Invoice}.
   * 
   * @param unitIds list of unit IDs.
   *
   * @throws HttpException if user hasn't permissions or units not found
   */
  public CompletableFuture<Void> isOperationRestricted(List<String> unitIds, ProtectedOperationType operation) {
    return isOperationRestricted(unitIds, Collections.singleton(operation));
  }

  /**
   * This method determines status of operation restriction based on unit IDs.
   * 
   * @param unitIds list of unit IDs.
   *
   * @throws HttpException if user hasn't permissions or units not found
   */
  public CompletableFuture<Void> isOperationRestricted(List<String> unitIds, Set<ProtectedOperationType> operations) {
    if (CollectionUtils.isNotEmpty(unitIds)) {
      return getUnitsByIds(unitIds).thenCompose(units -> {
        if (unitIds.size() == units.size()) {
          if (applyMergingStrategy(units, operations)) {
            return verifyUserIsMemberOfAcqUnits(unitIds);
          }
          return CompletableFuture.completedFuture(null);
        } else {
          Error error = ACQ_UNITS_NOT_FOUND.toError();
          unitIds.removeAll(units.stream().map(AcquisitionsUnit::getId).collect(Collectors.toSet()));
          error.getParameters().add(new Parameter().withKey(ACQUISITIONS_UNIT_IDS).withValue(unitIds.toString()));
          throw new HttpException(HttpStatus.HTTP_VALIDATION_ERROR.toInt(), error);
        }
      });
    } else {
      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * Check whether the user is a member of at least one group from which the related invoice belongs.
   *
   * @return list of unit ids associated with user.
   */
  private CompletableFuture<Void> verifyUserIsMemberOfAcqUnits(List<String> unitIdsAssignedToInvoice) {
    String query = String.format("userId==%s AND %s", getCurrentUserId(),
        convertIdsToCqlQuery(unitIdsAssignedToInvoice, ACQUISITIONS_UNIT_ID, true));
    return acquisitionsUnitsHelper.getAcquisitionsUnitsMemberships(query, 0, 0)
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
    String query = convertIdsToCqlQuery(unitIds);
    return acquisitionsUnitsHelper.getAcquisitionsUnits(query, 0, Integer.MAX_VALUE)
      .thenApply(AcquisitionsUnitCollection::getAcquisitionsUnits);
  }

  /**
   * This method returns operation protection resulted status based on list of units with using least restrictive wins strategy.
   *
   * @param units list of {@link AcquisitionsUnit}.
   * @return true if operation is protected, otherwise - false.
   */
  private Boolean applyMergingStrategy(List<AcquisitionsUnit> units, Set<ProtectedOperationType> operations) {
    return units.stream()
      .allMatch(unit -> operations.stream()
        .anyMatch(operation -> operation.isProtected(unit)));
  }

}
