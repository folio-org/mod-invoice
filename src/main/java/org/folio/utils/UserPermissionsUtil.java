package org.folio.utils;

import static org.folio.invoices.utils.AcqDesiredPermissions.ASSIGN;
import static org.folio.invoices.utils.AcqDesiredPermissions.MANAGE;
import static org.folio.invoices.utils.AcqDesiredPermissions.FISCAL_YEAR_UPDATE;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_FISCAL_YEAR_UPDATE_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.AcqDesiredPermissions;

import io.vertx.core.json.JsonArray;

public final class UserPermissionsUtil {
  public static final String OKAPI_HEADER_PERMISSIONS = "X-Okapi-Permissions";

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

  public static void verifyPaidPermission(List<String> newAcqUnitIds, List<String> currentAcqUnitIds, String acqSataus,
                                                   Map<String, String> okapiHeaders) {
    Set<String> newAcqUnits = new HashSet<>(CollectionUtils.emptyIfNull(newAcqUnitIds));
    Set<String> acqUnitsFromStorage = new HashSet<>(CollectionUtils.emptyIfNull(currentAcqUnitIds));
    // this varible is used to check if the inovice appovred 
    Set<String> acqStatus = new HashSet<>(CollectionUtils.emptyIfNull(status));
    // check if the invoice is approved. if the invoice had not been approved, the function will throw a expection. otherwise the function will check the if the user has permission. 
    if (isApporved(acqStatus)){
    throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), INVOICE_HAD_NOT_BEEN_APPROVED);
      
    }
     else if (isManagePermissionRequired(newAcqUnits, acqUnitsFromStorage) && isUserDoesNotHaveDesiredPermission(MANAGE, okapiHeaders)) {
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_ACQ_PERMISSIONS);
    }
  }

    /**
   * The method checks if list of acquisition units to which the invoice is assigned is changed, if yes, then check that if the user
   * has desired permission to approve acquisition units assignments
   *
   * @throws HttpException    if user does not have manage permission
   * @param newAcqUnitIds     list of acquisition units coming from request
   * @param currentAcqUnitIds list of acquisition units from storage
   */

  public static void verifyApprovalPermission(List<String> newAcqUnitIds, List<String> currentAcqUnitIds,
                                                   Map<String, String> okapiHeaders) {
    Set<String> newAcqUnits = new HashSet<>(CollectionUtils.emptyIfNull(newAcqUnitIds));
    Set<String> acqUnitsFromStorage = new HashSet<>(CollectionUtils.emptyIfNull(currentAcqUnitIds));

    if (isManagePermissionRequired(newAcqUnits, acqUnitsFromStorage) && isUserDoesNotHaveDesiredPermission(MANAGE, okapiHeaders)) {
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_ACQ_PERMISSIONS);
    }
  }


  /**
   * The method checks whether the user has the desired permission to update the fiscal year.
   *
   * @throws HttpException if user does not have fiscal year update permission
   * @param newFiscalYearId     fiscal year id coming from request
   * @param fiscalYearIdFromStorage fiscal year id from storage
   */
  public static void verifyUserHasFiscalYearUpdatePermission(String newFiscalYearId, String fiscalYearIdFromStorage, Map<String, String> okapiHeaders) {
    if (isFiscalYearUpdated(newFiscalYearId, fiscalYearIdFromStorage) && isUserDoesNotHaveDesiredPermission(FISCAL_YEAR_UPDATE, okapiHeaders)) {
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_FISCAL_YEAR_UPDATE_PERMISSIONS);
    }
  }

  private static boolean isFiscalYearUpdated(String newFiscalYearId, String fiscalYearIdFromStorage) {
    return ObjectUtils.notEqual(newFiscalYearId, fiscalYearIdFromStorage);
  }

  private static boolean isManagePermissionRequired(Set<String> newAcqUnits, Set<String> acqUnitsFromStorage) {
    return !CollectionUtils.isEqualCollection(newAcqUnits, acqUnitsFromStorage);
  }

    /**
   * The method checks whether the user has the desired permission to update the fiscal year.
   *
   * @throws HttpException if user does not have fiscal year update permission
   * @param newFiscalYearId     fiscal year id coming from request
   * @param fiscalYearIdFromStorage fiscal year id from storage
   */

  private static boolean isApporved(String newStatus) {
    return !CollectionUtils.isEqualCollection(newStatus, "Approved");
  }

}
