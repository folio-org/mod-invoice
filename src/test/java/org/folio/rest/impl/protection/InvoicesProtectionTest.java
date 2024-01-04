package org.folio.rest.impl.protection;

import static io.vertx.core.json.Json.encodePrettily;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ErrorCodes.ACQ_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_UNITS;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.ProtectionHelper.ACQUISITIONS_UNIT_IDS;
import static org.folio.rest.impl.protection.ProtectedOperations.UPDATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.acq.model.units.AcquisitionsUnit;
import org.folio.rest.acq.model.units.AcquisitionsUnitCollection;
import org.folio.rest.impl.MockServer;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.http.Headers;

public class InvoicesProtectionTest extends ProtectedEntityTestBase {

  private static final Logger logger = LogManager.getLogger(InvoicesProtectionTest.class);

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "CREATE",
    "UPDATE",
    "DELETE"
  })
  public void testOperationWithNonExistedUnits(ProtectedOperations operation) {
    logger.info("=== Invoices protection: Test record contains non-existent unit ids - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID, ALL_DESIRED_PERMISSIONS_HEADER);
    Errors errors = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NON_EXISTENT_UNITS)),
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
        "=== Invoices protection: Test corresponding record has units allowed operation - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID, ALL_DESIRED_PERMISSIONS_HEADER);
    operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NOT_PROTECTED_UNITS)), headers,
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
        "=== Invoices protection: Test corresponding record has units, units protect operation, user is member of order's units - expecting of calls to Units, Memberships APIs and allowance of operation ===");

    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_RECORD, ALL_DESIRED_PERMISSIONS_HEADER);
    operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)), headers,
        operation.getContentType(), operation.getCode());

    validateNumberOfRequests(1, 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "READ",
    "UPDATE",
    "CREATE",
    "DELETE"
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

  @ParameterizedTest
  @ValueSource(strings = {
    "CREATE",
    "UPDATE"
  })
  public void testModifyUnitsList(ProtectedOperations operation) {
    logger.info("=== Invoices protection: Test user without desired permissions modifying acqUnitsIds ===");

    Headers headers = prepareHeadersWithoutPermissions(X_OKAPI_TENANT, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_RECORD);
    Invoice invoice = prepareInvoice(Collections.emptyList());
    Errors errors = operation.process(INVOICE_PATH, encodePrettily(invoice.withAcqUnitIds(PROTECTED_UNITS)),
       headers, APPLICATION_JSON, HttpStatus.HTTP_FORBIDDEN.toInt()).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_ACQ_PERMISSIONS.getCode()));

    validateNumberOfRequests(0, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "CREATE",
    "UPDATE"
  })
  public void testAssigningSoftDeletedUnit(ProtectedOperations operation) {
    logger.info("=== Test invoice contains \"soft deleted\" unit id - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USER_ID);

    // Prepare acq unit in storage for update case and
    AcquisitionsUnit unit1 = prepareTestUnit(false);
    AcquisitionsUnit unit2 = prepareTestUnit(true);
    AcquisitionsUnit unit3 = prepareTestUnit(true);
    // Add all acq units as mock data
    addMockEntry(ACQUISITIONS_UNITS, unit1);
    addMockEntry(ACQUISITIONS_UNITS, unit2);
    addMockEntry(ACQUISITIONS_UNITS, unit3);

    // Prepare invoice with 2 acq units (one is "soft deleted") and add it as mock data for update case
    Invoice invoice = prepareInvoice(Arrays.asList(unit1.getId(), unit2.getId()));

    // Add the third unit to request
    invoice.getAcqUnitIds().add(unit3.getId());

    Errors errors = operation.process(INVOICE_PATH, encodePrettily(invoice),
        headers, APPLICATION_JSON, HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt()).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getCode(), equalTo(ACQ_UNITS_NOT_FOUND.getCode()));
    assertThat(error.getAdditionalProperties().get(ACQUISITIONS_UNIT_IDS), instanceOf(List.class));

    // Verify number of sub-requests
    validateNumberOfRequests(1, 0);

    List<?> ids = (List<?>) error.getAdditionalProperties().get(ACQUISITIONS_UNIT_IDS);
    if (operation == UPDATE) {
      assertThat(ids, contains(unit3.getId()));
    } else {
      assertThat(ids, containsInAnyOrder(unit2.getId(), unit3.getId()));
    }
  }

  @Test
  public void testGetInvoiceWithAssignedSoftDeletedUnit() {
    logger.info("=== Test invoice contains only \"soft deleted\" unit id - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(X_OKAPI_TENANT, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USER_ID);
    // Prepare acq unit in storage for update case
    AcquisitionsUnit unit1 = prepareTestUnit(true).withProtectRead(true);
    // Add all acq units as mock data
    addMockEntry(ACQUISITIONS_UNITS, unit1);

    // Prepare invoice with one acq unit ("soft deleted")
    Invoice invoice = prepareInvoice(Collections.singletonList(unit1.getId()));

    ProtectedOperations.READ.process(INVOICE_PATH, encodePrettily(invoice), headers, APPLICATION_JSON,
        HttpStatus.HTTP_OK.toInt());

    // Verify number of sub-requests
    validateNumberOfRequests(1, 0);

    // Verify that unit was deleted so logic skipped membership check
    List<AcquisitionsUnit> acquisitionsUnits = MockServer.getAcqUnitsSearches()
      .get(0)
      .mapTo(AcquisitionsUnitCollection.class)
      .getAcquisitionsUnits();
    assertThat(acquisitionsUnits, hasSize(1));
    assertThat(acquisitionsUnits.get(0).getIsDeleted(), is(true));
  }
}
