package org.folio.rest.impl;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.awaitility.Awaitility.await;
import static org.folio.invoices.utils.ErrorCodes.ACCOUNTING_CODE_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.BUDGET_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.EXTERNAL_ACCOUNT_NUMBER_IS_MISSING;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.folio.invoices.utils.ErrorCodes.FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.invoices.utils.ErrorCodes.INCORRECT_FUND_DISTRIBUTION_TOTAL;
import static org.folio.invoices.utils.ErrorCodes.INVALID_INVOICE_TRANSITION_ON_PAID_STATUS;
import static org.folio.invoices.utils.ErrorCodes.LOCK_AND_CALCULATED_TOTAL_MISMATCH;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_FIELD_CHANGING;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_APPROVE_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_FISCAL_YEAR_UPDATE_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_PAY_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NUMBER_PREFIX_NOT_ALPHA;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_UPDATE_FAILURE;
import static org.folio.invoices.utils.HelperUtils.INVOICE;
import static org.folio.invoices.utils.HelperUtils.calculateInvoiceLineTotals;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherAmount;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getNoAcqUnitCQL;
import static org.folio.invoices.utils.ResourcePathResolver.BUDGETS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_BATCH_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.LEDGERS;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;
import static org.folio.rest.impl.AbstractHelper.DEFAULT_SYSTEM_CURRENCY;
import static org.folio.rest.impl.InvoiceLinesApiTest.APPROVED_INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_LIST_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_MOCK_DATA_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.InvoicesImpl.PROTECTED_AND_MODIFIED_FIELDS;
import static org.folio.rest.impl.MockServer.CURRENT_FISCAL_YEAR;
import static org.folio.rest.impl.MockServer.ERROR_CONFIG_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.FISCAL_YEAR_ID;
import static org.folio.rest.impl.MockServer.INVALID_PREFIX_CONFIG_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.INVOICE_NUMBER_ERROR_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.NON_EXIST_CONFIG_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.PREFIX_CONFIG_WITHOUT_VALUE_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.PREFIX_CONFIG_WITH_NON_EXISTING_VALUE_X_OKAPI_TENANT;
import static org.folio.rest.impl.MockServer.TEST_PREFIX;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getAcqMembershipsSearches;
import static org.folio.rest.impl.MockServer.getAcqUnitsSearches;
import static org.folio.rest.impl.MockServer.getInvoiceCreations;
import static org.folio.rest.impl.MockServer.getInvoiceLineSearches;
import static org.folio.rest.impl.MockServer.getInvoiceRetrievals;
import static org.folio.rest.impl.MockServer.getInvoiceSearches;
import static org.folio.rest.impl.MockServer.getInvoiceUpdates;
import static org.folio.rest.impl.MockServer.getQueryParams;
import static org.folio.rest.impl.MockServer.getRqRsEntries;
import static org.folio.rest.impl.MockServer.serverRqRs;
import static org.folio.rest.impl.ProtectionHelper.ACQUISITIONS_UNIT_IDS;
import static org.folio.rest.impl.VouchersApiTest.VOUCHERS_LIST_PATH;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.AMOUNT;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;
import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;
import static org.folio.services.validator.InvoiceValidator.NO_INVOICE_LINES_ERROR_MSG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.rest.acq.model.finance.Batch;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.Budget.BudgetStatus;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.Transaction.TransactionType;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.units.AcquisitionsUnitMembershipCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Adjustment.Prorate;
import org.folio.rest.jaxrs.model.Adjustment.Type;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.Invoice.Status;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.rest.jaxrs.model.VoucherLineCollection;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.hamcrest.Matchers;
import org.hamcrest.beans.HasProperty;
import org.hamcrest.beans.HasPropertyWithValue;
import org.hamcrest.core.Every;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class InvoicesApiTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(InvoicesApiTest.class);

  public static final String INVOICE_PATH = "/invoice/invoices";
  static final String INVOICE_ID_PATH = INVOICE_PATH+ "/%s";
  static final String INVOICE_LINE_FOR_MOVE_PAYMENT_PATH = INVOICE_LINES_MOCK_DATA_PATH + "8a1dc57e-8857-4dc3-bdda-871686e0b98b.json";
  private static final String INVOICE_ID_WITH_LANG_PATH = INVOICE_ID_PATH + "?lang=%s";
  private static final String INVOICE_FISCAL_YEARS_PATH = INVOICE_ID_PATH + "/fiscal-years";
  private static final String INVOICE_PATH_BAD = "/invoice/bad";
  private static final String INVOICE_NUMBER_PATH = "/invoice/invoice-number";
  static final String INVOICE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "invoices/";
  static final String VOUCHER_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "vouchers/";
  static final String VOUCHER_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "voucherLines/";
  private static final String PO_LINE_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  static final String REVIEWED_INVOICE_ID = "3773625a-dc0d-4a2d-959e-4a91ee265d67";
  public static final String OPEN_INVOICE_ID = "52fd6ec7-ddc3-4c53-bc26-2779afc27136";
  public static final String OPEN_INVOICE_ID_WITHOUT_FISCAL_YEAR = "8c92f2a1-67e3-4cfe-ba02-ba16404f7f1c";
  private static final String INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID = "5cb6d270-a54c-4c38-b645-3ae7f249c606";
  private static final String APPROVED_INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + APPROVED_INVOICE_ID + ".json";
  private static final String APPROVED_INVOICE_WITHOUT_FISCAL_YEAR_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + APPROVED_INVOICE_WITHOUT_FISCAL_YEAR_ID + ".json";
  private static final String APPROVED_INVOICE_MULTIPLE_ADJUSTMENTS_FISCAL_YEAR_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + APPROVED_INVOICE_MULTIPLE_ADJUSTMENTS_FISCAL_YEAR_ID + ".json";
  private static final String APPROVED_INVOICE_SAMPLE_PATH_WITHOUT_FISCAL_YEAR = INVOICE_MOCK_DATA_PATH + APPROVED_INVOICE_ID_WITHOUT_FISCAL_YEAR + ".json";
  private static final String APPROVED_INVOICE_FOR_MOVE_PAYMENT_PATH = INVOICE_MOCK_DATA_PATH + "b8862151-6aa5-4301-aa1a-34096a03a5f7.json";
  private static final String REVIEWED_INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + REVIEWED_INVOICE_ID + ".json";
  private static final String REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + "402d0d32-7377-46a7-86ab-542b5684506e.json";
  private static final String VOUCHER_FOR_MOVE_PAYMENT_PATH = VOUCHER_MOCK_DATA_PATH + "9ebe5abc-1565-48e0-8531-ae03e83815ca.json";

  public static final String OPEN_INVOICE_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + OPEN_INVOICE_ID + ".json";
  public static final String OPEN_INVOICE_SAMPLE_PATH_WITHOUT_FISCAL_YEAR = INVOICE_MOCK_DATA_PATH + OPEN_INVOICE_ID_WITHOUT_FISCAL_YEAR + ".json";
  private static final String OPEN_INVOICE_WITH_APPROVED_FILEDS_SAMPLE_PATH = INVOICE_MOCK_DATA_PATH + "d3e13ed1-59da-4f70-bba3-a140e11d30f3.json";
  private static final String INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID_PATH = INVOICE_LINES_MOCK_DATA_PATH +
    INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID + ".json";

  static final String BAD_QUERY = "unprocessableQuery";
  private static final String VENDOR_INVOICE_NUMBER_FIELD = "vendorInvoiceNo";
  static final String EXISTING_VENDOR_INV_NO = "existingVendorInvoiceNo";
  private static final String BAD_INVOICE_ID = "5a34ae0e-5a11-4337-be95-1a20cfdc3161";
  private static final String EXISTENT_PO_LINE_ID = "c2755a78-2f8d-47d0-a218-059a9b7391b4";
  private static final String EXISTING_VOUCHER_ID = "a9b99f8a-7100-47f2-9903-6293d44a9905";
  private static final String FUND_ID_WITHOUT_EXTERNAL_ACCOUNT_NUMBER = "29db8f8e-0d7c-4406-a373-389f00884f99";
  private static final String STATUS = "status";
  private static final String INVALID_CURRENCY = "ABC";
  public static final String EXISTING_FUND_ID = "1d1574f1-9196-4a57-8d1f-3b2e4309eb81";
  public static final String EXISTING_FUND_ID_2 = "2d1574f1-919cc4a57-8d1f-3b2e4619eb81";
  public static final String EXISTING_FUND_ID_3 = "7fbd5d84-62d1-44c6-9c45-6cb173998bbd";
  private static final String FUND_ID_WITH_NOT_ENOUGH_AMOUNT_IN_BUDGET = "2d1574f1-919cc4a57-8d1f-3b2e4619eb81";
  public static final String FUND_ID_WITH_NOT_ACTIVE_BUDGET = "3d1574f1-919cc4a57-8d1f-3b2e4619eb81";
  public static final String EXISTING_LEDGER_ID = "a3ec5552-c4a4-4a15-a57c-0046db536369";
  public static final String EXPENSE_CLASSES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "expense-classes/";
  public static final String EXPENSE_CLASSES_LIST_PATH = EXPENSE_CLASSES_MOCK_DATA_PATH + "expense-classes.json";

  @Test
  void testGetInvoicingInvoices() {
    logger.info("=== Test Get Invoices by without query - get 200 by successful retrieval of invoices ===");

    final InvoiceCollection resp = verifySuccessGet(INVOICE_PATH, InvoiceCollection.class, X_OKAPI_PROTECTED_READ_TENANT);

    assertThat(resp.getTotalRecords(), is(3));
    assertThat(getInvoiceSearches(), hasSize(1));
    assertThat(getInvoiceLineSearches(), empty());
    assertThat(getAcqUnitsSearches(), hasSize(1));
    assertThat(getAcqMembershipsSearches(), hasSize(1));
    verifyInvoiceUpdateCalls(0);

    List<String> queryParams = getQueryParams(INVOICES);
    assertThat(queryParams, hasSize(1));
    assertThat(queryParams.get(0), Matchers.equalTo(getNoAcqUnitCQL(INVOICES)));
  }

  @Test
  void testGetInvoicingInvoicesWithQueryParam() {
    logger.info("=== Test Get Invoices with query - get 200 by successful retrieval of invoices by query ===");

    String sortBy = " sortBy folioInvoiceNo";
    String queryValue = VENDOR_INVOICE_NUMBER_FIELD + "==" + EXISTING_VENDOR_INV_NO;
    String endpointQuery = String.format("%s?query=%s%s", INVOICE_PATH,  queryValue, sortBy);

    final InvoiceCollection resp = verifySuccessGet(endpointQuery, InvoiceCollection.class, X_OKAPI_PROTECTED_READ_TENANT);

    assertThat(resp.getTotalRecords(), is(1));
    assertThat(getInvoiceSearches(), hasSize(1));
    assertThat(getAcqUnitsSearches(), hasSize(1));
    assertThat(getAcqMembershipsSearches(), hasSize(1));
    assertThat(getInvoiceLineSearches(), empty());
    verifyInvoiceUpdateCalls(0);

    List<String> queryParams = getQueryParams(INVOICES);
    assertThat(queryParams, hasSize(1));
    String queryToStorage = queryParams.get(0);
    assertThat(queryToStorage, containsString("(" + queryValue + ")"));
    assertThat(queryToStorage, Matchers.not(containsString(ACQUISITIONS_UNIT_IDS + "=")));
    assertThat(queryToStorage, containsString(getNoAcqUnitCQL(INVOICE)));
    assertThat(queryToStorage, endsWith(sortBy));
  }

  @Test
  void testGetInvoicesForUserAssignedToAcqUnits() {
    logger.info("=== Test Get Invoices by query - user assigned to acq units ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, NON_EXIST_CONFIG_X_OKAPI_TENANT, X_OKAPI_USER_ID_WITH_ACQ_UNITS);
    verifyGet(INVOICE_PATH, headers, APPLICATION_JSON, 200);

    assertThat(getInvoiceSearches(), hasSize(1));
    assertThat(getAcqUnitsSearches(), hasSize(1));
    assertThat(getAcqMembershipsSearches(), hasSize(1));

    List<String> queryParams = getQueryParams(INVOICES);
    assertThat(queryParams, hasSize(1));
    String queryToStorage = queryParams.get(0);
    assertThat(queryToStorage, containsString(ACQUISITIONS_UNIT_IDS + "="));
    assertThat(queryToStorage, containsString(getNoAcqUnitCQL(INVOICES)));

    getAcqMembershipsSearches().get(0)
      .mapTo(AcquisitionsUnitMembershipCollection.class)
      .getAcquisitionsUnitMemberships()
      .forEach(member -> assertThat(queryToStorage, containsString(member.getAcquisitionsUnitId())));
  }

  @Test
  void testGetInvoicesBadQuery() {
    logger.info("=== Test Get Invoices by query - unprocessable query to emulate 400 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_PATH,  BAD_QUERY);

    verifyGet(endpointQuery, APPLICATION_JSON, 400);
  }

  @Test
  void testGetInvoicesInternalServerError() {
    logger.info("=== Test Get Invoices by query - emulating 500 from storage ===");

    String endpointQuery = String.format("%s?query=%s", INVOICE_PATH,  ID_FOR_INTERNAL_SERVER_ERROR);

    verifyGet(endpointQuery, APPLICATION_JSON, 500);

  }


  @Test
  void testShouldAlwaysRecalculateTotalAndSubTotalWhenGetInvoicingInvoicesById() {
    logger.info("=== Test Get Invoice By Id ===");

    final Invoice resp = verifySuccessGet(String.format(INVOICE_ID_PATH, APPROVED_INVOICE_ID), Invoice.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertThat(resp.getId(), equalTo(APPROVED_INVOICE_ID));

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    verifyInvoiceUpdateCalls(0);
  }

  @Test
  void testGetOutdatedOpenInvoiceById() {
    logger.info("=== Test Get Invoice By Id - invoice totals are outdated ===");
    Invoice invoice = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    invoice.setStatus(Invoice.Status.OPEN);
    addMockEntry(INVOICES, invoice);

    final Invoice resp = verifySuccessGet(String.format(INVOICE_ID_PATH, invoice.getId()), Invoice.class);

    /* The invoice has 2 not prorated adjustments, 3 related invoice lines and each one has adjustment */
    assertThat(resp.getAdjustmentsTotal(), equalTo(7.17d));
    assertThat(resp.getSubTotal(), equalTo(10.62d));
    assertThat(resp.getTotal(), equalTo(17.79d));

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    verifyInvoiceUpdateCalls(0);
  }

  @Test
  void testGetInvoiceWithoutLines() throws IOException {
    logger.info("=== Test Get Invoice without associated invoice lines ===");

    // ===  Preparing invoice for test with random id to make sure no lines exists  ===
    String id = UUID.randomUUID().toString();
    Invoice invoice = new JsonObject(getMockData(APPROVED_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);
    invoice.setStatus(Invoice.Status.OPEN);
    invoice.setId(id);
    invoice.setLockTotal(15d);

    addMockEntry(INVOICES, JsonObject.mapFrom(invoice));

    // ===  Run test  ===
    final Invoice resp = verifySuccessGet(String.format(INVOICE_ID_PATH, id), Invoice.class);

    /* The invoice has 2 not prorated adjustments one with fixed amount and another with percentage type */
    assertThat(resp.getAdjustmentsTotal(), equalTo(5.06d));
    assertThat(resp.getSubTotal(), equalTo(0d));
    assertThat(resp.getTotal(), equalTo(5.06d));

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    verifyInvoiceUpdateCalls(0);
  }

  @Test
  void testGetInvoiceWithoutLinesButProratedAdjustments() throws IOException {
    logger.info("=== Test Get Invoice without associated invoice lines ===");

    // ===  Preparing invoice for test with random id to make sure no lines exists  ===
    String id = UUID.randomUUID().toString();
    Invoice invoice = new JsonObject(getMockData(OPEN_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);
    invoice.setId(id);
    invoice.getAdjustments().forEach(adj -> adj.setProrate(Adjustment.Prorate.BY_LINE));
    // Setting totals to verify that they are re-calculated
    Adjustment adjustment1 = new Adjustment()
      .withProrate(Prorate.NOT_PRORATED)
      .withDescription("Description")
      .withType(Type.AMOUNT)
      .withValue(50d)
      .withRelationToTotal(Adjustment.RelationToTotal.IN_ADDITION_TO);

    FundDistribution fundDistribution1 = new FundDistribution()
      .withDistributionType(AMOUNT)
      .withValue(50d)
      .withFundId(EXISTING_FUND_ID);

    adjustment1.getFundDistributions().add(fundDistribution1);
    invoice.getAdjustments().add(adjustment1);

    addMockEntry(INVOICES, JsonObject.mapFrom(invoice));

    // ===  Run test  ===
    final Invoice resp = verifySuccessGet(String.format(INVOICE_ID_PATH, id), Invoice.class);

    logger.info(JsonObject.mapFrom(resp).encodePrettily());
    assertThat(resp.getId(), equalTo(id));

    /* The invoice has 2 not prorated adjustments one with fixed amount and another with percentage type */
    assertThat(resp.getAdjustmentsTotal(), equalTo(50d));
    assertThat(resp.getSubTotal(), equalTo(0d));
    assertThat(resp.getTotal(), equalTo(50d));

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    verifyInvoiceUpdateCalls(0);
  }

  @Test
  void testGetInvoiceByIdUpdateTotalException() {
    logger.info("=== Test success response when error happens while updating record back in storage ===");

    Invoice invoice = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class)
      .withId(ID_FOR_INTERNAL_SERVER_ERROR_PUT)
      .withStatus(Invoice.Status.OPEN)
      .withTotal(100.500d);

    addMockEntry(INVOICES, invoice);

    verifySuccessGet(String.format(INVOICE_ID_PATH, invoice.getId()), Invoice.class);
  }

  @Test
  void testGetInvoicingInvoicesByIdNotFound() {
    logger.info("=== Test Get Invoices by Id - 404 Not found ===");

    final Response resp = verifyGet(String.format(INVOICE_ID_PATH, BAD_INVOICE_ID), APPLICATION_JSON, 404);

    String actual = resp.getBody().as(Errors.class).getErrors().get(0).getMessage();
    logger.info("Id not found: " + actual);

    assertEquals(BAD_INVOICE_ID, actual);

    // Verify that expected number of external calls made
    assertThat(getInvoiceLineSearches(), empty());
    verifyInvoiceUpdateCalls(0);
  }

  @Test
  void testUpdateValidInvoice() {
    logger.info("=== Test update invoice by id ===");

    String newInvoiceNumber = "testFolioInvoiceNumber";

    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setFolioInvoiceNo(newInvoiceNumber);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, "", 204);
    assertThat(getInvoiceUpdates().get(0).getString(FOLIO_INVOICE_NUMBER), not(newInvoiceNumber));
  }

  @Test
  void testTransitionFromOpenToApproved() {
    logger.info("=== Test transition invoice to Approved ===");

    Adjustment adjustment1 = new Adjustment()
      .withProrate(Prorate.NOT_PRORATED)
      .withDescription("Description")
      .withType(Type.AMOUNT)
      .withValue(50d)
      .withRelationToTotal(Adjustment.RelationToTotal.IN_ADDITION_TO);

    FundDistribution fundDistribution1 = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(100d)
      .withFundId(EXISTING_FUND_ID);

    Adjustment adjustment2 = new Adjustment()
      .withProrate(Prorate.NOT_PRORATED)
      .withDescription("Description")
      .withType(Type.AMOUNT)
      .withValue(50d)
      .withRelationToTotal(Adjustment.RelationToTotal.IN_ADDITION_TO);

    FundDistribution fundDistribution2 = new FundDistribution()
      .withDistributionType(AMOUNT)
      .withValue(50d)
      .withFundId(EXISTING_FUND_ID);

    adjustment1.getFundDistributions().add(fundDistribution1);
    adjustment2.getFundDistributions().add(fundDistribution2);

    List<InvoiceLine> invoiceLines = getMockAsJson(INVOICE_LINES_LIST_PATH).mapTo(InvoiceLineCollection.class).getInvoiceLines();
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.getAdjustments().add(adjustment1);
    reqData.getAdjustments().add(adjustment2);
    String id = reqData.getId();
    invoiceLines
      .forEach(invoiceLine -> {
        invoiceLine.setId(UUID.randomUUID().toString());
        invoiceLine.setInvoiceId(id);
        invoiceLine.getFundDistributions().forEach(fundDistribution -> fundDistribution.setCode(null));
        addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
      });

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    verifyTransitionToApproved(voucherCreated, invoiceLines, updatedInvoice, 5);
    verifyVoucherLineWithExpenseClasses(2L);
    checkVoucherAcqUnitIdsList(voucherCreated, reqData);
  }

  @Test
  void testTransitionFromOpenToApprovedWithAmountTypeFundDistributions() {
    logger.info("=== Test transition invoice to Approved ===");

    List<InvoiceLine> invoiceLines = getMockAsJson(INVOICE_LINES_LIST_PATH).mapTo(InvoiceLineCollection.class).getInvoiceLines();
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    invoiceLines
      .forEach(invoiceLine -> {
        invoiceLine.setId(UUID.randomUUID().toString());
        invoiceLine.setInvoiceId(id);

        // prepare fund distributions
        BigDecimal total = BigDecimal.valueOf(invoiceLine.getSubTotal())
          .add(BigDecimal.valueOf(invoiceLine.getAdjustmentsTotal()));
        double fundDistrValue = total
          .divide(BigDecimal.valueOf(invoiceLine.getFundDistributions().size()), 2, RoundingMode.HALF_EVEN)
          .doubleValue();
        invoiceLine.getFundDistributions()
          .forEach(fundDistribution -> fundDistribution.withDistributionType(AMOUNT)
            .withCode(null)
            .setValue(fundDistrValue));
        var invoiceLineSum = invoiceLine.getFundDistributions()
          .stream()
          .map(FundDistribution::getValue)
          .map(BigDecimal::valueOf)
          .reduce(BigDecimal::add)
          .get();
        var reminder = total.subtract(invoiceLineSum);
        var fd1Value = BigDecimal.valueOf(invoiceLine.getFundDistributions().get(0).getValue());
        invoiceLine.getFundDistributions().get(0).setValue(fd1Value.add(reminder).doubleValue());

        addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
      });

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    verifyTransitionToApproved(voucherCreated, invoiceLines, updatedInvoice, 5);
    verifyVoucherLineWithExpenseClasses(2L);
    checkVoucherAcqUnitIdsList(voucherCreated, reqData);
  }

  @Test
  void testTransitionFromApprovedToPaidWithDifferentFundDistributions() {
    logger.info("=== Test transition invoice to Paid with different FundDistributions ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_FOR_MOVE_PAYMENT_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(APPROVED_INVOICE_FOR_MOVE_PAYMENT_PATH).mapTo(Invoice.class);
    Voucher voucher = getMockAsJson(VOUCHER_FOR_MOVE_PAYMENT_PATH).mapTo(Voucher.class);
    String id = reqData.getId();

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    addMockEntry(VOUCHERS_STORAGE, JsonObject.mapFrom(voucher));
    reqData.setStatus(Status.PAID);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    assertEquals(Status.PAID, updatedInvoice.getStatus());
  }

  @Test
  void testInvoiceTransitionApprovedWithZeroDollarAmount() {
    logger.info("=== Test transition invoice to Approved with 0$ amount ===");

    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    invoice.setTotal(0.0);
    String id = invoice.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLineWithZeroAmount(id);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId("1d1574f1-9196-4a57-8d1f-3b2e4309eb81")
      .withDistributionType(PERCENTAGE)
      .withValue(100d);

    invoiceLine.getFundDistributions().add(amountDistribution);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    invoice.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(invoice).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    verifyTransitionToApproved(voucherCreated, Collections.singletonList(invoiceLine), updatedInvoice, 1);
    checkVoucherAcqUnitIdsList(voucherCreated, invoice);
  }


  @Test
  void testInvoiceTransitionApprovedWithOddNumberOfPennies() {
    logger.info("=== Test transition invoice to Approved with odd number of pennies ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINES_LIST_PATH).mapTo(InvoiceLineCollection.class).getInvoiceLines().get(0);
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    invoice.getAdjustments().clear();
    invoice.getAdjustments().add(createAdjustment(Prorate.BY_LINE, Type.AMOUNT, 4d));
    String id = invoice.getId();

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);

    var conversionFactor = 3d;

    invoice.setExchangeRate(conversionFactor);

    List<FundDistribution> fundDistrList = new ArrayList<>();

    invoiceLine.setSubTotal(20.01d);
    invoiceLine.getAdjustments().clear();

    fundDistrList.add(new FundDistribution().withFundId("1d1574f1-9196-4a57-8d1f-3b2e4309eb81").withDistributionType(PERCENTAGE).withValue(50d));
    fundDistrList.add(new FundDistribution().withFundId("55f48dc6-efa7-4cfe-bc7c-4786efe493e3").withDistributionType(PERCENTAGE).withValue(50d));

    invoiceLine.setFundDistributions(fundDistrList);
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    invoice.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(invoice).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    verifyTransitionToApproved(voucherCreated, Collections.singletonList(invoiceLine), updatedInvoice, 2);
    checkVoucherAcqUnitIdsList(voucherCreated, invoice);

    List<JsonObject> batchCalls = serverRqRs.get(FINANCE_BATCH_TRANSACTIONS, HttpMethod.POST);
    assertThat(batchCalls, hasSize(1));
    List<Transaction> pendingPayments = batchCalls.stream()
      .map(entries -> entries.mapTo(Batch.class))
      .map(Batch::getTransactionsToCreate)
      .flatMap(Collection::stream)
      .toList();
    assertThat(pendingPayments, Every.everyItem(hasProperty("transactionType",
      is(TransactionType.PENDING_PAYMENT))));
    Double transactionsAmount = pendingPayments.stream()
      .map(tr -> Money.of(tr.getAmount(), tr.getCurrency()))
      .reduce(Money::add)
      .get()
      .getNumber()
      .doubleValue();

    InvoiceLine invLineWithRecalculatedTotals = serverRqRs.get(INVOICE_LINES, HttpMethod.PUT).get(0).mapTo(InvoiceLine.class);
    assertEquals(invLineWithRecalculatedTotals.getTotal(), transactionsAmount);

    // check voucher totals
    Voucher voucher = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST)
      .stream()
      .map(transaction -> transaction.mapTo(Voucher.class))
      .collect(toList())
      .get(0);

    List<VoucherLine> voucherLines = serverRqRs.get(VOUCHER_LINES, HttpMethod.POST)
      .stream()
      .map(transaction -> transaction.mapTo(VoucherLine.class))
      .collect(toList());
    Double voucherLinesTotal = voucherLines.stream()
      .map(vLine -> BigDecimal.valueOf(vLine.getAmount()))
      .reduce(BigDecimal::add)
      .get()
      .doubleValue();

    var expectedVoucherAmount = Money.of(invLineWithRecalculatedTotals.getTotal(), invoice.getCurrency()).multiply(conversionFactor).getNumber().doubleValue();
    assertEquals(expectedVoucherAmount, voucher.getAmount());
    assertEquals(expectedVoucherAmount, voucherLinesTotal);
  }

  @Test
  void testTransitionFromOpenToApprovedWithNotEnoughMoney() {
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(60d);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(FUND_ID_WITH_NOT_ENOUGH_AMOUNT_IN_BUDGET)
      .withDistributionType(AMOUNT)
      .withValue(60d);

    invoiceLine.getFundDistributions().add(amountDistribution);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 422).as(Errors.class);
    assertThat(errors.getErrors().get(0).getCode(), equalTo(FUND_CANNOT_BE_PAID.getCode()));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(FUND_CANNOT_BE_PAID.getDescription()));
  }

  @Test
  void testTransitionFromOpenToApprovedWithoutSpecifiedAllowableExpenditure() {
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(50d);
    invoiceLine.setId(UUID.randomUUID().toString());

    String fundId = UUID.randomUUID().toString();

    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withAllocated(45d)
      .withAvailable(45d)
      .withBudgetStatus(BudgetStatus.ACTIVE)
      .withUnavailable(0d);

    Fund fund = new Fund()
      .withId(fundId)
      .withName("test")
      .withLedgerId(EXISTING_LEDGER_ID)
      .withCode("FC")
      .withExternalAccountNo("1234")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(fundId)
      .withDistributionType(AMOUNT)
      .withValue(50d);

    invoiceLine.getFundDistributions().add(amountDistribution);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    addMockEntry(BUDGETS, JsonObject.mapFrom(budget));
    addMockEntry(FUNDS, JsonObject.mapFrom(fund));

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);
  }

  @Test
  void testTransitionFromOpenToApprovedWithRestrictExpenditures() {
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(50d);
    invoiceLine.setId(UUID.randomUUID().toString());

    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withAllocated(50d)
      .withAvailable(50d)
      .withBudgetStatus(BudgetStatus.ACTIVE)
      .withUnavailable(0d);

    Fund fund = new Fund()
      .withId(fundId)
      .withExternalAccountNo("test")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);


    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(false);

    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(fundId)
      .withDistributionType(AMOUNT)
      .withValue(50d);

    invoiceLine.getFundDistributions().add(amountDistribution);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    addMockEntry(BUDGETS, JsonObject.mapFrom(budget));
    addMockEntry(FUNDS, JsonObject.mapFrom(fund));
    addMockEntry(LEDGERS, JsonObject.mapFrom(ledger));

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);
  }

  @Test
  void testTransitionFromOpenToApprovedWithMixedTypesFundDistributionsAndWithoutLockTotal() {
    logger.info("=== Test transition invoice to Approved ===");

    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(100d);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution percentageDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withCode(null)
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(50d);
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withDistributionType(AMOUNT)
      .withValue(50d);

    invoiceLine.getFundDistributions().addAll(Arrays.asList(percentageDistribution, amountDistribution));

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    List<JsonObject> fundsSearches = serverRqRs.get(FUNDS, HttpMethod.GET);
    List<Fund> funds = fundsSearches.get(0).mapTo(FundCollection.class).getFunds();
    verifyTransitionToApproved(voucherCreated, Collections.singletonList(invoiceLine), updatedInvoice,  getExpectedVoucherLinesQuantity(funds));
    checkVoucherAcqUnitIdsList(voucherCreated, reqData);
  }

  @Test
  void testTransitionFromOpenToApprovedWithMixedTypesFundDistributionsAndWithLockTotalWhichEqualToTotal() {
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(100d);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution percentageDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withCode(null)
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(50d);
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withDistributionType(AMOUNT)
      .withValue(50d);

    invoiceLine.getFundDistributions().addAll(Arrays.asList(percentageDistribution, amountDistribution));

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);
    reqData.setLockTotal(100d);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    List<JsonObject> fundsSearches = serverRqRs.get(FUNDS, HttpMethod.GET);
    List<Fund> funds = fundsSearches.get(0).mapTo(FundCollection.class).getFunds();
    verifyTransitionToApproved(voucherCreated, Collections.singletonList(invoiceLine), updatedInvoice,  getExpectedVoucherLinesQuantity(funds));
    checkVoucherAcqUnitIdsList(voucherCreated, reqData);
  }

  @Test
  void testTransitionFromOpenToApprovedWithMixedTypesFundDistributionsAndWithLockTotalWhichEqualToDecimalTotal() {
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(100.18d);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution percentageDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withCode(null)
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(50d);
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withDistributionType(AMOUNT)
      .withValue(50.09d);

    invoiceLine.getFundDistributions().addAll(Arrays.asList(percentageDistribution, amountDistribution));

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);
    reqData.setLockTotal(100.18d);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getVoucherNumber(), equalTo(TEST_PREFIX + VOUCHER_NUMBER_VALUE));
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    List<JsonObject> fundsSearches = serverRqRs.get(FUNDS, HttpMethod.GET);
    List<Fund> funds = fundsSearches.get(0).mapTo(FundCollection.class).getFunds();
    verifyTransitionToApproved(voucherCreated, Collections.singletonList(invoiceLine), updatedInvoice,  getExpectedVoucherLinesQuantity(funds));
    checkVoucherAcqUnitIdsList(voucherCreated, reqData);
  }

  @Test
  void testShouldThrowExceptionTransitionFromOpenToApprovedWithMixedTypesFundDistributionsAndWithLockTotalWhichNotEqualToCalculatedTotal() {
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(100d);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution percentageDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withCode(null)
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(50d);
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withDistributionType(AMOUNT)
      .withValue(50d);

    invoiceLine.getFundDistributions().addAll(Arrays.asList(percentageDistribution, amountDistribution));

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);
    reqData.setLockTotal(15.4d);
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400).as(Errors.class);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));
    List<JsonObject> updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT);
    assertThat(updatedInvoice, nullValue());
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, nullValue());

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(LOCK_AND_CALCULATED_TOTAL_MISMATCH.getDescription()));
    assertThat(error.getCode(), equalTo(LOCK_AND_CALCULATED_TOTAL_MISMATCH.getCode()));
  }



  @Test
  void testTransitionFromOpenToApprovedWithMixedTypesFundDistributionsInvalidSummary() {
    logger.info("=== Test transition invoice to Approved ===");

    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);
    invoiceLine.setSubTotal(100d);
    invoiceLine.setId(UUID.randomUUID().toString());
    FundDistribution percentageDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(33.33333333d);
    FundDistribution amountDistribution = new FundDistribution()
      .withFundId(EXISTING_FUND_ID)
      .withDistributionType(AMOUNT)
      .withValue(66.66d);

    invoiceLine.getFundDistributions().addAll(Arrays.asList(percentageDistribution, amountDistribution));

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 422)
      .as(Errors.class);

    // Verify that expected number of external calls made
    assertThat(getInvoiceRetrievals(), hasSize(1));
    assertThat(getInvoiceLineSearches(), hasSize(1));

    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, nullValue());
    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(INCORRECT_FUND_DISTRIBUTION_TOTAL.getDescription()));
    assertThat(error.getCode(), equalTo(INCORRECT_FUND_DISTRIBUTION_TOTAL.getCode()));
  }

  @Test
  void testTransitionFromOpenToApprovedWithMultipleFiscalYears() {
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH_WITHOUT_FISCAL_YEAR).mapTo(Invoice.class);
    String invoiceId = invoice.getId();

    InvoiceLine invoiceLine1 = getMinimalContentInvoiceLine(invoiceId);

    Fund fund1 = new Fund()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(EXISTING_LEDGER_ID)
      .withCode("FUND1")
      .withExternalAccountNo("1234")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget1 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFundId(fund1.getId())
      .withFiscalYearId(UUID.randomUUID().toString())
      .withAllocated(100d)
      .withAvailable(100d)
      .withBudgetStatus(BudgetStatus.ACTIVE)
      .withUnavailable(0d);

    FundDistribution fd1 = new FundDistribution()
      .withFundId(fund1.getId())
      .withDistributionType(PERCENTAGE)
      .withValue(100d)
      .withCode(fund1.getCode());
    invoiceLine1.getFundDistributions().add(fd1);

    InvoiceLine invoiceLine2 = getMinimalContentInvoiceLine(invoiceId);

    Fund fund2 = new Fund()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(EXISTING_LEDGER_ID)
      .withCode("FUND2")
      .withExternalAccountNo("1234")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget2 = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFundId(fund2.getId())
      .withFiscalYearId(UUID.randomUUID().toString())
      .withAllocated(100d)
      .withAvailable(100d)
      .withBudgetStatus(BudgetStatus.ACTIVE)
      .withUnavailable(0d);

    FundDistribution fd2 = new FundDistribution()
      .withFundId(fund2.getId())
      .withDistributionType(PERCENTAGE)
      .withValue(100d)
      .withCode(fund2.getCode());
    invoiceLine2.getFundDistributions().add(fd2);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine1));
    addMockEntry(BUDGETS, JsonObject.mapFrom(budget1));
    addMockEntry(FUNDS, JsonObject.mapFrom(fund1));
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine2));
    addMockEntry(BUDGETS, JsonObject.mapFrom(budget2));
    addMockEntry(FUNDS, JsonObject.mapFrom(fund2));

    invoice.setStatus(Invoice.Status.APPROVED);

    String jsonBody = JsonObject.mapFrom(invoice).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, invoiceId), jsonBody, headers, "", 422)
      .as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    String errorMessage = errors.getErrors().get(0).getMessage();
    String possibleMessage1 = "Multiple fiscal years are used with the funds " + fund1.getCode() + " and " + fund2.getCode() + ".";
    String possibleMessage2 = "Multiple fiscal years are used with the funds " + fund2.getCode() + " and " + fund1.getCode() + ".";
    assertTrue(possibleMessage1.equals(errorMessage) || possibleMessage2.equals(errorMessage));
  }

  private Invoice createMockEntryInStorage() {
    // Add mock entry in storage with status Paid
    Invoice invoice = getMinimalContentInvoice();
    String id = invoice.getId();
    invoice.setStatus(Invoice.Status.PAID);
    MockServer.addMockEntry(INVOICES, invoice);

    Invoice reqData = getRqRsEntries(HttpMethod.SEARCH, INVOICES).get(0)
      .mapTo(Invoice.class);

    List<InvoiceLine> invoiceLines = getMockAsJson(INVOICE_LINES_LIST_PATH).mapTo(InvoiceLineCollection.class)
      .getInvoiceLines();
    invoiceLines.forEach(invoiceLine -> {
      invoiceLine.setId(UUID.randomUUID()
        .toString());
      invoiceLine.setInvoiceId(id);
      addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    });
    return reqData;
  }

  private void verifyInvoiceTransitionFailure(Invoice reqData) {
    String jsonBody = JsonObject.mapFrom(reqData)
      .encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    final Errors errors = verifyPut(String.format(INVOICE_ID_PATH, reqData.getId()), jsonBody, headers, "", 422).then()
      .extract()
      .body()
      .as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    final Error error = errors.getErrors()
      .get(0);
    assertThat(error.getMessage(), equalTo(INVALID_INVOICE_TRANSITION_ON_PAID_STATUS.getDescription()));
    assertThat(error.getCode(), equalTo(INVALID_INVOICE_TRANSITION_ON_PAID_STATUS.getCode()));
    assertThat(error.getParameters()
      .get(0)
      .getValue(), containsString(reqData.getId()));

    // Verify invoice not updated
    assertThat(getInvoiceUpdates(), hasSize(0));
  }

  @Test
  void testInvoiceTransitionFailureOnPaidStatus() {
    logger.info(
      "=== Test invoice cannot be transitioned to \"Open\", \"Reviewed\" or \"Approved\" status if it is in \"Paid\" status ===");

    for (Status status : Invoice.Status.values()) {
      if (status != Invoice.Status.PAID && status != Status.CANCELLED) {
        Invoice reqData = createMockEntryInStorage();

        // Try to update storage entry
        reqData.setStatus(status);

        verifyInvoiceTransitionFailure(reqData);
      }
    }
  }

  @Test
  void testTransitionFromOpenToApprovedWithMissingExternalAccountNumber() {
    logger.info("=== Test transition invoice to Approved with missing external account number ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().get(0).setFundId(FUND_ID_WITHOUT_EXTERNAL_ACCOUNT_NUMBER);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();

    Headers headers = prepareHeaders(X_OKAPI_USER_ID, X_OKAPI_TENANT);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(EXTERNAL_ACCOUNT_NUMBER_IS_MISSING.getDescription()));
    assertThat(error.getCode(), equalTo(EXTERNAL_ACCOUNT_NUMBER_IS_MISSING.getCode()));
    assertThat(error.getParameters().get(0).getValue(), containsString(FUND_ID_WITHOUT_EXTERNAL_ACCOUNT_NUMBER));
  }

  @Test
  void testTransitionToApprovedWithEmptyConfig() {
    logger.info("=== Test transition invoice to Approved with empty config ===");

    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    Headers nonExistConfigHeaders = prepareHeaders(X_OKAPI_URL, NON_EXIST_CONFIG_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifySuccessfulPut(prepareInvoiceLines(reqData), reqData, id, jsonBody, nonExistConfigHeaders);

    MockServer.release();

    Headers nonExistValueConfigHeaders = prepareHeaders(X_OKAPI_URL, PREFIX_CONFIG_WITHOUT_VALUE_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifySuccessfulPut(prepareInvoiceLines(reqData), reqData, id, jsonBody, nonExistValueConfigHeaders);

    MockServer.release();

    Headers nonExistNeededValueConfigHeaders = prepareHeaders(X_OKAPI_URL, PREFIX_CONFIG_WITH_NON_EXISTING_VALUE_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifySuccessfulPut(prepareInvoiceLines(reqData), reqData, id, jsonBody, nonExistNeededValueConfigHeaders);
  }

  private List<InvoiceLine> prepareInvoiceLines(Invoice reqData) {
    List<InvoiceLine> invoiceLines = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      invoiceLines.add(getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class));
    }

    invoiceLines
      .forEach(invoiceLine -> {
        invoiceLine.setId(UUID.randomUUID().toString());
        invoiceLine.setInvoiceId(reqData.getId());
        invoiceLine.getFundDistributions().forEach(fundDistribution -> fundDistribution.setCode(null));
        addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
      });
    return invoiceLines;
  }

  private void verifySuccessfulPut(List<InvoiceLine> invoiceLines, Invoice reqData, String id, String jsonBody, Headers headers) {
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);
    List<JsonObject> vouchersCreated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.POST);
    assertThat(vouchersCreated, notNullValue());
    assertThat(vouchersCreated, hasSize(1));
    Voucher voucherCreated = vouchersCreated.get(0).mapTo(Voucher.class);
    assertThat(voucherCreated.getSystemCurrency(), equalTo(DEFAULT_SYSTEM_CURRENCY));
    assertThat(voucherCreated.getVoucherNumber(), equalTo(VOUCHER_NUMBER_VALUE));
    List<JsonObject> fundsSearches = serverRqRs.get(FUNDS, HttpMethod.GET);
    List<Fund> funds = fundsSearches.get(0).mapTo(FundCollection.class).getFunds();
    verifyTransitionToApproved(voucherCreated, invoiceLines, updatedInvoice,  getExpectedVoucherLinesQuantity(funds));
    checkVoucherAcqUnitIdsList(voucherCreated, reqData);
  }

  @Test
  void testTransitionToApprovedWithExistingVoucherAndVoucherLines() {
    logger.info("=== Test transition invoice to Approved with existing voucher and voucherLines ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);


    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    invoiceLine.getFundDistributions().forEach(fundDistribution -> fundDistribution.setCode(null));
    prepareMockVoucher(reqData.getId());
    VoucherLine voucherLine = new VoucherLine().withVoucherId(EXISTING_VOUCHER_ID);
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    addMockEntry(VOUCHER_LINES, JsonObject.mapFrom(voucherLine));

    reqData.setStatus(Invoice.Status.APPROVED);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);
    List<JsonObject> vouchersUpdated = serverRqRs.get(VOUCHERS_STORAGE, HttpMethod.PUT);
    List<JsonObject> voucherLinesDeletions = serverRqRs.get(VOUCHER_LINES, HttpMethod.DELETE);
    List<JsonObject> voucherLinesSearches = serverRqRs.get(VOUCHER_LINES, HttpMethod.GET);

    assertThat(vouchersUpdated, notNullValue());
    assertThat(vouchersUpdated, hasSize(1));
    assertThat(voucherLinesDeletions, notNullValue());
    assertThat(voucherLinesSearches, notNullValue());
    assertThat(voucherLinesSearches, hasSize(1));
    VoucherLineCollection voucherLineCollection = voucherLinesSearches.get(0).mapTo(VoucherLineCollection.class);
    assertThat(voucherLinesDeletions, hasSize(voucherLineCollection.getTotalRecords()));


    Voucher updatedVoucher = vouchersUpdated.get(0).mapTo(Voucher.class);
    Invoice updatedInvoice = serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).mapTo(Invoice.class);

    List<JsonObject> fundsSearches = serverRqRs.get(FUNDS, HttpMethod.GET);
    List<Fund> funds = fundsSearches.get(0).mapTo(FundCollection.class).getFunds();
    verifyTransitionToApproved(updatedVoucher, Collections.singletonList(invoiceLine), updatedInvoice,  getExpectedVoucherLinesQuantity(funds));
  }

  @Test
  void testTransitionToApprovedWithInvalidVoucherNumberPrefix() {
    logger.info("=== Test transition invoice to Approved with invalid voucher number prefix ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, INVALID_PREFIX_CONFIG_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_SAMPLE_PATH, headers,400);

    List<JsonObject> voucherNumberGeneration = serverRqRs.get(VOUCHER_NUMBER_STORAGE, HttpMethod.GET);

    assertThat(voucherNumberGeneration, nullValue());
    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(VOUCHER_NUMBER_PREFIX_NOT_ALPHA.getDescription()));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(VOUCHER_NUMBER_PREFIX_NOT_ALPHA.getCode()));
  }

  @Test
  void testTransitionToApprovedWithoutPremission() {
    logger.info("=== Test transition invoice to Approved without premission ===");

    var headers = prepareHeadersWithoutPermissions(X_OKAPI_URL, INVALID_PREFIX_CONFIG_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID, X_OKAPI_PERMISSION_WITHOUT_PAY_APPROVE);
    var errors = transitionToApprovedWithoutPermission(REVIEWED_INVOICE_SAMPLE_PATH, headers, 403);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(USER_HAS_NO_APPROVE_PERMISSIONS.getDescription()));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_APPROVE_PERMISSIONS.getCode()));
  }

  @Test
  void testTransitionToPaidWithoutPremission() {
    logger.info("=== Test transition invoice to paid without premission ===");

    var headers = prepareHeadersWithoutPermissions(X_OKAPI_URL, INVALID_PREFIX_CONFIG_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID, X_OKAPI_PERMISSION_WITHOUT_PAY);
    var errors = transitionToPaidWithoutPermission(APPROVED_INVOICE_SAMPLE_PATH, headers, 403);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(USER_HAS_NO_PAY_PERMISSIONS.getDescription()));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(USER_HAS_NO_PAY_PERMISSIONS.getCode()));
  }

  @Test
  void testTransitionToApprovedInvalidCurrency() {
    logger.info("=== Test transition invoice to Approved with invalid invoiceCurrency ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);


    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));


    reqData.setStatus(Invoice.Status.APPROVED);
    reqData.setCurrency(INVALID_CURRENCY);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();

    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 500)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getAdditionalProperties().get("cause"), equalTo("Unknown currency code: " + INVALID_CURRENCY));
  }

  @Test
  void testTransitionToApprovedErrorFromModConfig() {
    logger.info("=== Test transition invoice to Approved with mod-config error ===");

    var headers = prepareHeaders(X_OKAPI_URL, ERROR_CONFIG_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    var errors = transitionToApprovedWithError(REVIEWED_INVOICE_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
  }

  private Errors transitionToApprovedWithError(String invoiceSamplePath, Headers headers) {
    return transitionToApprovedWithError(invoiceSamplePath, headers, 500);
  }

  private Errors transitionToApprovedWithError(String invoiceSamplePath, Headers headers, int expectedCode) {
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    Invoice reqData = getMockAsJson(invoiceSamplePath).mapTo(Invoice.class);
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Invoice.Status.APPROVED);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    return verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, expectedCode)
      .then()
      .extract()
      .body().as(Errors.class);
  }

  private Errors transitionToPaymentWithError(String invoiceSamplePath, Headers headers, int expectedCode) {
    InvoiceLine invoiceLine = getMockAsJson(APPROVED_INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    Invoice reqData = getMockAsJson(invoiceSamplePath).mapTo(Invoice.class);
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    reqData.setStatus(Status.PAID);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    return verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, expectedCode)
      .then()
      .extract()
      .body().as(Errors.class);
  }

  private Errors transitionToApprovedWithoutPermission(String invoiceSamplePath, Headers headers, int expectedCode) {

    Invoice reqData = getMockAsJson(invoiceSamplePath).mapTo(Invoice.class);
    reqData.setStatus(Status.APPROVED);
    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    return verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, expectedCode)
      .then()
      .extract()
      .body().as(Errors.class);
  }

  private Errors transitionToPaidWithoutPermission(String invoiceSamplePath, Headers headers, int expectedCode) {

    Invoice reqData = getMockAsJson(invoiceSamplePath).mapTo(Invoice.class);
    reqData.setStatus(Status.PAID);
    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    return verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, expectedCode)
      .then()
      .extract()
      .body().as(Errors.class);
  }

  @Test
  void testTransitionToApprovedWithoutInvoiceLines() {
    logger.info("=== Test transition invoice to Approved without invoiceLines should fail ===");

    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);

    reqData.setStatus(Invoice.Status.APPROVED);

    String id = reqData.getId();
    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(NO_INVOICE_LINES_ERROR_MSG));

  }

  @Test
  void testTransitionToApprovedWithFundDistributionsNull() {
    logger.info("=== Test transition invoice to Approved with Fund Distributions empty will fail ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.setFundDistributions(null);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(FUND_DISTRIBUTIONS_NOT_PRESENT.getDescription()));
    assertThat(error.getCode(), equalTo(FUND_DISTRIBUTIONS_NOT_PRESENT.getCode()));
  }

  @Test
  void testTransitionToApprovedWithFundDistributionsEmpty() {
    logger.info("=== Test transition invoice to Approved with Fund Distributions empty will fail ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.setFundDistributions(new ArrayList<>());

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(FUND_DISTRIBUTIONS_NOT_PRESENT.getDescription()));
    assertThat(error.getCode(), equalTo(FUND_DISTRIBUTIONS_NOT_PRESENT.getCode()));
  }

  @Test
  void testTransitionToApprovedWithFundDistributionsTotalPercentageNot100() {
    logger.info("=== Test transition invoice to Approved with Fund Distributions empty will fail ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().get(0).setDistributionType(FundDistribution.DistributionType.PERCENTAGE);
    invoiceLine.getFundDistributions().get(0).setValue(1d);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 422)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(INCORRECT_FUND_DISTRIBUTION_TOTAL.getDescription()));
    assertThat(error.getCode(), equalTo(INCORRECT_FUND_DISTRIBUTION_TOTAL.getCode()));
  }

  @Test
  void testTransitionToApprovedWithFundDistributionsTotalAmountNotEqualToLineTotal() {
    logger.info("=== Test transition invoice to Approved with Fund Distributions empty will fail ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().get(0).setDistributionType(AMOUNT);
    invoiceLine.getFundDistributions().get(0).setValue(100d);
    invoiceLine.setSubTotal(300d);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 422)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(INCORRECT_FUND_DISTRIBUTION_TOTAL.getDescription()));
    assertThat(error.getCode(), equalTo(INCORRECT_FUND_DISTRIBUTION_TOTAL.getCode()));
  }

  @Test
  void testTransitionToApprovedWithAdjustmentFundDistributionsTotalAmountNotEqualToAdjustmentTotal() {
    logger.info("=== Test transition invoice to Approved with adjustment's Fund Distributions amount not equal to adjustment amount will fail ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);

    FundDistribution fundDistribution1 = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withValue(50d)
      .withFundId(UUID.randomUUID().toString());
    FundDistribution fundDistribution2 = new FundDistribution()
      .withDistributionType(AMOUNT)
      .withValue(50d)
      .withFundId(UUID.randomUUID().toString());

    Adjustment adjustment = new Adjustment()
      .withProrate(Prorate.NOT_PRORATED)
      .withDescription("Description")
      .withType(Type.AMOUNT)
      .withValue(50d)
      .withRelationToTotal(Adjustment.RelationToTotal.IN_ADDITION_TO);
    adjustment.getFundDistributions().add(fundDistribution1);
    adjustment.getFundDistributions().add(fundDistribution2);
    reqData.getAdjustments().add(adjustment);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().get(0).setDistributionType(FundDistribution.DistributionType.PERCENTAGE);
    invoiceLine.getFundDistributions().get(0).setValue(100d);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 422)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo( INCORRECT_FUND_DISTRIBUTION_TOTAL.getDescription()));
    assertThat(error.getCode(), equalTo( INCORRECT_FUND_DISTRIBUTION_TOTAL.getCode()));
  }

  @Test
  void testTransitionToApprovedWithAdjustmentFundDistributionsNotPresent() {
    logger.info("=== Test transition invoice to Approved with adjustment's Fund Distributions empty will fail ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);

    Adjustment adjustment = new Adjustment()
      .withProrate(Prorate.NOT_PRORATED)
      .withDescription("Description")
      .withType(Type.AMOUNT)
      .withValue(50d)
      .withRelationToTotal(Adjustment.RelationToTotal.IN_ADDITION_TO);

    reqData.getAdjustments().add(adjustment);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().get(0).setDistributionType(FundDistribution.DistributionType.PERCENTAGE);
    invoiceLine.getFundDistributions().get(0).setValue(100d);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT.getDescription()));
    assertThat(error.getCode(), equalTo(ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT.getCode()));
  }

  @Test
  void testTransitionToApprovedWithoutFundDistrEncumbranceId() {
    logger.info("=== Test transition invoice to Approved without links to encumbrances ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);

    String id = reqData.getId();

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().forEach(fundDistribution -> fundDistribution.setEncumbrance(null));
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    List<JsonObject> pendingPaymentsCreated = serverRqRs.get(FINANCE_BATCH_TRANSACTIONS, HttpMethod.POST);

    int numPendingPayments = invoiceLine.getFundDistributions().size();
    assertThat(pendingPaymentsCreated, hasSize(numPendingPayments));
  }

  @Test
  void testTransitionToApprovedWithFundDistrEncumbranceId() {
    logger.info("=== Test transition invoice to Approved with links to encumbrances ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    reqData.setCurrency("GBP");
    reqData.setExchangeRate(1.5d);

    String id = reqData.getId();

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.setAdjustmentsTotal(0d);
    invoiceLine.setAdjustments(emptyList());

    Transaction enc1 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withTransactionType(TransactionType.ENCUMBRANCE)
      .withAmount(1.00)
      .withCurrency("USD")
      .withFromFundId(EXISTING_FUND_ID)
      .withToFundId(EXISTING_FUND_ID)
      .withEncumbrance(new Encumbrance()
        .withSourcePoLineId("0610be6d-0ddd-494b-b867-19f63d8b5d6d"));
    addMockEntry(FINANCE_TRANSACTIONS, enc1);
    Transaction enc2 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withTransactionType(TransactionType.ENCUMBRANCE)
      .withAmount(1.00)
      .withCurrency("USD")
      .withFromFundId(EXISTING_FUND_ID_2)
      .withToFundId(EXISTING_FUND_ID_2)
      .withEncumbrance(new Encumbrance()
        .withSourcePoLineId("0610be6d-0ddd-494b-b867-19f63d8b5d6d"));
    addMockEntry(FINANCE_TRANSACTIONS, enc2);
    Transaction enc3 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withTransactionType(TransactionType.ENCUMBRANCE)
      .withAmount(1.00)
      .withCurrency("USD")
      .withFromFundId(EXISTING_FUND_ID_3)
      .withToFundId(EXISTING_FUND_ID_3)
      .withEncumbrance(new Encumbrance()
        .withSourcePoLineId("0610be6d-0ddd-494b-b867-19f63d8b5d6d"));
    addMockEntry(FINANCE_TRANSACTIONS, enc3);

    invoiceLine.setSubTotal(10d);
    FundDistribution fd1 = new FundDistribution()
      .withDistributionType(FundDistribution.DistributionType.PERCENTAGE)
      .withFundId(EXISTING_FUND_ID)
      .withValue(50d)
      .withEncumbrance(enc1.getId());
    FundDistribution fd2 = new FundDistribution()
      .withDistributionType(AMOUNT)
      .withFundId(EXISTING_FUND_ID_2)
      .withValue(3d)
      .withEncumbrance(enc2.getId());
    FundDistribution fd3 = new FundDistribution()
      .withDistributionType(AMOUNT)
      .withFundId(EXISTING_FUND_ID_3)
      .withValue(2d)
      .withEncumbrance(enc3.getId());

    List<FundDistribution> fundDistributions = Arrays.asList(fd1, fd2, fd3);
    invoiceLine.setFundDistributions(fundDistributions);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    List<JsonObject> batchCalls = serverRqRs.get(FINANCE_BATCH_TRANSACTIONS, HttpMethod.POST);
    assertThat(batchCalls, hasSize(1));
    List<Transaction> pendingPaymentCreated = batchCalls.stream()
      .map(entries -> entries.mapTo(Batch.class))
      .map(Batch::getTransactionsToCreate)
      .flatMap(Collection::stream)
      .toList();
    assertThat(pendingPaymentCreated, hasSize(invoiceLine.getFundDistributions().size()));
    assertThat(pendingPaymentCreated, Every.everyItem(hasProperty("transactionType",
      is(TransactionType.PENDING_PAYMENT))));

    Map<String, Transaction> pendingPaymentMap = pendingPaymentCreated.stream()
      .collect(toMap((transaction)->transaction.getAwaitingPayment().getEncumbranceId(), Function.identity()));
    ConversionQuery conversionQuery = ConversionQueryBuilder.of().setTermCurrency(DEFAULT_SYSTEM_CURRENCY).set(RATE_KEY, 1.5).build();
    ExchangeRateProvider exchangeRateProvider = new ExchangeRateProviderResolver().resolve(conversionQuery, new RequestContext(
      Vertx.currentContext(), Collections.emptyMap()));
    CurrencyConversion conversion = exchangeRateProvider.getCurrencyConversion(conversionQuery); //currency from MockServer
    fundDistributions.forEach(fundDistribution -> {
      MonetaryAmount amount = getFundDistributionAmount(fundDistribution, 10d, reqData.getCurrency()).with(conversion);
      assertThat(convertToDoubleWithRounding(amount), is(pendingPaymentMap.get(fundDistribution.getEncumbrance()).getAmount()));
    });
  }

  @Test
  void testTransitionToApprovedWithoutAccountingCode() {
    logger.info("=== Test transition invoice to Approved without specified accounting code ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    reqData.setCurrency("GBP");
    reqData.setExportToAccounting(true);
    reqData.setAccountingCode(null);

    String id = reqData.getId();


    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);

    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400).then()
      .extract()
      .as(Errors.class);

    assertEquals(ACCOUNTING_CODE_NOT_PRESENT.getCode(), errors.getErrors().get(0).getCode());

  }

  @Test
  void testTransitionToApprovedWithInternalServerErrorFromGetInvoiceLines() {
    logger.info("=== Test transition invoice to Approved with Internal Server Error when retrieving invoiceLines ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.GET_INVOICE_LINES_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()));

  }

  @Test
  void testTransitionToApprovedFundsNotFound() {
    logger.info("=== Test transition invoice to Approved when fund not found ===");

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    Invoice reqData = getMockAsJson(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setStatus(Invoice.Status.APPROVED);
    String id = reqData.getId();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.getFundDistributions().get(0).setFundId(ID_DOES_NOT_EXIST);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));

    String jsonBody = JsonObject.mapFrom(reqData).encode();

    Headers headers = prepareHeaders(X_OKAPI_USER_ID, X_OKAPI_TENANT);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 404)
      .then()
      .extract()
      .body().as(Errors.class);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getMessage(), equalTo(FUNDS_NOT_FOUND.getDescription()));
    assertThat(error.getCode(), equalTo(FUNDS_NOT_FOUND.getCode()));
    assertThat(error.getParameters().get(0).getValue(), containsString(ID_DOES_NOT_EXIST));
  }

  @Test
  void testTransitionToApprovedWithInternalServerErrorFromGetFunds() {
    logger.info("=== Test transition invoice to Approved with Internal Server Error when retrieving funds ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.GET_FUNDS_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
    assertThat(errors.getErrors().get(0).getMessage(), equalTo(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()));
  }

  @Test
  void testTransitionToApprovedWithErrorFromGetVouchersByInvoiceId() {
    logger.info("=== Test transition invoice to Approved when searching existing voucher ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.GET_VOUCHERS_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
  }

  @Test
  void testTransitionToApprovedWithErrorFromUpdateVoucherById() {
    logger.info("=== Test transition invoice to Approved with error when updating existing voucher  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.UPDATE_VOUCHER_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
  }

  @Test
  void testTransitionToApprovedWithErrorFromCreateVoucher() {
    logger.info("=== Test transition invoice to Approved with error when creating voucher  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.CREATE_VOUCHER_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
  }

  @Test
  void testTransitionToApprovedWithErrorFromGetVoucherLines() {
    logger.info("=== Test transition invoice to Approved with error when getting voucherLines  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.GET_VOUCHER_LINE_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_WITH_EXISTING_VOUCHER_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
  }

  @Test
  void testTransitionToApprovedWithErrorFromCreateVoucherLines() {
    logger.info("=== Test transition invoice to Approved with error when creating voucherLines  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.CREATE_VOUCHER_LINE_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_SAMPLE_PATH, headers);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
  }

  @Test
  void testTransitionToApprovedWithInternalErrorFromCreatePendingPayment() {
    logger.info("=== Test transition invoice to Approved with internal server error when creating AwaitingPayment  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.POST_BATCH_TRANSACTIONS_INTERNAL_SERVER_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_SAMPLE_PATH, headers, 500);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
  }

  @Test
  void testTransitionToApprovedWithErrorFromCreatePendingPayment() {
    logger.info("=== Test transition invoice to Approved with error when creating AwaitingPayment  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.POST_BATCH_TRANSACTIONS_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToApprovedWithError(REVIEWED_INVOICE_SAMPLE_PATH, headers, 422);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
  }

  @Test
  void testTransitionToApprovedWithInternalErrorFromCreateCreditPayment() {
    logger.info("=== Test transition invoice to Approved with internal server error when creating payment credit  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.POST_BATCH_TRANSACTIONS_INTERNAL_SERVER_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToPaymentWithError(APPROVED_INVOICE_SAMPLE_PATH, headers, 500);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
  }

  @Test
  void testTransitionToApprovedWithErrorFromCreateCreditPayment() {
    logger.info("=== Test transition invoice to Approved with error when creating payment credit  ===");

    Headers headers = prepareHeaders(X_OKAPI_URL, MockServer.POST_BATCH_TRANSACTIONS_ERROR_X_OKAPI_TENANT, X_OKAPI_TOKEN);
    Errors errors = transitionToPaymentWithError(APPROVED_INVOICE_SAMPLE_PATH, headers, 422);

    assertThat(errors, notNullValue());
    assertThat(errors.getErrors(), hasSize(1));
  }

  private void verifyTransitionToApproved(Voucher voucherCreated, List<InvoiceLine> invoiceLines, Invoice invoice, int createdVoucherLines) {
    List<JsonObject> invoiceLinesSearches = serverRqRs.get(INVOICE_LINES, HttpMethod.GET);
    List<JsonObject> invoiceLinesUpdates = serverRqRs.get(INVOICE_LINES, HttpMethod.PUT);
    List<JsonObject> voucherLinesCreated = serverRqRs.get(VOUCHER_LINES, HttpMethod.POST);
    List<JsonObject> fundsSearches = serverRqRs.get(FUNDS, HttpMethod.GET);
    List<JsonObject> invoiceUpdates = serverRqRs.get(INVOICES, HttpMethod.PUT);
    List<JsonObject> batchCalls = Optional.ofNullable(serverRqRs.get(FINANCE_BATCH_TRANSACTIONS, HttpMethod.POST))
      .orElse(emptyList());

    assertThat(invoiceLinesSearches, notNullValue());
    assertThat(invoiceLinesUpdates, notNullValue());
    assertThat(fundsSearches, notNullValue());
    assertThat(voucherLinesCreated, notNullValue());
    assertThat(invoiceUpdates, notNullValue());

    assertThat(invoiceLinesSearches, hasSize(invoiceLines.size()/MAX_IDS_FOR_GET_RQ + 1));
    List<InvoiceLine> linesWithUpdatedStatus = invoiceLinesUpdates.stream()
      .map(entries -> entries.mapTo(InvoiceLine.class))
      .filter(invoiceLine -> invoiceLine.getInvoiceLineStatus() == InvoiceLine.InvoiceLineStatus.APPROVED)
      .collect(toList());
    assertThat(linesWithUpdatedStatus, hasSize(invoiceLines.size()));

    assertThat(voucherLinesCreated, hasSize(createdVoucherLines));

    Invoice invoiceUpdate = invoiceUpdates.get(0).mapTo(Invoice.class);

    List<VoucherLine> voucherLines = voucherLinesCreated.stream().map(json -> json.mapTo(VoucherLine.class)).collect(Collectors.toList());

    assertThat(Invoice.Status.APPROVED, equalTo(invoiceUpdate.getStatus()));
    assertThat(invoiceUpdate.getVoucherNumber(), equalTo(voucherCreated.getVoucherNumber()));
    assertThat(invoiceUpdate.getId(), equalTo(voucherCreated.getInvoiceId()));
    assertThat(invoiceUpdate.getCurrency(), equalTo(voucherCreated.getInvoiceCurrency()));
    //  assertThat(HelperUtils.getInvoiceExchangeRateProvider().getExchangeRate(voucherCreated.getInvoiceCurrency(), voucherCreated.getSystemCurrency()).getFactor().doubleValue(), equalTo(voucherCreated.getExchangeRate()));
    assertThat(voucherCreated.getAccountingCode(), equalTo(invoiceUpdate.getAccountingCode()));
    assertThat(voucherCreated.getExportToAccounting(), is(false));
    assertThat(Voucher.Status.AWAITING_PAYMENT, equalTo(voucherCreated.getStatus()));
    assertThat(Voucher.Type.VOUCHER, equalTo(voucherCreated.getType()));

    int paymentCreditNumber = invoiceLines.stream()
      .filter(invoiceLine -> invoiceLine.getTotal() >= 0)
      .mapToInt(line -> line.getFundDistributions().size())
      .sum();

    paymentCreditNumber += invoice.getAdjustments()
      .stream()
      .mapToInt(adj -> adj.getFundDistributions().size())
      .sum();

    assertThat(batchCalls, hasSize(1));
    List<Transaction> pendingPaymentCreated = batchCalls.stream()
      .map(entries -> entries.mapTo(Batch.class))
      .map(Batch::getTransactionsToCreate)
      .flatMap(Collection::stream)
      .toList();
    assertThat(pendingPaymentCreated, hasSize(paymentCreditNumber));
    assertThat(pendingPaymentCreated, Every.everyItem(hasProperty("sourceInvoiceId", is(invoice.getId()))));
    assertThat(pendingPaymentCreated, Every.everyItem(hasProperty("transactionType",
      is(TransactionType.PENDING_PAYMENT))));

    assertThat(calculateVoucherAmount(voucherCreated, voucherLines), equalTo(voucherCreated.getAmount()));
    assertThat(createdVoucherLines, equalTo(voucherLinesCreated.size()));
    invoiceLines.forEach(invoiceLine -> calculateInvoiceLineTotals(invoiceLine, invoiceUpdate));

    voucherLines.forEach(voucherLine -> {
      assertThat(voucherCreated.getId(), equalTo(voucherLine.getVoucherId()));
      assertThat(voucherLine.getFundDistributions(), allOf(
        Every.everyItem(hasProperty("distributionType", is(AMOUNT))),
        Every.everyItem(hasProperty("code"))
      ));
      assertThat(calculateVoucherLineAmount(voucherLine.getFundDistributions(), voucherCreated), equalTo(voucherLine.getAmount()));

      assertThat(voucherLine.getFundDistributions().stream()
        .filter(fundDistribution -> Objects.nonNull(fundDistribution.getInvoiceLineId()))
        .map(FundDistribution::getInvoiceLineId)
        .distinct().collect(Collectors.toList()), hasSize(voucherLine.getSourceIds().size()));
    });

  }

  private double calculateVoucherLineAmount(List<FundDistribution> fundDistributions, Voucher voucher) {
    CurrencyUnit systemCurrency = Monetary.getCurrency(voucher.getSystemCurrency());
    MonetaryAmount voucherLineAmount = Money.of(0, systemCurrency);

    for (FundDistribution fundDistribution : fundDistributions) {
      voucherLineAmount = voucherLineAmount.add(Money.of(fundDistribution.getValue(), systemCurrency));
    }

    return convertToDoubleWithRounding(voucherLineAmount);
  }

  private int getExpectedVoucherLinesQuantity(List<Fund> fundsSearches) {
    return Math.toIntExact(fundsSearches.stream().map(Fund::getExternalAccountNo).distinct().count());
  }

  @Test
  void testUpdateValidInvoiceTransitionToPaidWithMissingPoLine() {
    logger.info("=== Test transition invoice to paid with deleted associated poLine ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(id);
    invoiceLine.setPoLineId(ID_DOES_NOT_EXIST);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    prepareMockVoucher(id);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 404).then().extract().body().as(Errors.class);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT), nullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(PO_LINE_NOT_FOUND.getCode()));
    assertThat(errors.getErrors().get(0).getParameters().get(0).getValue(), equalTo(ID_DOES_NOT_EXIST));
  }

  @Test
  void testUpdateValidInvoiceTransitionToPaidWitErrorOnPoLineUpdate() {
    logger.info("=== Test transition invoice to paid with server error poLine update ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setInvoiceId(id);
    invoiceLine.setPoLineId(ID_FOR_INTERNAL_SERVER_ERROR_PUT);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    prepareMockVoucher(id);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, APPLICATION_JSON, 400).as(Errors.class);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT), nullValue());
    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(PO_LINE_UPDATE_FAILURE.getCode()));
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT), nullValue());
  }

  @Test
  void testUpdateValidInvoiceTransitionToPaid() {
    logger.info("=== Test transition invoice to paid and mixed releaseEncumbrance ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    prepareMockVoucher(id);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));

    List<JsonObject> invoiceLinesUpdates = serverRqRs.get(INVOICE_LINES, HttpMethod.PUT);
    List<InvoiceLine> invoiceLines = invoiceLinesUpdates.stream()
      .map(entry -> entry.mapTo(InvoiceLine.class))
      .collect(toList());
    assertThat(invoiceLines, everyItem(hasProperty("invoiceLineStatus", is(InvoiceLine.InvoiceLineStatus.PAID))));
    validatePoLinesPaymentStatus();
    assertThatVoucherPaid();

    //  Check that the invoice has updated paid date (equals to the one from the metadata).
    logger.info("Test that the invoice has updated paid date on pay transition.");

    var invoices = serverRqRs.get(INVOICES, HttpMethod.PUT)
      .stream()
      .map(entry -> entry.mapTo(Invoice.class))
      .collect(toList());

    var expectedPaymentDate = invoices.get(0).getMetadata().getUpdatedDate();

    assertThat(invoices, everyItem(hasProperty("paymentDate", is(expectedPaymentDate))));
    var payments = getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS).stream()
      .map(entries -> entries.mapTo(Batch.class))
      .map(Batch::getTransactionsToCreate)
      .flatMap(Collection::stream)
      .toList();

    invoiceLines.forEach(invLine -> {
      var sumPaymentsByLine = payments.stream()
        .filter(tr -> tr.getSourceInvoiceLineId() != null)
        .filter(tr -> tr.getSourceInvoiceLineId().equals(invLine.getId()))
        .map(tr -> Money.of(tr.getAmount(), tr.getCurrency()))
        .reduce(Money::add)
        .get()
        .getNumber()
        .doubleValue();

      assertEquals(invLine.getTotal(), sumPaymentsByLine);
    });

    invoiceLines.forEach(invLine -> {
      var sumPaymentsByNonProratedAdjs = payments.stream()
        .filter(tr -> tr.getSourceInvoiceLineId() == null)
        .map(tr -> Money.of(tr.getAmount(), tr.getCurrency()))
        .reduce(Money::add)
        .get()
        .getNumber()
        .doubleValue();
      var invNonProratedAdjs = invoices.get(0).getAdjustments().stream()
        .map(adj -> Money.of(adj.getValue(), invoices.get(0).getCurrency()))
        .reduce(Money::add)
        .get()
        .getNumber()
        .doubleValue();
      assertEquals(invNonProratedAdjs, sumPaymentsByNonProratedAdjs);
    });

  }

  @Test
  void testUpdateInvoiceTransitionToPaidPoLineIdNotSpecified() {
    logger.info("=== Test transition invoice to paid, invoice line doesn't have poLineId ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    prepareMockVoucher(id);
    InvoiceLine invoiceLine1 = getMinimalContentInvoiceLine(id).withPoLineId(null).withFundDistributions(List.of(getFundDistribution()));
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine1));

    InvoiceLine invoiceLine2 = getMinimalContentInvoiceLine(id).withPoLineId(null).withFundDistributions(List.of(getFundDistribution()));
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine2));

    CompositePoLine poLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTENT_PO_LINE_ID))
      .mapTo(CompositePoLine.class)
      .withPaymentStatus(CompositePoLine.PaymentStatus.AWAITING_PAYMENT);
    addMockEntry(ORDER_LINES, JsonObject.mapFrom(poLine));

    InvoiceLine invoiceLine3 = getMinimalContentInvoiceLine(id).withPoLineId(poLine.getId());
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine3));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers,"", 204);
    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));

    final List<CompositePoLine> updatedPoLines = getRqRsEntries(HttpMethod.PUT, ORDER_LINES).stream()
      .map(line -> line.mapTo(CompositePoLine.class))
      .collect(Collectors.toList());

    assertThat(updatedPoLines, hasSize(1));
    assertThatVoucherPaid();
  }

  @Test
  void testUpdateInvoiceTransitionToPaidNoVoucherUpdate() {
    logger.info("=== Test transition invoice to paid - voucher already paid ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    // Prepare already paid voucher
    Voucher voucher = getMockAsJson(VOUCHERS_LIST_PATH).mapTo(VoucherCollection.class).getVouchers().get(0);
    voucher.setInvoiceId(id);
    voucher.setStatus(Voucher.Status.PAID);
    addMockEntry(VOUCHERS_STORAGE, JsonObject.mapFrom(voucher));

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    assertThat(getRqRsEntries(HttpMethod.GET, VOUCHERS_STORAGE), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.PUT, VOUCHERS_STORAGE), empty());
    assertThat(getRqRsEntries(HttpMethod.PUT, INVOICES), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.PUT, INVOICES).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
  }

  @Test
  void testUpdateInvoiceTransitionToPaidNoVoucher() {
    logger.info("=== Test transition invoice to paid - no voucher found ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    prepareMockVoucher(ID_DOES_NOT_EXIST);

    String url = String.format(INVOICE_ID_PATH, reqData.getId());
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(url, JsonObject.mapFrom(reqData), headers, APPLICATION_JSON, 404).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(VOUCHER_NOT_FOUND.getCode()));
    assertThat(getRqRsEntries(HttpMethod.GET, INVOICES), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.PUT, INVOICES), empty());
    assertThat(getRqRsEntries(HttpMethod.PUT, VOUCHERS_STORAGE), empty());
  }

  @Test
  void testUpdateInvoiceTransitionToPaidVoucherUpdateFailure() {
    logger.info("=== Test transition invoice to paid - voucher update failure ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    prepareMockVoucher(reqData.getId(), true);


    String url = String.format(INVOICE_ID_PATH, reqData.getId());
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(url, JsonObject.mapFrom(reqData), headers, APPLICATION_JSON, 500).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    assertThat(errors.getErrors().get(0).getCode(), equalTo(VOUCHER_UPDATE_FAILURE.getCode()));
    assertThat(getRqRsEntries(HttpMethod.PUT, INVOICES), empty());
  }

  private void validatePoLinesPaymentStatus() {

    final List<CompositePoLine> updatedPoLines = getRqRsEntries(HttpMethod.PUT, ORDER_LINES).stream()
      .map(poLine -> poLine.mapTo(CompositePoLine.class))
      .collect(Collectors.toList());

    assertThat(updatedPoLines, not(empty()));

    Map<String, List<InvoiceLine>> invoiceLines = getRqRsEntries(HttpMethod.GET, INVOICE_LINES).get(0)
      .mapTo(InvoiceLineCollection.class)
      .getInvoiceLines()
      .stream()
      .collect(groupingBy(InvoiceLine::getPoLineId));

    assertThat(invoiceLines.size(), equalTo(updatedPoLines.size()));

    for (Map.Entry<String, List<InvoiceLine>> poLineIdWithInvoiceLines : invoiceLines.entrySet()) {
      CompositePoLine poLine = updatedPoLines.stream()
        .filter(compositePoLine -> compositePoLine.getId().equals(poLineIdWithInvoiceLines.getKey()))
        .findFirst()
        .orElseThrow(NullPointerException::new);
      CompositePoLine.PaymentStatus expectedStatus = poLineIdWithInvoiceLines.getValue().stream()
                                                       .anyMatch(InvoiceLine::getReleaseEncumbrance) ? CompositePoLine.PaymentStatus.FULLY_PAID : CompositePoLine.PaymentStatus.PARTIALLY_PAID;
      assertThat(expectedStatus, is(poLine.getPaymentStatus()));
    }
  }

  private void assertThatVoucherPaid() {
    assertThat(getRqRsEntries(HttpMethod.PUT, VOUCHERS_STORAGE), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.PUT, VOUCHERS_STORAGE).get(0).mapTo(Voucher.class).getStatus(), is(Voucher.Status.PAID));
  }

  @Test
  void testUpdateValidInvoiceTransitionToPaidReleaseEncumbranceFalse() {
    logger.info("=== Test transition invoice to paid and releaseEncumbrance false for all invoice lines ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();
    List<InvoiceLine> invoiceLines = new ArrayList<>();
    // Prepare invoice lines
    for (int i = 0; i < 3; i++) {
      InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
      invoiceLine.setId(UUID.randomUUID().toString());
      invoiceLine.setInvoiceId(id);
      invoiceLine.setPoLineId(EXISTENT_PO_LINE_ID);
      invoiceLine.setReleaseEncumbrance(false);
      invoiceLines.add(invoiceLine);
      addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    }

    prepareMockVoucher(id);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, "", 204);

    assertThat(serverRqRs.get(INVOICES, HttpMethod.PUT).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET), notNullValue());
    assertThat(serverRqRs.get(INVOICE_LINES, HttpMethod.GET).get(0).mapTo(InvoiceLineCollection.class).getTotalRecords(), equalTo(3));
    assertThat(serverRqRs.get(ORDER_LINES, HttpMethod.PUT), notNullValue());
    assertThat(serverRqRs.get(ORDER_LINES, HttpMethod.PUT), hasSize(1));
    assertThat(serverRqRs.get(ORDER_LINES, HttpMethod.PUT).get(0).mapTo(CompositePoLine.class).getPaymentStatus(), equalTo(CompositePoLine.PaymentStatus.PARTIALLY_PAID));

    List<JsonObject> invoiceLinesUpdates = serverRqRs.get(INVOICE_LINES, HttpMethod.PUT);
    List<InvoiceLine> lines = invoiceLinesUpdates.stream()
      .map(entry -> entry.mapTo(InvoiceLine.class))
      .collect(toList());
    assertThat(lines, everyItem(hasProperty("invoiceLineStatus", is(InvoiceLine.InvoiceLineStatus.PAID))));

    checkCreditsPayments(reqData, invoiceLines);
  }

  @Test
  void testUpdateValidInvoiceTransitionToPaidReleaseEncumbranceFalseNoPoLineUpdate() {
    logger.info("=== Test transition invoice to paid and releaseEncumbrance false for invoice line without poLine update ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(id);
    invoiceLine.setPoLineId(EXISTENT_PO_LINE_ID);
    invoiceLine.setReleaseEncumbrance(false);

    // to test credit creation
    invoiceLine.setSubTotal(-invoiceLine.getSubTotal());
    invoiceLine.setTotal(-invoiceLine.getTotal());

    CompositePoLine poLine = getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTENT_PO_LINE_ID)).mapTo(CompositePoLine.class);
    poLine.setId(EXISTENT_PO_LINE_ID);
    poLine.setPaymentStatus(CompositePoLine.PaymentStatus.PARTIALLY_PAID);

    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    addMockEntry(ORDER_LINES, JsonObject.mapFrom(poLine));
    prepareMockVoucher(id);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, "", 204);

    assertThat(getRqRsEntries(HttpMethod.PUT, INVOICES).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES).get(0).mapTo(InvoiceLineCollection.class).getTotalRecords(), equalTo(1));
    assertThat(getRqRsEntries(HttpMethod.PUT, ORDER_LINES), empty());
    assertThatVoucherPaid();

    List<JsonObject> invoiceLinesUpdates = serverRqRs.get(INVOICE_LINES, HttpMethod.PUT);
    List<InvoiceLine> lines = invoiceLinesUpdates.stream()
      .map(entry -> entry.mapTo(InvoiceLine.class))
      .collect(toList());
    assertThat(lines, everyItem(hasProperty("invoiceLineStatus", is(InvoiceLine.InvoiceLineStatus.PAID))));

    checkCreditsPayments(reqData, Collections.singletonList(invoiceLine));
  }

  private void prepareMockVoucher(String invoiceId) {
    prepareMockVoucher(invoiceId, false);
  }

  private void prepareMockVoucher(String invoiceId, boolean failOnUpdate) {
    Voucher voucher = getMockAsJson(VOUCHERS_LIST_PATH).mapTo(VoucherCollection.class).getVouchers().get(0);
    voucher.setInvoiceId(invoiceId);
    if (failOnUpdate) {
      voucher.setId(ID_FOR_INTERNAL_SERVER_ERROR_PUT);
    }
    addMockEntry(VOUCHERS_STORAGE, JsonObject.mapFrom(voucher));
  }

  @Test
  void testPutInvoiceByIdChangeStatusToPayedFundsNotFound() {
    logger.info("=== Test Put Invoice By Id Funds not found ===");


    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    reqData.setAdjustments(emptyList());
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    invoiceLine.getFundDistributions().get(0).withFundId(ID_DOES_NOT_EXIST);
    reqData.setStatus(Status.APPROVED);
    addMockEntry(INVOICES, reqData);
    addMockEntry(INVOICE_LINES, invoiceLine);
    prepareMockVoucher(id);
    reqData.setStatus(Status.PAID);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, APPLICATION_JSON, 404).as(Errors.class);

    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES), hasSize(1));

    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(0));
    assertThat(getRqRsEntries(HttpMethod.GET, FUNDS), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS), hasSize(0));

    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getCode(), equalTo(FUNDS_NOT_FOUND.getCode()));
    assertThat(error.getParameters().get(0).getValue(), equalTo(ID_DOES_NOT_EXIST));
  }

  @Test
  void testPutInvoiceByIdChangeStatusToPayedGetFundsServerError() {
    logger.info("=== Test Put Invoice By Id, get Funds server error ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();
    reqData.setStatus(Status.APPROVED);
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);
    reqData.setAdjustments(emptyList());
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    invoiceLine.getFundDistributions().get(0).withFundId(ID_FOR_INTERNAL_SERVER_ERROR);

    addMockEntry(INVOICES, reqData);
    addMockEntry(INVOICE_LINES, invoiceLine);
    prepareMockVoucher(id);
    reqData.setStatus(Status.PAID);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, APPLICATION_JSON, 500).as(Errors.class);

    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES), hasSize(1));

    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(0));
    assertThat(getRqRsEntries(HttpMethod.GET, FUNDS), hasSize(0));
    assertThat(getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS), hasSize(0));

    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getCode(), equalTo(GENERIC_ERROR_CODE.getCode()));
  }

  @Test
  void testUpdateInvoiceTransitionToPaidTransactionsAlreadyExists() {
    logger.info("=== Test transition invoice to paid - transactions already exist in finance storage ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    // Prepare existing transaction
    Transaction transaction = new Transaction()
      .withSourceInvoiceId(id)
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withTransactionType(TransactionType.PAYMENT)
      .withAmount(1.00)
      .withToFundId(EXISTING_FUND_ID);

    addMockEntry(FINANCE_TRANSACTIONS, transaction);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FUNDS), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS), hasSize(0));
  }

  @Test
  void testUpdateInvoiceTransitionToPaidWithNullFiscalYearIdTransactionsAlreadyExists() {
    logger.info("=== Test transition invoice to paid with null fiscalYearId - transactions already exist in finance storage ===");

    JsonObject initialData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH);
    initialData.putNull("fiscalYearId");
    addMockEntry(INVOICES, initialData);
    Invoice reqData = initialData.mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();

    // Prepare existing transaction
    Transaction transaction = new Transaction()
      .withSourceInvoiceId(id)
      .withFiscalYearId(FISCAL_YEAR_ID)
      .withTransactionType(TransactionType.PAYMENT)
      .withAmount(1.00)
      .withToFundId(EXISTING_FUND_ID);

    addMockEntry(FINANCE_TRANSACTIONS, transaction);

    String jsonBody = JsonObject.mapFrom(reqData).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), jsonBody, headers, "", 204);

    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FUNDS), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, CURRENT_FISCAL_YEAR), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS), hasSize(0));
  }

  @Test
  void testPutInvoiceByIdChangeStatusToPayedActiveBudgetNotFound() {
    logger.info("=== Test Put Invoice By Id, Current fiscal year not found ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH_WITHOUT_FISCAL_YEAR).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    invoiceLine.getFundDistributions().get(0).withFundId(FUND_ID_WITH_NOT_ACTIVE_BUDGET);
    invoiceLine.getFundDistributions().forEach(fundDistribution -> {
      Fund fund = new Fund()
        .withId(fundDistribution.getFundId())
        .withExternalAccountNo("externalNo")
        .withLedgerId(EXISTING_LEDGER_ID);
      addMockEntry(FUNDS, fund);
    });

    reqData.getAdjustments().stream().flatMap(adjustment -> adjustment.getFundDistributions().stream())
      .map(FundDistribution::getFundId).distinct().forEach(fundId -> {
        Fund fund = new Fund()
          .withId(fundId)
          .withExternalAccountNo("externalNo")
          .withLedgerId(EXISTING_LEDGER_ID);
        addMockEntry(FUNDS, fund);
      });


    addMockEntry(LEDGERS, JsonObject.mapFrom(new Ledger().withId(EXISTING_LEDGER_ID).withRestrictEncumbrance(true)));
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    prepareMockVoucher(id);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, APPLICATION_JSON, 404).as(Errors.class);

    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES), hasSize(1));

    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(0));
    assertThat(getRqRsEntries(HttpMethod.GET, FUNDS), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS), hasSize(0));

    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getCode(), equalTo(BUDGET_NOT_FOUND.getCode()));
  }

  @Test
  void testUpdateValidInvoiceTransitionToPaidReleaseEncumbranceTrue() {
    logger.info("=== Test transition invoice to paid and releaseEncumbrance true for all invoice lines ===");
    List<InvoiceLine> invoiceLines = new ArrayList<>();
    List<CompositePoLine> poLines = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      invoiceLines.add(getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class));
      poLines.add(getMockAsJson(String.format("%s%s.json", PO_LINE_MOCK_DATA_PATH, EXISTENT_PO_LINE_ID)).mapTo(CompositePoLine.class));
    }

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Invoice.Status.PAID);
    String id = reqData.getId();
    for (int i = 0; i < 3; i++) {
      InvoiceLine invoiceLine = invoiceLines.get(i);

      invoiceLine.setId(UUID.randomUUID().toString());
      invoiceLine.setInvoiceId(reqData.getId());
      String poLineId = UUID.randomUUID().toString();
      invoiceLine.setPoLineId(poLineId);
      poLines.get(i).setId(poLineId);
    }

    invoiceLines.forEach(line -> addMockEntry(INVOICE_LINES, JsonObject.mapFrom(line)));
    poLines.forEach(line -> addMockEntry(ORDER_LINES, JsonObject.mapFrom(line)));
    prepareMockVoucher(id);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, "", 204);

    assertThat(getRqRsEntries(HttpMethod.PUT, INVOICES).get(0).getString(STATUS), is(Invoice.Status.PAID.value()));
    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES), hasSize(1));
    assertThat(getRqRsEntries(HttpMethod.GET, INVOICE_LINES).get(0).mapTo(InvoiceLineCollection.class).getTotalRecords(), equalTo(3));
    assertThat(getRqRsEntries(HttpMethod.PUT, ORDER_LINES), hasSize(3));
    getRqRsEntries(HttpMethod.PUT, ORDER_LINES).stream()
      .map(entries -> entries.mapTo(CompositePoLine.class))
      .forEach(compositePoLine -> assertThat(compositePoLine.getPaymentStatus(), equalTo(CompositePoLine.PaymentStatus.FULLY_PAID)));
    assertThatVoucherPaid();

    List<JsonObject> invoiceLinesUpdates = serverRqRs.get(INVOICE_LINES, HttpMethod.PUT);
    List<InvoiceLine> lines = invoiceLinesUpdates.stream()
      .map(entry -> entry.mapTo(InvoiceLine.class))
      .collect(toList());
    assertThat(lines, everyItem(hasProperty("invoiceLineStatus", is(InvoiceLine.InvoiceLineStatus.PAID))));

    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(2));
    var payments = getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS).stream()
      .map(entries -> entries.mapTo(Batch.class))
      .map(Batch::getTransactionsToCreate)
      .flatMap(Collection::stream)
      .toList();
    assertThat(payments, Every.everyItem(hasProperty("transactionType",
      is(TransactionType.PAYMENT))));
    assertThat(payments, hasSize(5));

    checkCreditsPayments(reqData, invoiceLines);
  }

  @Test
  void testUpdateNotExistentInvoice() throws IOException {
    logger.info("=== Test update non existent invoice===");

    String jsonBody  = getMockData(APPROVED_INVOICE_SAMPLE_PATH);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, ID_DOES_NOT_EXIST), jsonBody, headers, APPLICATION_JSON, 404);
  }

  @Test
  void testUpdateInvoiceInternalErrorOnStorage() throws IOException {
    logger.info("=== Test update invoice by id with internal server error from storage ===");

    String jsonBody  = getMockData(APPROVED_INVOICE_SAMPLE_PATH);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), jsonBody, headers, APPLICATION_JSON, 500);
  }

  @Test
  @Disabled
  void testUpdateInvoiceByIdWithInvalidFormat() throws IOException {

    String jsonBody  = getMockData(APPROVED_INVOICE_SAMPLE_PATH);
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, ID_BAD_FORMAT), jsonBody, headers, TEXT_PLAIN, 400);
  }

  @Test
  void testPostInvoicingInvoices() throws Exception {
    logger.info("=== Test create invoice without id and folioInvoiceNo ===");

    String body = getMockData(APPROVED_INVOICE_SAMPLE_PATH);

    final Invoice respData = verifyPostResponse(INVOICE_PATH, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON, 201).as(Invoice.class);

    String poId = respData.getId();
    Integer nextInvoiceLineNumber = respData.getNextInvoiceLineNumber();
    String folioInvoiceNo = respData.getFolioInvoiceNo();

    assertThat(poId, notNullValue());
    assertThat(poId, notNullValue());
    assertThat(nextInvoiceLineNumber, nullValue());
    assertThat(folioInvoiceNo, notNullValue());
    assertThat(getRqRsEntries(HttpMethod.GET, FOLIO_INVOICE_NUMBER), hasSize(1));

    // Check that invoice in the response and the one in storage are the same
    compareRecordWithSentToStorage(respData);
  }


  @Test
  void testCreateInvoiceWithLockedTotalAndTwoAdjustmentsAndNoInvoiceLinesProvided() throws IOException {
    logger.info("=== Test create invoice with locked total and 2 adjustments ===");

    // ===  Preparing invoice for test  ===
    Invoice invoice = new JsonObject(getMockData(APPROVED_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);

    invoice.setLockTotal(15d);

    // ===  Run test  ===
    final Invoice resp = verifyPostResponse(INVOICE_PATH, JsonObject.mapFrom(invoice), prepareHeaders(X_OKAPI_TENANT),
      APPLICATION_JSON, 201).as(Invoice.class);

    /* The invoice has 2 not prorated adjustments one with fixed amount and another with percentage type */
    assertThat(resp.getLockTotal(), equalTo(15d));
    assertThat(resp.getAdjustmentsTotal(), equalTo(5.06d));
    assertThat(resp.getSubTotal(), equalTo(0d));
    assertThat(resp.getTotal(), equalTo(5.06d));

    // Verify that expected number of external calls made
    assertThat(serverRqRs.cellSet(), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FOLIO_INVOICE_NUMBER), hasSize(1));

    compareRecordWithSentToStorage(resp);
  }

  @Test
  void testCreateInvoiceWithTwoProratedAdjustmentsNoLines() throws IOException {
    logger.info(
      "=== Test create invoice with 1 prorated and 1 not prorated adjustments with no lines - adjustmentTotal should always be calculated irrespective if there are any invoiceLines or not===");

    // === Preparing invoice for test ===
    Invoice invoice = new JsonObject(getMockData(REVIEWED_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);

    Adjustment adjustment1 = createAdjustment(Prorate.NOT_PRORATED, Type.AMOUNT, 10d).withId(randomUUID().toString());
    Adjustment adjustment2 = createAdjustment(Prorate.BY_LINE, Type.AMOUNT, 10d).withId(randomUUID().toString());
    invoice.setAdjustments((Arrays.asList(adjustment1, adjustment2)));

    // === Run test ===
    final Invoice resp = verifyPostResponse(INVOICE_PATH, JsonObject.mapFrom(invoice), prepareHeaders(X_OKAPI_TENANT),
      APPLICATION_JSON, 201).as(Invoice.class);

    /* The invoice has 2 adjustments with no lines, one not prorated and one prorated by line adjustments */
    assertThat(resp.getAdjustmentsTotal(), equalTo(20.d));
    assertThat(resp.getSubTotal(), equalTo(0d));
    assertThat(resp.getTotal(), equalTo(20d));

    // Verify that expected number of external calls made
    assertThat(serverRqRs.cellSet(), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FOLIO_INVOICE_NUMBER), hasSize(1));

    compareRecordWithSentToStorage(resp);
  }

  @Test
  void testCreateInvoiceWithLockedTotalAndTwoProratedAdjustmentsAndWithoutInvoiceLines() throws IOException {
    logger.info("=== Test create invoice with locked total and 2 prorated adjustments ===");

    // ===  Preparing invoice for test  ===
    Invoice invoice = new JsonObject(getMockData(APPROVED_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);
    invoice.getAdjustments().forEach(adj -> adj.setProrate(Adjustment.Prorate.BY_AMOUNT));
    invoice.setLockTotal(15d);

    // ===  Run test  ===
    final Invoice resp = verifyPostResponse(INVOICE_PATH, JsonObject.mapFrom(invoice), prepareHeaders(X_OKAPI_TENANT),
      APPLICATION_JSON, 201).as(Invoice.class);

    /* The invoice has 2 not prorated adjustments one with fixed amount and another with percentage type */
    assertThat(resp.getLockTotal(), equalTo(15d));
    assertThat(resp.getAdjustmentsTotal(), equalTo(5.06d));
    assertThat(resp.getSubTotal(), equalTo(0d));
    assertThat(resp.getTotal(), equalTo(5.06d));

    // Verify that expected number of external calls made
    assertThat(serverRqRs.cellSet(), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FOLIO_INVOICE_NUMBER), hasSize(1));

    compareRecordWithSentToStorage(resp);
  }

  @Test
  void testCreateInvoiceWithNonLockedTotalAndWithoutAdjustments() throws IOException {
    logger.info("=== Test create invoice without total and no adjustments ===");

    // ===  Preparing invoice for test  ===
    Invoice invoice = new JsonObject(getMockData(APPROVED_INVOICE_SAMPLE_PATH)).mapTo(Invoice.class);
//    invoice.setLockTotal(false);
    invoice.setAdjustments(null);
    invoice.setTotal(null);

    // ===  Run test  ===
    final Invoice resp = verifyPostResponse(INVOICE_PATH, JsonObject.mapFrom(invoice), prepareHeaders(X_OKAPI_TENANT),
      APPLICATION_JSON, 201).as(Invoice.class);

    assertThat(resp.getAdjustmentsTotal(), equalTo(0d));
    assertThat(resp.getSubTotal(), equalTo(0d));
    assertThat(resp.getTotal(), equalTo(0d));

    // Verify that expected number of external calls made
    assertThat(serverRqRs.cellSet(), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FOLIO_INVOICE_NUMBER), hasSize(1));

    compareRecordWithSentToStorage(resp);
  }

  @Test
  void testPostInvoicingInvoicesErrorFromStorage() throws Exception {
    logger.info("=== Test create invoice without with error from storage on saving invoice  ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(APPROVED_INVOICE_SAMPLE_PATH);

    verifyPostResponse(INVOICE_PATH, body, prepareHeadersWithoutPermissions(ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);

    assertThat(getRqRsEntries(HttpMethod.GET, FOLIO_INVOICE_NUMBER), hasSize(1));
  }

  @Test
  void testPostInvoicingInvoicesWithInvoiceNumberGenerationFail() throws IOException {
    logger.info("=== Test create invoice without error from storage on folioInvoiceNo generation  ===");

    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setId(null);
    reqData.setFolioInvoiceNo(null);
    String body = getMockData(APPROVED_INVOICE_SAMPLE_PATH);

    verifyPostResponse(INVOICE_PATH, body, prepareHeaders(INVOICE_NUMBER_ERROR_X_OKAPI_TENANT), APPLICATION_JSON, 500);

  }

  @Test
  void testGetInvoiceNumber() {
    logger.info("=== Test Get Invoice number - not implemented ===");

    verifyGet(INVOICE_NUMBER_PATH, TEXT_PLAIN, 500);
  }

  @Test
  void testDeleteInvoiceByValidId() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, VALID_UUID), "", 204);
  }

  @Test
  void testDeleteApprovedInvoice() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, APPROVED_INVOICE_ID), "", 403);
  }

  @Test
  void testDeletePaidInvoice() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, PAID_INVOICE_ID), "", 403);
  }

  @Test
  @Disabled
  void testDeleteInvoiceByIdWithInvalidFormat() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, ID_BAD_FORMAT), TEXT_PLAIN, 400);
  }

  @Test
  void testDeleteNotExistentInvoice() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, ID_DOES_NOT_EXIST), APPLICATION_JSON, 404);
  }

  @Test
  void testDeleteInvoiceInternalErrorOnStorage() {
    verifyDeleteResponse(String.format(INVOICE_ID_PATH, ID_FOR_INTERNAL_SERVER_ERROR), APPLICATION_JSON, 500);
  }

  @Test
  void testNumberOfRequests() {
    logger.info("=== Test number of requests on invoice PUT ===");

    // Invoice status APPROVED, PAID - expect invoice updating with GET invoice rq + PUT invoice rq by statuses processable flow
    Invoice.Status[] processableStatuses = {Invoice.Status.APPROVED, Invoice.Status.PAID};
    checkNumberOfRequests(processableStatuses);

    // Invoice status OPEN, REVIEWED - expect invoice updating with GET invoice rq + PUT invoice rq without statuses processable flow
    Invoice.Status[] nonProcessableStatuses = {Invoice.Status.OPEN, Invoice.Status.REVIEWED};
    checkNumberOfRequests(nonProcessableStatuses);
  }

  private void checkNumberOfRequests(Invoice.Status[] statuses) {
    // Invoice status open - expect no GET invoice rq + PUT invoice rq
    for (Invoice.Status status : statuses) {
      // MODINVOICE-76 prepare mock invoice with appropriate statuses
      Invoice invoice;
      String mockFilePath;
      switch (status) {
        case OPEN:
          mockFilePath = OPEN_INVOICE_SAMPLE_PATH;
          break;
        case REVIEWED:
          mockFilePath = REVIEWED_INVOICE_SAMPLE_PATH;
          break;
        default:
          mockFilePath = APPROVED_INVOICE_SAMPLE_PATH;
          break;
      }
      invoice = getMockAsJson(mockFilePath).mapTo(Invoice.class).withStatus(status);

      prepareMockVoucher(invoice.getId());
      Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
      verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), JsonObject.mapFrom(invoice).encode(), headers, "", HttpStatus.SC_NO_CONTENT);

      assertThat(serverRqRs.row(INVOICES).get(HttpMethod.GET), hasSize(1));
      assertThat(serverRqRs.row(INVOICES).get(HttpMethod.PUT), hasSize(1));

      List<JsonObject> invoiceLinesUpdates = Optional.ofNullable(serverRqRs.get(INVOICE_LINES, HttpMethod.PUT)).orElse(emptyList());
      List<InvoiceLine> lines = invoiceLinesUpdates.stream()
        .map(entry -> entry.mapTo(InvoiceLine.class))
        .collect(toList());
      assertThat(lines, everyItem(hasProperty("invoiceLineStatus", is(InvoiceLine.InvoiceLineStatus.fromValue(status.value())))));

      clearServiceInteractions();
    }
  }

  @Test
  void testCreateOpenedInvoiceWithIncompatibleFields() {
    logger.info("=== Test create opened invoice with 'approvedBy' and 'approvedDate' fields ===");

    Invoice invoice = getMockAsJson(OPEN_INVOICE_WITH_APPROVED_FILEDS_SAMPLE_PATH).mapTo(Invoice.class);
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID);

    verifyPostResponse(INVOICE_PATH, JsonObject.mapFrom(invoice).encode(), headers, APPLICATION_JSON, HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  void testUpdateOpenedInvoiceWithIncompatibleFields() {
    logger.info("=== Test update opened invoice with 'approvedBy' and 'approvedDate' fields ===");

    Invoice invoice = getMockAsJson(OPEN_INVOICE_WITH_APPROVED_FILEDS_SAMPLE_PATH).mapTo(Invoice.class);
    Headers headers = prepareHeaders(X_OKAPI_TENANT, X_OKAPI_USER_ID);

    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), JsonObject.mapFrom(invoice).encode(), headers, APPLICATION_JSON, HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  void testUpdateInvoiceWithProtectedFields() throws IllegalAccessException {
    logger.info("=== Test update invoice by id with protected fields (all fields set) ===");

    Invoice invoice = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);

    Map<InvoiceProtectedFields, Object> allProtectedFieldsModification = new HashMap<>();

    List<Adjustment> adjustments = invoice.getAdjustments();
    adjustments.get(0).setValue(12345.54321);
    allProtectedFieldsModification.put(InvoiceProtectedFields.ADJUSTMENTS, adjustments);

    allProtectedFieldsModification.put(InvoiceProtectedFields.APPROVED_BY, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceProtectedFields.APPROVAL_DATE, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceProtectedFields.CHK_SUBSCRIPTION_OVERLAP, true);
    allProtectedFieldsModification.put(InvoiceProtectedFields.CURRENCY, "TUGRIK");
    allProtectedFieldsModification.put(InvoiceProtectedFields.FOLIO_INVOICE_NO, "some_folio_inv_num");
    allProtectedFieldsModification.put(InvoiceProtectedFields.INVOICE_DATE, new Date(System.currentTimeMillis()));
    allProtectedFieldsModification.put(InvoiceProtectedFields.LOCK_TOTAL, 15d);
    allProtectedFieldsModification.put(InvoiceProtectedFields.PAYMENT_TERMS, "Payment now");
    allProtectedFieldsModification.put(InvoiceProtectedFields.SOURCE, Invoice.Source.EDI);
    allProtectedFieldsModification.put(InvoiceProtectedFields.VOUCHER_NUMBER, "some_voucher_number");
    allProtectedFieldsModification.put(InvoiceProtectedFields.PAYMENT_ID, UUID.randomUUID().toString());
    allProtectedFieldsModification.put(InvoiceProtectedFields.VENDOR_ID, UUID.randomUUID().toString());

    List<String> poNumbers = invoice.getPoNumbers();
    poNumbers.add(0, "AB267798XYZ");
    allProtectedFieldsModification.put(InvoiceProtectedFields.PO_NUMBERS, poNumbers);

    checkPreventInvoiceModificationRule(invoice, allProtectedFieldsModification);

    // Check number of requests
    assertThat(serverRqRs.row(INVOICES).get(HttpMethod.GET), hasSize(1));
    // PUT request wasn't processed
    assertThat(serverRqRs.row(INVOICES).get(HttpMethod.PUT), nullValue());
  }


  @Test
  void testUpdateTagsForPaidStatusInvoice(){
    logger.info("=== allow to update  tags fields for the paid invoices ===");
    Invoice reqData = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withStatus(Status.PAID);
    String id = reqData.getId();
    prepareMockVoucher(reqData.getId());
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(id);

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    addMockEntry(INVOICES, reqData);
    addMockEntry(INVOICE_LINES, invoiceLine);
    List<String> tagsList =Arrays.asList("TestTagURGENT","TestTagIMPORTANT");
    reqData.setTags(new Tags().withTagList(tagsList));
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, "", 204);
  }

  @Test
  void testInvoiceFiscalYears() {
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_OPEN_EXISTED_INVOICE_ID_PATH).mapTo(InvoiceLine.class);
    addMockEntry(INVOICE_LINES, invoiceLine);
    FiscalYearCollection fyCollection = verifySuccessGet(String.format(INVOICE_FISCAL_YEARS_PATH, OPEN_INVOICE_ID),
      FiscalYearCollection.class);
    assertThat(fyCollection.getTotalRecords(), equalTo(1));
  }

  @Test
  void testPaidInvoiceWithUpdatedFiscalYear() {
    Invoice reqData = getMockAsJson(APPROVED_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    reqData.setFiscalYearId(UUID.randomUUID().toString());
    String id = reqData.getId();
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINE_WITH_APPROVED_INVOICE_SAMPLE_PATH).mapTo(InvoiceLine.class);

    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceId(reqData.getId());
    invoiceLine.getFundDistributions().get(0).withFundId(FUND_ID_WITH_NOT_ACTIVE_BUDGET);
    invoiceLine.getFundDistributions().forEach(fundDistribution -> {
      Fund fund = new Fund()
        .withId(fundDistribution.getFundId())
        .withExternalAccountNo("externalNo")
        .withLedgerId(EXISTING_LEDGER_ID);
      addMockEntry(FUNDS, fund);
    });

    reqData.getAdjustments().stream().flatMap(adjustment -> adjustment.getFundDistributions().stream())
      .map(FundDistribution::getFundId).distinct().forEach(fundId -> {
        Fund fund = new Fund()
          .withId(fundId)
          .withExternalAccountNo("externalNo")
          .withLedgerId(EXISTING_LEDGER_ID);
        addMockEntry(FUNDS, fund);
      });

    addMockEntry(LEDGERS, JsonObject.mapFrom(new Ledger().withId(EXISTING_LEDGER_ID).withRestrictEncumbrance(true)));
    addMockEntry(INVOICE_LINES, JsonObject.mapFrom(invoiceLine));
    prepareMockVoucher(id);
    reqData.setStatus(Status.PAID);
    Headers headers = prepareHeadersWithoutPermissions(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, id), JsonObject.mapFrom(reqData), headers, APPLICATION_JSON, 403).as(Errors.class);

    assertThat(errors.getErrors(), hasSize(1));
    Error error = errors.getErrors().get(0);
    assertThat(error.getCode(), equalTo(USER_HAS_NO_FISCAL_YEAR_UPDATE_PERMISSIONS.getCode()));
  }

  private void checkPreventInvoiceModificationRule(Invoice invoice, Map<InvoiceProtectedFields, Object> updatedFields) throws IllegalAccessException {
    invoice.setStatus(Invoice.Status.APPROVED);
    for (Map.Entry<InvoiceProtectedFields, Object> m : updatedFields.entrySet()) {
      FieldUtils.writeDeclaredField(invoice, m.getKey().getFieldName(), m.getValue(), true);
    }
    String body = JsonObject.mapFrom(invoice).encode();
    Headers headers = prepareHeaders(X_OKAPI_URL, X_OKAPI_TENANT, X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    Errors errors = verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), body, headers, "", HttpStatus.SC_BAD_REQUEST).as(Errors.class);

    // Only one error expected
    assertThat(errors.getErrors(), hasSize(1));

    Error error = errors.getErrors().get(0);
    assertThat(error.getCode(), equalTo(PROHIBITED_FIELD_CHANGING.getCode()));

    Object[] failedFieldNames = getModifiedProtectedFields(error);
    Object[] expected = updatedFields.keySet().stream().map(InvoiceProtectedFields::getFieldName).toArray();
    assertThat(failedFieldNames.length, is(expected.length));
    assertThat(expected, Matchers.arrayContainingInAnyOrder(failedFieldNames));
  }

  private Object[] getModifiedProtectedFields(Error error) {
    return Optional.of(error.getAdditionalProperties().get(PROTECTED_AND_MODIFIED_FIELDS))
      .map(obj -> (List) obj)
      .get()
      .toArray();
  }

  private void verifyInvoiceUpdateCalls(int msgQty) {
    logger.debug("Verifying calls to update invoice");
    // Wait until message is registered
    await().atLeast(50, MILLISECONDS)
      .atMost(1, SECONDS)
      .until(MockServer::getInvoiceUpdates, hasSize(msgQty));
  }

  private void compareRecordWithSentToStorage(Invoice invoice) {
    // Verify that invoice sent to storage is the same as in response
    assertThat(getInvoiceCreations(), hasSize(1));
    Invoice invoiceToStorage = getInvoiceCreations().get(0).mapTo(Invoice.class);
    assertThat(invoice, equalTo(invoiceToStorage));
  }

  private void checkVoucherAcqUnitIdsList(Voucher voucherCreated, Invoice invoice) {
    assertTrue(CollectionUtils.isEqualCollection(voucherCreated.getAcqUnitIds(), invoice.getAcqUnitIds()), "acqUnitId lists are equal");
  }

  private void checkCreditsPayments(Invoice invoice, List<InvoiceLine> invoiceLines) {
    int invoiceLinePaymentsCount = Math.toIntExact(invoiceLines.stream()
      .flatMapToDouble(invoiceLine -> invoiceLine.getFundDistributions()
        .stream().mapToDouble(fundDistribution -> getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), invoice.getCurrency()).getNumber().doubleValue()))
      .filter(value -> value > 0)
      .count());
    int invoiceLineCreditsCount = invoiceLines.stream()
      .mapToInt(invoiceLine -> invoiceLine.getFundDistributions()
        .size()).sum() - invoiceLinePaymentsCount;

    int adjustmentPaymentsCount = Math.toIntExact(invoice.getAdjustments().stream()
      .flatMapToDouble(adjustment -> adjustment.getFundDistributions()
        .stream().mapToDouble(fundDistribution -> getAdjustmentFundDistributionAmount(fundDistribution, adjustment, invoice).getNumber().doubleValue()))
      .filter(value -> value > 0)
      .count());

    int adjustmentCreditsCount = invoice.getAdjustments().stream()
      .mapToInt(adjustment -> adjustment.getFundDistributions()
        .size()).sum() - adjustmentPaymentsCount;

    int invoiceLineEncumbranceReferenceNumber = (int) invoiceLines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream())
      .filter(fundDistribution -> StringUtils.isNotEmpty(fundDistribution.getEncumbrance()))
      .count();



    assertThat(getRqRsEntries(HttpMethod.GET, FINANCE_TRANSACTIONS), hasSize(2));
    assertThat(getRqRsEntries(HttpMethod.GET, FUNDS), hasSize(1));

    var transactions = getRqRsEntries(HttpMethod.POST, FINANCE_BATCH_TRANSACTIONS).stream()
      .map(entries -> entries.mapTo(Batch.class))
      .map(Batch::getTransactionsToCreate)
      .flatMap(Collection::stream)
      .toList();
    var payments = transactions.stream().filter(tr -> tr.getTransactionType() == TransactionType.PAYMENT).toList();
    var credits = transactions.stream().filter(tr -> tr.getTransactionType() == TransactionType.CREDIT).toList();
    assertThat(payments, hasSize(invoiceLinePaymentsCount + adjustmentPaymentsCount));
    assertThat(credits, hasSize(invoiceLineCreditsCount + adjustmentCreditsCount));

    assertThat(payments, everyItem(HasProperty.hasProperty("fromFundId")));
    assertThat(credits, everyItem(HasProperty.hasProperty("toFundId")));

    int transactionEncumbranceReferenceNumber = (int) transactions.stream()
            .filter(transaction -> StringUtils.isNotEmpty(transaction.getPaymentEncumbranceId()))
            .count();

    assertEquals(invoiceLineEncumbranceReferenceNumber, transactionEncumbranceReferenceNumber);
    assertThat(transactions, allOf(
      Every.everyItem(HasPropertyWithValue.hasProperty("sourceInvoiceId", is(invoice.getId()))),
      Every.everyItem(HasProperty.hasProperty("sourceInvoiceLineId")),
      Every.everyItem(HasProperty.hasProperty("currency")),
      Every.everyItem(HasPropertyWithValue.hasProperty("amount", greaterThanOrEqualTo(0.0))),
      Every.everyItem(HasPropertyWithValue.hasProperty("fiscalYearId", is(FISCAL_YEAR_ID))),
      Every.everyItem(HasPropertyWithValue.hasProperty("source", is(Transaction.Source.INVOICE)))
    ));
  }

  private void verifyVoucherLineWithExpenseClasses(long fundDistributionCount) {
    List<VoucherLine> voucherLines = serverRqRs.get(VOUCHER_LINES, HttpMethod.POST).stream()
      .map(json -> json.mapTo(VoucherLine.class)).collect(Collectors.toList());
    List<VoucherLine> expVoucherLines = voucherLines.stream()
      .filter(voucherLinesP -> voucherLinesP.getFundDistributions().stream()
        .anyMatch(fundDistribution -> Objects.nonNull(fundDistribution.getExpenseClassId()))
      ).toList();

    long actFundDistrCount = expVoucherLines.stream().mapToLong(voucherLine -> voucherLine.getFundDistributions().size()).sum();
    assertThat(actFundDistrCount, equalTo(fundDistributionCount));
  }
}
