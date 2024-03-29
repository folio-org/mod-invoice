package org.folio.rest.impl.protection;

import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.utils.UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.invoices.utils.AcqDesiredPermissions;
import org.folio.rest.acq.model.units.AcquisitionsUnit;
import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.MockServer;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.hamcrest.Matcher;

import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class ProtectedEntityTestBase extends ApiTestBase {

  static final String FULL_PROTECTED_USERS_UNIT_ID = "e68c18fc-833f-494e-9a0e-b236eb4b310b";
  public static final List<String> PROTECTED_UNITS = Collections.singletonList(FULL_PROTECTED_USERS_UNIT_ID);
  static final String NOT_PROTECTED_UNIT_ID = "0e9525aa-d123-4e4d-9f7e-1b302a97eb90";
  public static final List<String> NOT_PROTECTED_UNITS = Arrays.asList(NOT_PROTECTED_UNIT_ID, FULL_PROTECTED_USERS_UNIT_ID);
  static final String NON_EXISTENT_UNIT_ID = "b548d790-07da-456f-b4ea-7a77c0e34a0f";
  public static final List<String> NON_EXISTENT_UNITS = Arrays.asList(NON_EXISTENT_UNIT_ID, "0f2bb7a2-728f-4e07-9268-082577a7bedb");
  public static final List<String> BAD_UNITS = Arrays.asList(BAD_QUERY, BAD_QUERY);

  private static final String USER_IS_NOT_MEMBER_OF_ACQ_UNITS = "7007ed1b-85ab-46e8-9524-fada8521dfd5";
  static final Header X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_RECORD = new Header(OKAPI_USERID_HEADER,
      USER_IS_NOT_MEMBER_OF_ACQ_UNITS);

  private static final String USER_IS_MEMBER_OF_ACQ_UNITS = "6b4be232-5ad9-47a6-80b1-8c1acabd6212";
  static final Header X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_RECORD = new Header(OKAPI_USERID_HEADER, USER_IS_MEMBER_OF_ACQ_UNITS);
  protected static final Header ALL_DESIRED_PERMISSIONS_HEADER = new Header(OKAPI_HEADER_PERMISSIONS,
      new JsonArray(AcqDesiredPermissions.getValuesExceptBypass()).encode());

  static void validateNumberOfRequests(int numOfUnitRqs, int numOfMembershipRqs) {
    assertThat(MockServer.getAcqUnitsSearches(), getMatcher(numOfUnitRqs));
    assertThat(MockServer.getAcqMembershipsSearches(), getMatcher(numOfMembershipRqs));
  }

  static Matcher<Collection<?>> getMatcher(Integer value) {
    return value > 0 ? hasSize(value) : empty();
  }

  public Invoice prepareInvoice(List<String> acqUnitsIds) {
    Invoice invoice = getMinimalContentInvoice();
    invoice.setAcqUnitIds(new ArrayList<>(acqUnitsIds));
    addMockEntry(INVOICES, JsonObject.mapFrom(invoice));
    return invoice;
  }

  public InvoiceLine prepareInvoiceLine(List<String> acqUnitsIds) {
    Invoice invoice = prepareInvoice(acqUnitsIds);
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(invoice.getId());
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    return invoiceLine;
  }

  public AcquisitionsUnit prepareTestUnit(boolean isDeleted) {
    String id = UUID.randomUUID().toString();
    return new AcquisitionsUnit().withId(id).withName(id).withIsDeleted(isDeleted);
  }
}
