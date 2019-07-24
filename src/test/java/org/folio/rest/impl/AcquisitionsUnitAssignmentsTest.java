package org.folio.rest.impl;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignment;
import org.folio.rest.jaxrs.model.AcquisitionsUnitAssignmentCollection;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.Test;

import javax.ws.rs.core.HttpHeaders;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ErrorCodes.MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.MockServer.ERROR_X_OKAPI_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class AcquisitionsUnitAssignmentsTest extends ApiTestBase {
  private static final Logger logger = LoggerFactory.getLogger(AcquisitionsUnitAssignmentsTest.class);

  private static final String ACQ_UNIT_ASSIGNMENTS_ENDPOINT = "/invoice/acquisitions-unit-assignments";
  private static final String VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT = "/voucher/acquisitions-unit-assignments";
  
  private static final String ASSIGNED_INVOICE_ID = "57257531-c6bc-4b6e-b91a-06bc0034b768";
  private static final String ASSIGNED_VOUCHER_ID = "57b575a1-c6bc-4b6e-b91a-06bc0034b768";
  private static final String ACQ_ASSIGNMENT_ID = "8ad510ee-d3f1-4271-bff4-c7c80ccb22e5";
  private static final String ACQ_UNIT_ID = "c2d6608f-6d1f-45f7-8817-5d32c2416116";

  @Test
  public void testGetAcqUnitAssignmentsNoQuery() {
    logger.info("=== Test Get Acquisitions Unit Assignments - With empty query ===");
    final AcquisitionsUnitAssignmentCollection units = verifySuccessGet(ACQ_UNIT_ASSIGNMENTS_ENDPOINT, AcquisitionsUnitAssignmentCollection.class);
    assertThat(units.getAcquisitionsUnitAssignments(), hasSize(2));
  }

  @Test
  public void testGetAcqUnitAssignmentsWithQuery() {
    logger.info("=== Test GET Acquisitions Unit Assignments - search by query ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "?query=recordId==" + ASSIGNED_INVOICE_ID;

    final AcquisitionsUnitAssignmentCollection units = verifySuccessGet(url, AcquisitionsUnitAssignmentCollection.class);
    assertThat(units.getAcquisitionsUnitAssignments(), hasSize(1));
  }

  @Test
  public void testGetAcqUnitAssignmentsWithUnprocessableQuery() {
    logger.info("=== Test GET Acquisitions Unit Assignments - unprocessable query ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "?query=" + BAD_QUERY;

    verifyGet(url, APPLICATION_JSON, 400);
  }

  @Test
  public void testGetAcqUnitAssignmentsSuccess() {
    logger.info("=== Test GET Acquisitions Unit Assignment - success case ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ACQ_ASSIGNMENT_ID;

    final AcquisitionsUnitAssignment unit = verifySuccessGet(url, AcquisitionsUnitAssignment.class);
    assertThat(unit, notNullValue());
  }

  @Test
  public void testGetAcqUnitAssignmentNotFound() {
    logger.info("=== Test GET Acquisitions Unit Assignment - not found ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ID_DOES_NOT_EXIST;

    verifyGet(url, APPLICATION_JSON, 404);
  }

  @Test
  public void testPutAcqUnitAssignmentSuccess() {
    logger.info("=== Test PUT Acquisitions Unit Assignment - success case ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ACQ_ASSIGNMENT_ID;

    verifyPut(url, JsonObject.mapFrom(new AcquisitionsUnitAssignment()
      .withRecordId("47Ac60b4-159D-4e1c-9aCB-8293Df67D16d")
      .withAcquisitionsUnitId(ACQ_UNIT_ID)), "", 204);
  }

  @Test
  public void testValidationOnPutUnitAssignmentWithoutBody() {
    logger.info("=== Test validation on PUT Acquisitions Unit Assignment with no body ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + UUID.randomUUID().toString();

    verifyPut(url, "", TEXT_PLAIN, 400);
  }

  @Test
  public void testPutUnitAssignmentNotFound() {
    logger.info("=== Test PUT Acquisitions Unit Assignment - not found ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ID_DOES_NOT_EXIST;

    verifyPut(url, JsonObject.mapFrom(new AcquisitionsUnitAssignment()
      .withRecordId(ASSIGNED_INVOICE_ID)
      .withAcquisitionsUnitId(ACQ_ASSIGNMENT_ID)), APPLICATION_JSON, 404);
  }

  @Test
  public void testPutUnitAssignmentIdMismatch() {
    logger.info("=== Test PUT Acquisitions Unit Assignment - different ids in path and body ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + UUID.randomUUID().toString();

    AcquisitionsUnitAssignment unitAssignment = new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_INVOICE_ID)
      .withAcquisitionsUnitId(ACQ_ASSIGNMENT_ID).withId(UUID.randomUUID().toString());
    Errors errors = verifyPut(url, JsonObject.mapFrom(unitAssignment), APPLICATION_JSON, 422).as(Errors.class);
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY.getCode()));
  }

  @Test
  public void testDeleteAcqUnitAssignmentSuccess() {
    logger.info("=== Test DELETE Acquisitions Unit Assignment - success case ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ACQ_ASSIGNMENT_ID;

    verifyDeleteResponse(url, "", 204);
  }

  @Test
  public void testDeletePutUnitAssignmentNotFound() {
    logger.info("=== Test DELETE Acquisitions Unit Assignment - not found ===");
    String url = ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ID_DOES_NOT_EXIST;

    verifyPut(url, JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_INVOICE_ID)
      .withAcquisitionsUnitId(ACQ_UNIT_ID)), APPLICATION_JSON, 404);
  }

  @Test
  public void testPostAcqUnitAssignmentSuccess() {
    logger.info("=== Test POST Acquisitions Unit Assignment - success case ===");

    String body = JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId("c4d6608f-6d1f-45f7-8717-5d32c2416116")
      .withAcquisitionsUnitId("6b982ffe-8efd-4690-8168-0c773b49cde1")).encode();
    Response response = verifyPostResponse(ACQ_UNIT_ASSIGNMENTS_ENDPOINT, body, prepareHeaders(X_OKAPI_TENANT),
        APPLICATION_JSON, 201);
    AcquisitionsUnitAssignment unitAssignment = response.as(AcquisitionsUnitAssignment.class);

    assertThat(unitAssignment.getId(), not(isEmptyOrNullString()));
    assertThat(response.header(HttpHeaders.LOCATION), containsString(unitAssignment.getId()));
  }

  @Test
  public void testPostAcqUnitAssignmentServerError() {
    logger.info("=== Test POST Acquisitions Unit Assignment - Server Error ===");

    String body = JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_INVOICE_ID)
      .withAcquisitionsUnitId(ACQ_UNIT_ID)).encode();
    Headers headers = prepareHeaders(ERROR_X_OKAPI_TENANT, new Header(X_ECHO_STATUS, String.valueOf(500)));
    verifyPostResponse(ACQ_UNIT_ASSIGNMENTS_ENDPOINT, body, headers, APPLICATION_JSON, 500);
  }
  
  @Test
  public void testGetVoucherAcqUnitAssignmentsNoQuery() {
    logger.info("=== Test Get Voucher Acquisitions Unit Assignments - With empty query ===");
    final AcquisitionsUnitAssignmentCollection units = verifySuccessGet(VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT,
        AcquisitionsUnitAssignmentCollection.class);
    assertThat(units.getAcquisitionsUnitAssignments(), hasSize(2));
  }

  @Test
  public void testGetVoucherAcqUnitAssignmentsWithQuery() {
    logger.info("=== Test GET Voucher Acquisitions Unit Assignments - search by query ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "?query=recordId==" + ASSIGNED_VOUCHER_ID;

    final AcquisitionsUnitAssignmentCollection units = verifySuccessGet(url, AcquisitionsUnitAssignmentCollection.class);
    assertThat(units.getAcquisitionsUnitAssignments(), hasSize(1));
  }

  @Test
  public void testGetVoucherAcqUnitAssignmentsWithUnprocessableQuery() {
    logger.info("=== Test GET Voucher Acquisitions Unit Assignments - unprocessable query ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "?query=" + BAD_QUERY;

    verifyGet(url, APPLICATION_JSON, 400);
  }

  @Test
  public void testGetVoucherAcqUnitAssignmentsSuccess() {
    logger.info("=== Test GET Voucher Acquisitions Unit Assignment - success case ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ACQ_ASSIGNMENT_ID;

    final AcquisitionsUnitAssignment unit = verifySuccessGet(url, AcquisitionsUnitAssignment.class);
    assertThat(unit, notNullValue());
  }

  @Test
  public void testGetVoucherAcqUnitAssignmentNotFound() {
    logger.info("=== Test GET Voucher Acquisitions Unit Assignment - not found ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ID_DOES_NOT_EXIST;

    verifyGet(url, APPLICATION_JSON, 404);
  }

  @Test
  public void testPutVoucherAcqUnitAssignmentSuccess() {
    logger.info("=== Test PUT Voucher Acquisitions Unit Assignment - success case ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ACQ_ASSIGNMENT_ID;

    verifyPut(url, JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId("5ef68fca-68fc-440a-8edc-29e60a8ceea6")
      .withAcquisitionsUnitId("72a51033-2e05-4e12-bfc2-f3ed48992e54")), "", 204);
  }

  @Test
  public void testValidationOnPutVoucherAcqUnitAssignmentWithoutBody() {
    logger.info("=== Test validation on PUT Voucher Acquisitions Unit Assignment with no body ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + UUID.randomUUID()
      .toString();

    verifyPut(url, "", TEXT_PLAIN, 400);
  }

  @Test
  public void testPutVoucherAcqUnitAssignmentNotFound() {
    logger.info("=== Test PUT Voucher Acquisitions Unit Assignment - not found ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ID_DOES_NOT_EXIST;

    verifyPut(url, JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_VOUCHER_ID)
      .withAcquisitionsUnitId(ACQ_ASSIGNMENT_ID)), APPLICATION_JSON, 404);
  }

  @Test
  public void testPutVoucherAcqUnitAssignmentIdMismatch() {
    logger.info("=== Test PUT Voucher Acquisitions Unit Assignment - different ids in path and body ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + UUID.randomUUID()
      .toString();

    AcquisitionsUnitAssignment unitAssignment = new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_VOUCHER_ID)
      .withAcquisitionsUnitId(ACQ_ASSIGNMENT_ID)
      .withId(UUID.randomUUID()
        .toString());
    Errors errors = verifyPut(url, JsonObject.mapFrom(unitAssignment), APPLICATION_JSON, 422).as(Errors.class);
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors()
      .get(0)
      .getCode(), equalTo(MISMATCH_BETWEEN_ID_IN_PATH_AND_BODY.getCode()));
  }

  @Test
  public void testDeleteVoucherAcqUnitAssignmentSuccess() {
    logger.info("=== Test DELETE Voucher Acquisitions Unit Assignment - success case ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ACQ_ASSIGNMENT_ID;

    verifyDeleteResponse(url, "", 204);
  }

  @Test
  public void testDeleteVoucherAcqUnitAssignmentNotFound() {
    logger.info("=== Test DELETE Voucher Acquisitions Unit Assignment - not found ===");
    String url = VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT + "/" + ID_DOES_NOT_EXIST;

    verifyDeleteResponse(url, "", 404);
  }

  @Test
  public void testPostVoucherAcqUnitAssignmentSuccess() {
    logger.info("=== Test POST Voucher Acquisitions Unit Assignment - success case ===");

    String body = JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_VOUCHER_ID)
      .withAcquisitionsUnitId(ACQ_ASSIGNMENT_ID))
      .encode();
    Response response = verifyPostResponse(VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT, body, prepareHeaders(X_OKAPI_TENANT),
        APPLICATION_JSON, 201);
    AcquisitionsUnitAssignment unitAssignment = response.as(AcquisitionsUnitAssignment.class);

    assertThat(unitAssignment.getId(), not(isEmptyOrNullString()));
    assertThat(response.header(HttpHeaders.LOCATION), containsString(unitAssignment.getId()));
  }

  @Test
  public void testPostVoucherAcqUnitAssignmentServerError() {
    logger.info("=== Test POST Voucher Acquisitions Unit Assignment - Server Error ===");

    String body = JsonObject.mapFrom(new AcquisitionsUnitAssignment().withRecordId(ASSIGNED_VOUCHER_ID)
      .withAcquisitionsUnitId(ACQ_ASSIGNMENT_ID))
      .encode();
    Headers headers = prepareHeaders(ERROR_X_OKAPI_TENANT, new Header(X_ECHO_STATUS, String.valueOf(500)));
    verifyPostResponse(VOUCHER_ACQ_UNIT_ASSIGNMENTS_ENDPOINT, body, headers, APPLICATION_JSON, 500);
  }
}
