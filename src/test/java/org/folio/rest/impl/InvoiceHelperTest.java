package org.folio.rest.impl;

import static org.folio.ApiTestSuite.mockPort;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.List;
import org.folio.utils.UserPermissionsUtil;
import io.vertx.core.json.JsonArray;

import org.folio.rest.RestConstants;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.folio.invoices.rest.exceptions.HttpException;
import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

@DisplayName("InvoiceHelper should :")
class InvoiceHelperTest extends ApiTestBase {

  private static final String PO_LINE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  private static final String EXISTING_PO_LINE_ID = "c2755a78-2f8d-47d0-a218-059a9b7391b4";

  private Context context;
  private Map<String, String> okapiHeaders;

  RestClient restClient = new RestClient();

  @BeforeEach
  public void setUp() {
    super.setUp();
    context = Vertx.vertx().getOrCreateContext();
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(RestConstants.OKAPI_URL, "http://localhost:" + mockPort);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
  }

   @Test
  @DisplayName("should not throw exception when approve permission is in position")
  void shouldNotThrowExceptionWhenApprovePermissionIsInPosition(){
      List<String> permissionsList = Arrays.asList(
        "invoice.item.approve",
        "invoice.invoices.item.put",
        "invoices.acquisitions-units-assignments.manage",
        "invoices.fiscal-year.update"
      );

      JsonArray permissionsArray = new JsonArray(permissionsList);
      String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
      okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
      Invoice invoice = new Invoice();
      invoice.setId("123456783423425");
      invoice.setStatus(Invoice.Status.REVIEWED);
      Invoice invoiceFromStorage = new Invoice();
      invoice.setId("123456783423425");
      invoice.setStatus(Invoice.Status.APPROVED);

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasInvoiceApprovePermission(
      invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
  }

  

  @Test
  @DisplayName("should throw exception when approve permission is absent")
  void shouldThrowExceptionWhenApprovePermissionIsAbsent(){

    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);

    assertThrows(HttpException.class, () -> {
      UserPermissionsUtil.verifyUserHasInvoiceApprovePermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders);
    });
  }

  @Test
  @DisplayName("should not throw exception when pay permission is in position")
  void shouldNotThrowExceptionWhenPayPermissionIsInPosition(){

    List<String> permissionsList = Arrays.asList(
      "invoice.item.pay",
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasInvoicePayPermission(
      invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
  }
  
 @Test
  @DisplayName("should throw exception when pay permission is absent")
  void shouldThrowExceptionWhenPayPermissionIsAbsent(){
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);

    assertThrows(HttpException.class, () -> {
      UserPermissionsUtil.verifyUserHasInvoicePayPermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders);
    });
  }

@Test
  @DisplayName("should throw correct error code when pay permission is absent")
  void shouldThrowCorrectErrorCodeWhenPayPermissionIsAbsent(){
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.PAID);

    HttpException exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasInvoicePayPermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
    assertEquals(org.folio.HttpStatus.HTTP_FORBIDDEN.toInt(), 403);
  }

  @Test
  @DisplayName("should throw correct error code when approve permission absent")
  void shouldThrowCorrectErrorCodeWhenApprovePermissionIsAbsent(){

    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);

    HttpException exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasInvoiceApprovePermission(
        invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders));
    assertEquals(org.folio.HttpStatus.HTTP_FORBIDDEN.toInt(), 403);
  }

  @Test
  @DisplayName("should throw correct error code when assign permission is absent")
  void shouldThrowCorrectErrorCodeWhenAssignPermissionIsAbsent(){

    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    LinkedList <String> inoviceAcqID = new LinkedList<String>();
    LinkedList <String> inoviceFromStorageAcqID = new LinkedList<String>();

    inoviceAcqID.add("12345678");
    inoviceFromStorageAcqID.add("6475643839");
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(inoviceAcqID);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    invoiceFromStorage.setAcqUnitIds(inoviceFromStorageAcqID);

    HttpException exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasAssignPermission(
        invoice.getAcqUnitIds(), okapiHeaders));
    assertEquals(org.folio.HttpStatus.HTTP_FORBIDDEN.toInt(), 403);
  }

 @Test
  @DisplayName("should throw exception when assign permission Is absent")
  void shouldThrowExceptionWhenAssignPermissionIsAbsent(){

    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    LinkedList <String> inoviceAcqID = new LinkedList<String>();
    LinkedList <String> inoviceFromStorageAcqID = new LinkedList<String>();

    inoviceAcqID.add("12345678");
    inoviceFromStorageAcqID.add("6475643839");
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(inoviceAcqID);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    invoiceFromStorage.setAcqUnitIds(inoviceFromStorageAcqID);

    assertThrows(HttpException.class, () -> {
      UserPermissionsUtil.verifyUserHasAssignPermission(
        invoice.getAcqUnitIds(), okapiHeaders);
    });
  }
    
 @Test
  @DisplayName("should not throw exception when assign permission is assigned")
  void shouldNotThrowExceptionWhenAssignPermissionIsAssigned(){

    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update",
      "invoices.acquisitions-units-assignments.assign"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    LinkedList <String> inoviceAcqID = new LinkedList<String>();
    LinkedList <String> inoviceFromStorageAcqID = new LinkedList<String>();

    inoviceAcqID.add("12345678");
    inoviceFromStorageAcqID.add("6475643839");
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(inoviceAcqID);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    invoiceFromStorage.setAcqUnitIds(inoviceFromStorageAcqID);

    assertDoesNotThrow(() -> UserPermissionsUtil.verifyUserHasAssignPermission(
      invoice.getAcqUnitIds(), okapiHeaders));
  }

  @Test
  @DisplayName("should not throw exception when manage permission is assigned")
  void shouldNotThrowExceptionWhenManagePermissionIsAssigned(){

    // Create a list of permissions
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.acquisitions-units-assignments.manage",
      "invoices.fiscal-year.update",
      "invoices.acquisitions-units-assignments.assign"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    LinkedList <String> inoviceAcqID = new LinkedList<String>();
    LinkedList <String> inoviceFromStorageAcqID = new LinkedList<String>();

    inoviceAcqID.add("12345678");
    inoviceFromStorageAcqID.add("6475643839");
    // okapiHeaders.put(X_OKAPI_PERMISSIONS.getName(), X_OKAPI_PERMISSIONS.getValue());
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(inoviceAcqID);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    invoiceFromStorage.setAcqUnitIds(inoviceFromStorageAcqID);

    assertDoesNotThrow(() -> UserPermissionsUtil. verifyUserHasManagePermission(
      invoice.getAcqUnitIds(),invoiceFromStorage.getAcqUnitIds(), okapiHeaders));
  }
    
@Test
  @DisplayName("should throw exception when manage permission is absent")
  void shouldThrowExceptionWhenManagePermissionIsAbsent(){
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.fiscal-year.update",
      "invoices.acquisitions-units-assignments.assign"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    LinkedList <String> inoviceAcqID = new LinkedList<String>();
    LinkedList <String> inoviceFromStorageAcqID = new LinkedList<String>();
    inoviceAcqID.add("12345678");
    inoviceFromStorageAcqID.add("6475643839");
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(inoviceAcqID);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    invoiceFromStorage.setAcqUnitIds(inoviceFromStorageAcqID);

    assertThrows(HttpException.class, () -> {
      UserPermissionsUtil. verifyUserHasManagePermission(
        invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders);
    });
  }
    
  @Test
  @DisplayName("should Throw Correct Error Code When Manage Permission Is Absent")
  void shouldThrowCorrectErrorCodeWhenManagePermissionIsAbsent(){
    List<String> permissionsList = Arrays.asList(
      "invoice.invoices.item.put",
      "invoices.fiscal-year.update",
      "invoices.acquisitions-units-assignments.assign"
    );

    JsonArray permissionsArray = new JsonArray(permissionsList);
    String permissionsJsonArrayString = new JsonArray(permissionsList).encode();
    okapiHeaders.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissionsJsonArrayString);
    LinkedList <String> inoviceAcqID = new LinkedList<String>();
    LinkedList <String> inoviceFromStorageAcqID = new LinkedList<String>();

    inoviceAcqID.add("12345678");
    inoviceFromStorageAcqID.add("6475643839");
    Invoice invoice = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.REVIEWED);
    invoice.setAcqUnitIds(inoviceAcqID);
    Invoice invoiceFromStorage = new Invoice();
    invoice.setId("123456783423425");
    invoice.setStatus(Invoice.Status.APPROVED);
    invoiceFromStorage.setAcqUnitIds(inoviceFromStorageAcqID);

    HttpException exception = assertThrows(HttpException.class, () ->
      UserPermissionsUtil.verifyUserHasManagePermission(
        invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders));
    assertEquals(org.folio.HttpStatus.HTTP_FORBIDDEN.toInt(), 403);
  }
    
  @Test
  @DisplayName("not decide to update status of POLines with ONGOING status")
  void shouldReturnFalseWhenCompositeCheckingForUpdatePoLinePaymentStatusIsOngoing() {
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context);

    CompositePoLine ongoingCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.ONGOING);

    CompositePoLine fullyPaidCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.FULLY_PAID);

    Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus = new HashMap<>() {{
      put(ongoingCompositePoLine, CompositePoLine.PaymentStatus.ONGOING);
      put(fullyPaidCompositePoLine, CompositePoLine.PaymentStatus.FULLY_PAID);
    }};

    assertFalse(invoiceHelper.isPaymentStatusUpdateRequired(compositePoLinesWithStatus, ongoingCompositePoLine));
  }
    
  @Test
  @DisplayName("decide to update status of POLines with different statuses")
  void shouldReturnTrueWhenCompositeCheckingForUpdatePoLinePaymentStatusIsDifferentValues() {
    InvoiceHelper invoiceHelper = new InvoiceHelper(okapiHeaders, context);

    CompositePoLine ongoingCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.ONGOING);

    CompositePoLine fullyPaidCompositePoLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTING_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.FULLY_PAID);

    Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus = new HashMap<>() {{
      put(ongoingCompositePoLine, CompositePoLine.PaymentStatus.ONGOING);
      put(fullyPaidCompositePoLine, CompositePoLine.PaymentStatus.PENDING);
    }};

    assertTrue(invoiceHelper.isPaymentStatusUpdateRequired(compositePoLinesWithStatus, fullyPaidCompositePoLine));
  }

}
