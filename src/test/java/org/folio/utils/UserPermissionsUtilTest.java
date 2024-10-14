package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.json.JsonArray;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Invoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class UserPermissionsUtilTest {

  private final Map<String, String> okapiHeaders = new HashMap<>();

  @Test
  @DisplayName("Should not throw exception when approve permission is in position")
  void shouldNotThrowExceptionWhenApprovePermissionIsInPosition() {
    List<String> permissionsList = List.of(
      "invoice.item.approve.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.APPROVED);
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasInvoiceApprovePermission(
      invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
  }

  @Test
  @DisplayName("should throw exception when approve permission is absent")
  void shouldThrowExceptionWhenApprovePermissionIsAbsent() {
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.APPROVED);
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);

    assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasInvoiceApprovePermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
  }

  @Test
  @DisplayName("Should not throw exception when pay permission is in position")
  void shouldNotThrowExceptionWhenPayPermissionIsInPosition() {
    List<String> permissionsList = List.of(
      "invoice.item.pay.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.PAID);
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.APPROVED);

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasInvoicePayPermission(
      invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
  }

  @Test
  @DisplayName("Should throw correct error code when pay permission is absent")
  void shouldThrowCorrectErrorCodeWhenPayPermissionIsAbsent() {
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.PAID);
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.APPROVED);

    var exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasInvoicePayPermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
    assertEquals(exception.getCode(), 403);
  }

  @Test
  @DisplayName("Should not throw exception when cancel permission is in position")
  void shouldNotThrowExceptionWhenCancelPermissionIsInPosition() {
    List<String> permissionsList = List.of(
      "invoice.item.cancel.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.CANCELLED);
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.PAID);

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasInvoiceCancelPermission(
      invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
  }

  @Test
  @DisplayName("Should throw correct error code when cancel permission absent")
  void shouldThrowCorrectErrorCodeWhenCancelPermissionIsAbsent() {
    List<String> permissionsList = Arrays.asList(
      "invoice.item.approve.execute",
      "invoice.item.pay.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.CANCELLED);
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.PAID);

    var exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasInvoiceCancelPermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
    assertEquals(exception.getCode(), 403);
  }

  @Test
  @DisplayName("Should throw correct error code when assign permission is absent")
  void shouldThrowCorrectErrorCodeWhenAssignPermissionIsAbsent() {
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update.execute"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(List.of("12345678"));
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);
    invoiceFromStorage.setAcqUnitIds(new ArrayList<>());

    var exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasAssignPermission(
        invoice.getAcqUnitIds(), okapiHeaders));
    assertEquals(exception.getCode(), 403);
  }

  @Test
  @DisplayName("should not throw exception when assign permission is assigned")
  void shouldNotThrowExceptionWhenAssignPermissionIsAssigned() {

    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update.execute",
      "invoices.acquisitions-units-assignments.assign"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(List.of("12345678"));
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);
    invoiceFromStorage.setAcqUnitIds(new ArrayList<>());

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasAssignPermission(
      invoice.getAcqUnitIds(), okapiHeaders));
  }

  @Test
  @DisplayName("should throw exception when manage permission is absent")
  void shouldThrowExceptionWhenManagePermissionIsAbsent() {
    List<String> permissionsList = List.of(
      "invoices.acquisitions-units-assignments.assign"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(List.of("12345678"));
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);
    invoiceFromStorage.setAcqUnitIds(List.of("6475643839"));

    assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasManagePermission(
        invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders));
  }

  @Test
  @DisplayName("Should not throw exception when manage permission is assigned")
  void shouldNotThrowExceptionWhenManagePermissionIsAssigned() {
    // Create a list of permissions
    List<String> permissionsList = Arrays.asList(
      "invoices.acquisitions-units-assignments.manage",
      "invoices.acquisitions-units-assignments.assign"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(List.of("12345678"));
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);
    invoiceFromStorage.setAcqUnitIds(List.of("6475643839"));

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasManagePermission(
      invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders));
  }

  @Test
  @DisplayName("should throw exception when fiscal year update permission is absent")
  void shouldThrowExceptionWhenFiscalYearUpdatePermissionIsAbsent() {
    List<String> permissionsList = List.of(
      "invoices.acquisitions-units-assignments.assign"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setFiscalYearId("2006");
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);
    invoiceFromStorage.setFiscalYearId("2005");

    assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasFiscalYearUpdatePermission(
        invoice.getFiscalYearId(), invoiceFromStorage.getFiscalYearId(), okapiHeaders));
  }

  @Test
  @DisplayName("Should not throw exception when fiscal year update permission is assigned")
  void shouldNotThrowExceptionWhenFiscalYearUpdatePermissionIsAssigned() {
    // Create a list of permissions
    List<String> permissionsList = Arrays.asList(
      "invoices.fiscal-year.update.execute",
      "invoices.acquisitions-units-assignments.assign"
    );

    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);

    Invoice invoice = new Invoice();
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setFiscalYearId("2006");
    Invoice invoiceFromStorage = new Invoice();
    invoiceFromStorage.setStatus(Invoice.Status.REVIEWED);
    invoiceFromStorage.setFiscalYearId("2005");

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasManagePermission(
      invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders));
  }
}
