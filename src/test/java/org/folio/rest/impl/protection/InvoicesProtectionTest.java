package org.folio.rest.impl.protection;

import static io.vertx.core.json.Json.encodePrettily;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ErrorCodes.ACQ_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_PATH;
import static org.folio.rest.impl.ProtectionHelper.ACQUISITIONS_UNIT_IDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;

import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.Headers;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class InvoicesProtectionTest extends ProtectedEntityTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesProtectionTest.class);

  @Test
  @Parameters({
    "READ",
    "CREATE"
  })
  public void testOperationWithNonExistedUnits(ProtectedOperations operation) {
    logger.info("=== Invoices protection: Test record contains non-existent unit ids - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID, ALL_DESIRED_PERMISSIONS_HEADER);
    Errors errors = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NON_EXISTENT_UNITS)),
      headers, APPLICATION_JSON, HttpStatus.HTTP_VALIDATION_ERROR.toInt()).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(ACQ_UNITS_NOT_FOUND.getCode()));
    assertThat(errors.getErrors().get(0).getParameters(), hasSize(1));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getKey(), equalTo(ACQUISITIONS_UNIT_IDS));
    // Verify number of sub-requests
    validateNumberOfRequests(1, 0);
  }

  @Test
  @Parameters({
    "READ",
    "CREATE"
  })
  public void testOperationWithAllowedUnits(ProtectedOperations operation) {
    logger.info(
        "=== Invoices protection: Test corresponding record has units allowed operation - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID, ALL_DESIRED_PERMISSIONS_HEADER);
    operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NOT_PROTECTED_UNITS)), headers,
        operation.getContentType(), operation.getCode());

    validateNumberOfRequests(1, 0);
  }

  @Test
  @Parameters({
    "READ",
    "CREATE"
  })
  public void testWithRestrictedUnitsAndAllowedUser(ProtectedOperations operation) {
    logger.info(
        "=== Invoices protection: Test corresponding record has units, units protect operation, user is member of order's units - expecting of calls to Units, Memberships APIs and allowance of operation ===");

    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_RECORD, ALL_DESIRED_PERMISSIONS_HEADER);
    operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)), headers,
        operation.getContentType(), operation.getCode());

    validateNumberOfRequests(1, 1);
  }

  @Test
  @Parameters({
    "READ",
    "CREATE"
  })
  public void testWithProtectedUnitsAndForbiddenUser(ProtectedOperations operation) {
    logger.info("=== Invoices protection: Test corresponding record has units, units protect operation, user isn't member of order's units - expecting of calls to Units, Memberships APIs and restriction of operation ===");

    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_RECORD, ALL_DESIRED_PERMISSIONS_HEADER);
    Errors errors = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)),
      headers, APPLICATION_JSON, HttpStatus.HTTP_FORBIDDEN.toInt()).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_PERMISSIONS.getCode()));

    validateNumberOfRequests(1, 1);
  }

  @Test
  @Parameters({
    "CREATE"
  })
  public void testModifyUnitsList(ProtectedOperations operation) {
    logger.info("=== Invoices protection: Test user without desired permissions modifying acqUnitsIds ===");

     Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_RECORD);
     Errors errors = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)),
         headers, APPLICATION_JSON, HttpStatus.HTTP_FORBIDDEN.toInt()).as(Errors.class);
     
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_ACQ_PERMISSIONS.getCode()));

     validateNumberOfRequests(0, 0);
  }
}
