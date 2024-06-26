package org.folio.rest.impl.protection;

import static io.vertx.core.json.Json.encodePrettily;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.AcqDesiredPermissions.BYPASS_ACQ_UNITS;
import static org.folio.invoices.utils.ErrorCodes.ACQ_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_PATH;
import static org.folio.utils.UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;

import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.http.Headers;

import java.util.List;

@ExtendWith(VertxExtension.class)
public class LinesProtectionTest extends ProtectedEntityTestBase {

  private static final Logger logger = LogManager.getLogger(LinesProtectionTest.class);

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "CREATE",
    "UPDATE",
    "DELETE"
  })
  public void testOperationWithNonExistedUnits(ProtectedOperations operation) {
    logger.info("=== Invoice-lines protection: Test corresponding record contains non-existent units - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID);
    Errors errors = operation.process(INVOICE_LINES_PATH, encodePrettily(prepareInvoiceLine(NON_EXISTENT_UNITS)),
      headers, APPLICATION_JSON, HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt()).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(ACQ_UNITS_NOT_FOUND.getCode()));

    // Verify number of sub-requests
    validateNumberOfRequests(1, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "CREATE",
    "UPDATE",
    "DELETE"
  })
  public void testOperationWithAllowedUnits(ProtectedOperations operation) {
    logger.info(
      "=== Invoice-lines protection: Test corresponding record has units allowed operation - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID);
    operation.process(INVOICE_LINES_PATH, encodePrettily(prepareInvoiceLine(NOT_PROTECTED_UNITS)), headers,
      operation.getContentType(), operation.getCode());

    validateNumberOfRequests(1, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "CREATE",
    "UPDATE",
    "DELETE"
  })
  public void testWithRestrictedUnitsAndAllowedUser(ProtectedOperations operation) {
    logger.info(
      "=== Invoice-lines protection: Test corresponding record has units, units protect operation, user is member of order's units - expecting of calls to Units, Memberships APIs and allowance of operation ===");

    operation.process(INVOICE_LINES_PATH, encodePrettily(prepareInvoiceLine(PROTECTED_UNITS)),
      prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_RECORD), operation.getContentType(),
      operation.getCode());

    validateNumberOfRequests(1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "CREATE",
    "UPDATE",
    "DELETE"
  })
  public void testWithProtectedUnitsAndForbiddenUser(ProtectedOperations operation) {
    logger.info("=== Invoice-lines protection: Test corresponding record has units, units protect operation, user isn't member of order's units - expecting of calls to Units, Memberships APIs and restriction of operation ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_RECORD);
    Errors errors = operation.process(INVOICE_LINES_PATH, encodePrettily(prepareInvoiceLine(PROTECTED_UNITS)),
      headers, APPLICATION_JSON, HttpStatus.HTTP_FORBIDDEN.toInt()).as(Errors.class);
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_PERMISSIONS.getCode()));

    validateNumberOfRequests(1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "CREATE",
    "UPDATE",
    "DELETE"
  })
  public void testOperationWithUnprocessableBadUnits(ProtectedOperations operation) {
    logger.info(
      "=== Invoice-lines protection: Test corresponding record contains unprocessable bad units - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID);

    Errors errors = operation
      .process(INVOICE_LINES_PATH, encodePrettily(prepareInvoiceLine(BAD_UNITS)), headers, APPLICATION_JSON,
        HttpStatus.HTTP_BAD_REQUEST.toInt())
      .as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors()
      .get(0)
      .getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
    // Verify number of sub-requests
    validateNumberOfRequests(0, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "UPDATE",
    "READ"
  })
  void testBypassAcqUnitChecks(ProtectedOperations operation) {
    Header permissionHeader = new Header(OKAPI_HEADER_PERMISSIONS,
      new JsonArray(List.of(BYPASS_ACQ_UNITS.getPermission())).encode());
    Headers headers = new Headers(X_OKAPI_TENANT, permissionHeader, X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_RECORD);
    operation.process(INVOICE_LINES_PATH, encodePrettily(prepareInvoiceLine(PROTECTED_UNITS)),
      headers, operation.getContentType(), operation.getCode());

    validateNumberOfRequests(0, 0);
  }

  @Test
  public void testBypassGetCollectionWithQuery() {
    Header permissionHeader = new Header(OKAPI_HEADER_PERMISSIONS,
      new JsonArray(List.of(BYPASS_ACQ_UNITS.getPermission())).encode());
    Headers headers = new Headers(X_OKAPI_TENANT, permissionHeader, X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_RECORD);
    String cql = String.format("%s==%s", INVOICE_ID, APPROVED_INVOICE_ID);
    String endpointQuery = String.format("%s?query=%s", INVOICE_LINES_PATH, cql);

    verifyGet(endpointQuery, headers, APPLICATION_JSON, 200);

    validateNumberOfRequests(0, 0);
  }

  @Test
  public void testBypassGetCollectionWithoutQuery() {
    Header permissionHeader = new Header(OKAPI_HEADER_PERMISSIONS,
      new JsonArray(List.of(BYPASS_ACQ_UNITS.getPermission())).encode());
    Headers headers = new Headers(X_OKAPI_TENANT, permissionHeader, X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_RECORD);

    verifyGet(INVOICE_LINES_PATH, headers, APPLICATION_JSON, 200);

    validateNumberOfRequests(0, 0);
  }
}
