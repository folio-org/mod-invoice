package org.folio.rest.impl.protection;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_UNITS_NOT_FOUND;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
//import static org.folio.orders.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
//import static org.folio.orders.utils.ErrorCodes.USER_HAS_NO_PERMISSIONS;
//import static org.folio.orders.utils.ResourcePathResolver.PO_LINES;
//import static org.folio.orders.utils.ResourcePathResolver.PURCHASE_ORDER;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.InvoicesApiTest.ALL_DESIRED_PERMISSIONS_HEADER;
import static org.folio.rest.impl.InvoicesApiTest.APPROVED_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_PATH;
import static org.folio.rest.impl.protection.ProtectedOperations.CREATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.HttpStatus;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.http.Headers;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class InvoicesProtectionTest extends ProtectedEntityTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesProtectionTest.class);


//  @Test
//  @Parameters({
//    "CREATE"
//  })
//  public void testOperationWithNonExistedUnits(ProtectedOperations operation) throws IOException {
//    logger.info("=== Test invoice contains non-existent unit ids - expecting of call only to Units API ===");
//
//    final Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USER_ID);
//    Errors errors = operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NON_EXISTENT_UNITS)),
//      headers, APPLICATION_JSON, HttpStatus.HTTP_VALIDATION_ERROR.toInt()).as(Errors.class);
//
//    assertThat(errors.getErrors(), hasSize(1));
//    assertThat(errors.getErrors().get(0).getCode(), equalTo(INVOICE_UNITS_NOT_FOUND.getCode()));
//    // Verify number of sub-requests
//    validateNumberOfRequests(1, 0);
//  }
//  
//  @Test
//  @Parameters({
//    "CREATE"
//  })
//  public void testOperationWithAllowedUnits(ProtectedOperations operation) throws IOException {
//    logger.info("=== Test corresponding invoice has units allowed operation - expecting of call only to Units API ===");
//
//    final Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USER_ID);
//    operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(NOT_PROTECTED_UNITS)), headers, operation.getContentType(), operation.getCode());
//
//    validateNumberOfRequests(1, 0);
//  }
  
//  @Test
//  @Parameters({
//    "CREATE"
//  })
//  public void testWithRestrictedUnitsAndAllowedUser(ProtectedOperations operation) throws IOException {
//    logger.info("=== Test corresponding invoice has units, units protect operation, user is member of invoice's units - expecting of calls to Units, Memberships APIs and allowance of operation ===");
//
//    Headers headers = prepareHeaders(EXIST_CONFIG_X_OKAPI_TENANT_LIMIT_10, ALL_DESIRED_PERMISSIONS_HEADER, X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_ORDER);
//    operation.process(INVOICE_PATH, encodePrettily(prepareInvoice(PROTECTED_UNITS)), headers, operation.getContentType(), operation.getCode());
//
//    validateNumberOfRequests(1, 1);
//  }
}
