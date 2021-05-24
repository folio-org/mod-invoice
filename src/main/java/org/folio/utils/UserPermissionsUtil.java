package org.folio.utils;

import static org.folio.invoices.utils.AcqDesiredPermissions.ASSIGN;
import static org.folio.invoices.utils.AcqDesiredPermissions.MANAGE;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_PERMISSIONS;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.AcqDesiredPermissions;

import io.vertx.core.json.JsonArray;

public final class UserPermissionsUtil {
  private static final String EMPTY_ARRAY = "[]";

  private UserPermissionsUtil(){

  }

  public static void verifyUserHasAssignPermission(List<String> acqUnitIds, Map<String, String> okapiHeaders) {
    if (CollectionUtils.isNotEmpty(acqUnitIds) && isUserDoesNotHaveDesiredPermission(ASSIGN, okapiHeaders)){
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_ACQ_PERMISSIONS);
    }
  }

  public static boolean isUserDoesNotHaveDesiredPermission(AcqDesiredPermissions acqPerm, Map<String, String> okapiHeaders) {
    return !getProvidedPermissions(okapiHeaders).contains(acqPerm.getPermission());
  }

  public static List<String> getProvidedPermissions(Map<String, String> okapiHeaders) {
    return new JsonArray(okapiHeaders.getOrDefault(OKAPI_HEADER_PERMISSIONS, EMPTY_ARRAY)).stream().
      map(Object::toString)
      .collect(Collectors.toList());
  }

  /**
   * The method checks if list of acquisition units to which the invoice is assigned is changed, if yes, then check that if the user
   * has desired permission to manage acquisition units assignments
   *
   * @throws HttpException if user does not have manage permission
   * @param newAcqUnitIds     list of acquisition units coming from request
   * @param currentAcqUnitIds list of acquisition units from storage
   */
  public static void verifyUserHasManagePermission(List<String> newAcqUnitIds, List<String> currentAcqUnitIds,
                                                   Map<String, String> okapiHeaders) {
    Set<String> newAcqUnits = new HashSet<>(CollectionUtils.emptyIfNull(newAcqUnitIds));
    Set<String> acqUnitsFromStorage = new HashSet<>(CollectionUtils.emptyIfNull(currentAcqUnitIds));

    if (isManagePermissionRequired(newAcqUnits, acqUnitsFromStorage) && isUserDoesNotHaveDesiredPermission(MANAGE, okapiHeaders)) {
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_ACQ_PERMISSIONS);
    }
  }

  private static boolean isManagePermissionRequired(Set<String> newAcqUnits, Set<String> acqUnitsFromStorage) {
    return !CollectionUtils.isEqualCollection(newAcqUnits, acqUnitsFromStorage);
  }

}