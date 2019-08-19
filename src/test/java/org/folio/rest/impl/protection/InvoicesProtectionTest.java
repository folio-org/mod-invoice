package org.folio.rest.impl.protection;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.folio.invoices.utils.ErrorCodes.INVOICE_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
import static org.folio.rest.impl.InvoicesApiTest.ALL_DESIRED_PERMISSIONS_HEADER;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;
import java.util.Collections;

import io.restassured.response.Response;

import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
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
  @Parameters({ "CREATE" })
  public void testOperationWithNonExistedUnits(ProtectedOperations operation) throws IOException {
    logger.info("=== Invoices protection: Test invoice contains non-existent unit ids resulting in units not found exception - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USERID);
    Errors errors = operation
      .process(INVOICE_PATH, encodePrettily(prepareInvoice(NON_EXISTENT_UNITS)), headers, APPLICATION_JSON,
          HttpStatus.HTTP_VALIDATION_ERROR.toInt())
      .as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors()
      .get(0)
      .getCode(), equalTo(INVOICE_UNITS_NOT_FOUND.getCode()));
    // Verify number of sub-requests
    validateNumberOfRequests(1, 0);
  }

  @Test
  @Parameters({ "CREATE" })
  public void testOperationWithAllowedUnits(ProtectedOperations operation) throws IOException {
    logger.info("=== Invoices protection: Test corresponding invoice has appropriate units which allows creation of invoice operation - expecting of call only to Units API ===");

    final Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USERID);
    Response resp = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NOT_PROTECTED_UNITS)), headers, operation.getContentType(),
        operation.getCode());

    assertThat(resp.getStatusCode(), equalTo(HttpStatus.HTTP_CREATED.toInt()));
    validateNumberOfRequests(1, 0);
  }

  @Test
  @Parameters({ "CREATE" })
  public void testWithRestrictedUnitsAndAllowedUser(ProtectedOperations operation) throws IOException {
    logger.info(
        "=== Invoices protection: Test corresponding invoice has appropriate units, units protect operation, user is member of invoice's units - expecting of calls to Units, Memberships APIs and allowance of operation ===");

    Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER,
        X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_INVOICE);
    Response resp = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)), headers, operation.getContentType(),
        operation.getCode());

    assertThat(resp.getStatusCode(), equalTo(HttpStatus.HTTP_CREATED.toInt()));
    validateNumberOfRequests(1, 1);
  }

  @Test
  @Parameters({ "CREATE" })
  public void testWithProtectedUnitsAndForbiddenUser(ProtectedOperations operation) throws IOException {
    logger.info(
        "=== Invoices protection: Test corresponding invoice has units, units protect operation, user isn't member of invoice units - expecting of calls to Units, Memberships APIs and restriction of operation ===");

    Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER,
        X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_INVOICE);
    Errors errors = operation
      .process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)), headers, APPLICATION_JSON,
          HttpStatus.HTTP_FORBIDDEN.toInt())
      .as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors()
      .get(0)
      .getCode(), equalTo(USER_HAS_NO_PERMISSIONS.getCode()));

    validateNumberOfRequests(1, 1);
  }
  
  @Test
  @Parameters({
    "CREATE"
  })
  public void testModifyUnitsList(ProtectedOperations operation) throws IOException {
    logger.info("=== Invoices protection: Test user without desired permissions modifying acqUnitsIds ===");

    Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_INVOICE);
    Invoice invoice = prepareInvoice(Collections.emptyList());
    invoice.setAcqUnitIds(PROTECTED_UNITS);
    Errors errors = operation.process(INVOICE_PATH, encodePrettily(invoice),
      headers, APPLICATION_JSON, HttpStatus.HTTP_FORBIDDEN.toInt()).as(Errors.class);
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_ACQ_PERMISSIONS.getCode()));

    validateNumberOfRequests(0, 0);
  }
}
