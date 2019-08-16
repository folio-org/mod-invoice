package org.folio.rest.impl.protection;


import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.impl.InvoicesApiTest.APPROVED_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.MockServer;
import org.folio.rest.jaxrs.model.Invoice;
import org.hamcrest.Matcher;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

public abstract class ProtectedEntityTestBase extends ApiTestBase {

  static final String DELETE_PROTECTED_UNIT = "2e6a170f-ae20-4889-813f-641831e24b84";
  public static final List<String> DELETE_PROTECTED_UNITS = Arrays.asList(DELETE_PROTECTED_UNIT);
  static final String CREATE_PROTECTED_UNIT = "f6d2cc9d-82ca-437c-a4e6-e5c30323df00";
  public static final List<String> CREATE_PROTECTED_UNITS = Arrays.asList(CREATE_PROTECTED_UNIT);
  static final String FULL_PROTECTED_USERS_UNIT_ID = "e68c18fc-833f-494e-9a0e-b236eb4b310b";
  public static final List<String> PROTECTED_UNITS = Arrays.asList(FULL_PROTECTED_USERS_UNIT_ID);
  static final String NOT_PROTECTED_UNIT_ID = "0e9525aa-d123-4e4d-9f7e-1b302a97eb90";
  public static final List<String> NOT_PROTECTED_UNITS = Arrays.asList(NOT_PROTECTED_UNIT_ID, FULL_PROTECTED_USERS_UNIT_ID);
  static final String NON_EXISTENT_UNIT_ID = "b548d790-07da-456f-b4ea-7a77c0e34a0f";
  public static final List<String> NON_EXISTENT_UNITS = Arrays.asList(NON_EXISTENT_UNIT_ID, "0f2bb7a2-728f-4e07-9268-082577a7bedb");

  private static final String USER_IS_NOT_MEMBER_OF_ORDERS_UNITS = "7007ed1b-85ab-46e8-9524-fada8521dfd5";
  static final Header X_OKAPI_USER_WITH_UNITS_NOT_ASSIGNED_TO_ORDER = new Header(OKAPI_USERID_HEADER, USER_IS_NOT_MEMBER_OF_ORDERS_UNITS);

  private static final String USER_IS_MEMBER_OF_ORDER_UNITS = "6b4be232-5ad9-47a6-80b1-8c1acabd6212";
  static final Header X_OKAPI_USER_WITH_UNITS_ASSIGNED_TO_ORDER = new Header(OKAPI_USERID_HEADER, USER_IS_MEMBER_OF_ORDER_UNITS);

  static void validateNumberOfRequests(int numOfUnitRqs, int numOfMembershipRqs) {
    assertThat(MockServer.getAcqUnitsSearches(), getMatcher(numOfUnitRqs));
    assertThat(MockServer.getAcqMembershipsSearches(), getMatcher(numOfMembershipRqs));
  }

  static Matcher getMatcher(int value) {
    return value > 0 ? hasSize(value) : nullValue();
  }

  public Invoice prepareInvoice(List<String> acqUnitsIds) throws IOException {
    Invoice invoice = new JsonObject(getMockData(APPROVED_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);
    invoice.setAcqUnitIds(NON_EXISTENT_UNITS);
    addMockEntry(INVOICES, JsonObject.mapFrom(invoice));
    return invoice;
  }
}
