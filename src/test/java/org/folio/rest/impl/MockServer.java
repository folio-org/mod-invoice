package org.folio.rest.impl;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.invoices.utils.HelperUtils.ALL_UNITS_CQL;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.IS_DELETED_PROP;
import static org.folio.invoices.utils.HelperUtils.QUERY_PARAM_START_WITH;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_MEMBERSHIPS;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_UNITS;
import static org.folio.invoices.utils.ResourcePathResolver.AWAITING_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_GROUPS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS;
import static org.folio.invoices.utils.ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_DOCUMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.ApiTestBase.BASE_MOCK_DATA_PATH;
import static org.folio.rest.impl.ApiTestBase.FOLIO_INVOICE_NUMBER_VALUE;
import static org.folio.rest.impl.ApiTestBase.ID_DOES_NOT_EXIST;
import static org.folio.rest.impl.ApiTestBase.ID_FOR_INTERNAL_SERVER_ERROR;
import static org.folio.rest.impl.ApiTestBase.ID_FOR_INTERNAL_SERVER_ERROR_PUT;
import static org.folio.rest.impl.ApiTestBase.INVOICE_LINE_NUMBER_VALUE;
import static org.folio.rest.impl.ApiTestBase.PROTECTED_READ_ONLY_TENANT;
import static org.folio.rest.impl.ApiTestBase.VOUCHER_NUMBER_VALUE;
import static org.folio.rest.impl.ApiTestBase.getMockData;
import static org.folio.rest.impl.BatchGroupsApiTest.BATCH_GROUPS_LIST_PATH;
import static org.folio.rest.impl.BatchGroupsApiTest.BATCH_GROUP_MOCK_DATA_PATH;
import static org.folio.rest.impl.BatchVoucherExportConfigCredentialsTest.BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID;
import static org.folio.rest.impl.BatchVoucherExportConfigTest.BATCH_VOUCHER_EXPORT_CONFIGS_SAMPLE_PATH;
import static org.folio.rest.impl.BatchVoucherExportConfigTest.BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH;
import static org.folio.rest.impl.DocumentsApiTest.INVOICE_DOCUMENTS_SAMPLE_PATH;
import static org.folio.rest.impl.DocumentsApiTest.INVOICE_SAMPLE_DOCUMENTS_PATH;
import static org.folio.rest.impl.InvoiceHelper.INVOICE_CONFIG_MODULE_NAME;
import static org.folio.rest.impl.InvoiceHelper.LOCALE_SETTINGS;
import static org.folio.rest.impl.InvoiceHelper.SYSTEM_CONFIG_MODULE_NAME;
import static org.folio.rest.impl.InvoiceHelper.VOUCHER_NUMBER_CONFIG_NAME;
import static org.folio.rest.impl.InvoiceHelper.VOUCHER_NUMBER_PREFIX_CONFIG;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_MOCK_DATA_PATH;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesApiTest.EXISTING_VENDOR_INV_NO;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_MOCK_DATA_PATH;
import static org.folio.rest.impl.ProtectionHelper.ACQUISITIONS_UNIT_ID;
import static org.folio.rest.impl.VoucherLinesApiTest.VOUCHER_LINES_MOCK_DATA_PATH;
import static org.folio.rest.impl.VouchersApiTest.VOUCHERS_LIST_PATH;
import static org.folio.rest.impl.VouchersApiTest.VOUCHER_MOCK_DATA_PATH;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.BatchGroup;
import org.folio.rest.acq.model.BatchGroupCollection;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.units.AcquisitionsUnit;
import org.folio.rest.acq.model.units.AcquisitionsUnitCollection;
import org.folio.rest.acq.model.units.AcquisitionsUnitMembershipCollection;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.folio.rest.jaxrs.model.Credentials;
import org.folio.rest.jaxrs.model.Document;
import org.folio.rest.jaxrs.model.DocumentCollection;
import org.folio.rest.jaxrs.model.ExportConfig;
import org.folio.rest.jaxrs.model.ExportConfigCollection;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceDocument;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import io.restassured.http.Header;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import one.util.streamex.StreamEx;

public class MockServer {

  private static final Logger logger = LoggerFactory.getLogger(MockServer.class);
  private static final String MOCK_DATA_PATH_PATTERN = "%s%s.json";
  private static final String FUNDS_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "fundRecords/";
  private static final String VOUCHER_ID = "voucherId";
  private static final String QUERY = "query";
  private static final String LIMIT = "limit";
  private static final String OFFSET = "offset";
  
  private static final String MOCK_DATA_INVOICES = "mockdata/invoices/invoices.json";
  static final String TEST_PREFIX = "testPrefix";
  private static final String INVALID_PREFIX = "12-prefix";

  private static final String INVOICE_NUMBER_ERROR_TENANT = "po_number_error_tenant";
  private static final String INVOICE_LINE_NUMBER_ERROR_TENANT = "invoice_line_number_error_tenant";
  private static final String VOUCHER_NUMBER_ERROR_TENANT = "voucher_number_error_tenant";
  private static final String ERROR_TENANT = "error_tenant";
  private static final String ERROR_CONFIG_TENANT = "error_config_tenant";
  private static final String DELETE_VOUCHER_LINES_ERROR_TENANT = "get_voucher_lines_error_tenant";
  private static final String GET_VOUCHER_LINES_ERROR_TENANT = "get_voucher_lines_error_tenant";
  private static final String CREATE_VOUCHER_LINES_ERROR_TENANT = "create_voucher_lines_error_tenant";
  private static final String GET_FUNDS_ERROR_TENANT = "get_funds_error_tenant";
  private static final String CREATE_VOUCHER_ERROR_TENANT = "create_voucher_error_tenant";
  private static final String UPDATE_VOUCHER_ERROR_TENANT = "update_voucher_error_tenant";
  private static final String GET_VOUCHERS_ERROR_TENANT = "get_vouchers_error_tenant";
  private static final String GET_INVOICE_LINES_ERROR_TENANT = "get_invoice_lines_error_tenant";
  private static final String CREATE_INVOICE_TRANSACTION_SUMMARY_ERROR_TENANT = "create_invoice_transaction_summary_error_tenant";
  private static final String POST_AWAITING_PAYMENT_ERROR_TENANT = "post_awaiting_payment_error_tenant";
  private static final String NON_EXIST_CONFIG_TENANT = "invoicetest";
  private static final String INVALID_PREFIX_CONFIG_TENANT = "invalid_prefix_config_tenant";

  private static final String INVOICE_LINES_COLLECTION = BASE_MOCK_DATA_PATH + "invoiceLines/invoice_lines.json";
  private static final String VOUCHER_LINES_COLLECTION = BASE_MOCK_DATA_PATH + "voucherLines/voucher_lines.json";
  private static final String ACQUISITIONS_UNITS_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "acquisitionUnits";
  static final String ACQUISITIONS_UNITS_COLLECTION = ACQUISITIONS_UNITS_MOCK_DATA_PATH + "/units.json";
  static final String ACQUISITIONS_MEMBERSHIPS_COLLECTION = ACQUISITIONS_UNITS_MOCK_DATA_PATH + "/memberships.json";
  private static final String PO_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  private static final String ID_PATH_PARAM = "/:" + ID;
  private static final String VALUE_PATH_PARAM = "/:value";
  private static final String TOTAL_RECORDS = "totalRecords";
  public static final String SEARCH_INVOICE_BY_LINE_ID_NOT_FOUND = "b37cd8e7-d291-40f0-b687-57728ee3fc26";

  static final Header INVOICE_NUMBER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, INVOICE_NUMBER_ERROR_TENANT);
  static final Header ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, ERROR_TENANT);
  static final Header ERROR_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, ERROR_CONFIG_TENANT);
  static final Header INVALID_PREFIX_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, INVALID_PREFIX_CONFIG_TENANT);
  static final Header INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, INVOICE_LINE_NUMBER_ERROR_TENANT);
  static final Header GET_INVOICE_LINES_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, GET_INVOICE_LINES_ERROR_TENANT);
  static final Header GET_VOUCHERS_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, GET_VOUCHERS_ERROR_TENANT);
  static final Header UPDATE_VOUCHER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, UPDATE_VOUCHER_ERROR_TENANT);
  static final Header CREATE_VOUCHER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, CREATE_VOUCHER_ERROR_TENANT);
  static final Header GET_FUNDS_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, GET_FUNDS_ERROR_TENANT);
  static final Header CREATE_VOUCHER_LINE_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, CREATE_VOUCHER_LINES_ERROR_TENANT);
  static final Header GET_VOUCHER_LINE_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, GET_VOUCHER_LINES_ERROR_TENANT);
  static final Header DELETE_VOUCHER_LINE_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, DELETE_VOUCHER_LINES_ERROR_TENANT);
  static final Header NON_EXIST_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, NON_EXIST_CONFIG_TENANT);
  static final Header CREATE_INVOICE_TRANSACTION_SUMMARY_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT,
    CREATE_INVOICE_TRANSACTION_SUMMARY_ERROR_TENANT);
  static final Header POST_AWAITING_PAYMENT_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT,
    POST_AWAITING_PAYMENT_ERROR_TENANT);

  private final int port;
  private final Vertx vertx;

  static Table<String, HttpMethod, List<JsonObject>> serverRqRs = HashBasedTable.create();
  static HashMap<String, List<String>> serverRqQueries = new HashMap<>();

  MockServer(int port) {
    this.port = port;
    this.vertx = Vertx.vertx();
  }

  void start() throws InterruptedException, ExecutionException, TimeoutException {
    // Setup Mock Server...
    HttpServer server = vertx.createHttpServer();
    CompletableFuture<HttpServer> deploymentComplete = new CompletableFuture<>();
    server.requestHandler(defineRoutes()::handle).listen(port, result -> {
      if (result.succeeded()) {
        deploymentComplete.complete(result.result());
      } else {
        deploymentComplete.completeExceptionally(result.cause());
      }
    });
    deploymentComplete.get(60, TimeUnit.SECONDS);
  }

  void close() {
    vertx.close(res -> {
      if (res.failed()) {
        logger.error("Failed to shut down mock server", res.cause());
        fail(res.cause().getMessage());
      } else {
        logger.info("Successfully shut down mock server");
      }
    });
  }

  public static List<JsonObject> getAcqUnitsSearches() {
    return getRqRsEntries(HttpMethod.GET, ACQUISITIONS_UNITS);
  }

  public static List<JsonObject> getAcqMembershipsSearches() {
    return getRqRsEntries(HttpMethod.GET, ACQUISITIONS_MEMBERSHIPS);
  }

  public static List<JsonObject> getInvoiceSearches() {
    return getCollectionRecords(getRqRsEntries(HttpMethod.GET, INVOICES));
  }

  public static List<JsonObject> getInvoiceRetrievals() {
    return getRecordsByIds(getRqRsEntries(HttpMethod.GET, INVOICES));
  }

  public static List<JsonObject> getInvoiceUpdates() {
    return getRqRsEntries(HttpMethod.PUT, INVOICES);
  }

  public static List<JsonObject> getInvoiceCreations() {
    return getRqRsEntries(HttpMethod.POST, INVOICES);
  }

  public static List<JsonObject> getInvoiceLineSearches() {
    return getCollectionRecords(getRqRsEntries(HttpMethod.GET, INVOICE_LINES));
  }

  public static List<JsonObject> getInvoiceLineRetrievals() {
    return getRecordsByIds(getRqRsEntries(HttpMethod.GET, INVOICE_LINES));
  }

  public static List<JsonObject> getInvoiceLineUpdates() {
    return getRqRsEntries(HttpMethod.PUT, INVOICE_LINES);
  }

  public static List<JsonObject> getInvoiceLineCreations() {
    return getRqRsEntries(HttpMethod.POST, INVOICE_LINES);
  }

  private static List<JsonObject> getCollectionRecords(List<JsonObject> entries) {
    return entries.stream().filter(json -> !json.containsKey(ID)).collect(toList());
  }

  private static List<JsonObject> getRecordsByIds(List<JsonObject> entries) {
    return entries.stream().filter(json -> json.containsKey(ID)).collect(toList());
  }

  public static List<JsonObject> getBatchGroupUpdates() {
    return getRqRsEntries(HttpMethod.PUT, BATCH_GROUPS);
  }

  public static void release() {
    serverRqRs.clear();
    serverRqQueries.clear();
  }

  private Router defineRoutes() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.POST, resourcesPath(INVOICES)).handler(ctx -> handlePostEntry(ctx, Invoice.class, INVOICES));
    router.route(HttpMethod.POST, resourcesPath(INVOICE_LINES)).handler(ctx -> handlePostEntry(ctx, InvoiceLine.class, INVOICE_LINES));
    router.route(HttpMethod.POST, resourceByValuePath(VOUCHER_NUMBER_START)).handler(this::handlePostVoucherStartValue);
    router.route(HttpMethod.POST, resourcesPath(VOUCHERS)).handler(ctx -> handlePostEntry(ctx, Voucher.class, VOUCHERS));
    router.route(HttpMethod.POST, resourcesPath(VOUCHER_LINES)).handler(ctx -> handlePostEntry(ctx, VoucherLine.class, VOUCHER_LINES));
    router.route(HttpMethod.POST, "/invoice-storage/invoices/:id/documents").handler(this::handlePostInvoiceDocument);
    router.route(HttpMethod.POST, resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS)).handler(ctx -> handlePostEntry(ctx, ExportConfig.class, BATCH_VOUCHER_EXPORT_CONFIGS));
    router.route(HttpMethod.POST, "/batch-voucher-storage/export-configurations/:id/credentials").handler(this::handlePostCredentials);
    router.route(HttpMethod.POST, "/batch-voucher-storage/export-configurations/:id/credentials/test").handler(this::handlePostCredentialsTest);
    router.route(HttpMethod.POST, resourcesPath(INVOICE_TRANSACTION_SUMMARIES)).handler(this::handlePostInvoiceSummary);
    router.route(HttpMethod.POST, resourcesPath(AWAITING_PAYMENTS)).handler(this::handlePostAwaitingPayment);
    router.route(HttpMethod.POST, resourcesPath(BATCH_GROUPS)).handler(ctx -> handlePost(ctx, BatchGroup.class, BATCH_GROUPS, false));

    router.route(HttpMethod.GET, resourcesPath(INVOICES)).handler(this::handleGetInvoices);
    router.route(HttpMethod.GET, resourcesPath(INVOICE_LINES)).handler(this::handleGetInvoiceLines);
    router.route(HttpMethod.GET, resourceByIdPath(INVOICES)).handler(this::handleGetInvoiceById);
    router.route(HttpMethod.GET, resourcesPath(FOLIO_INVOICE_NUMBER)).handler(this::handleGetFolioInvoiceNumber);
    router.route(HttpMethod.GET, resourceByIdPath(INVOICE_LINES)).handler(this::handleGetInvoiceLineById);
    router.route(HttpMethod.GET, resourcesPath(INVOICE_LINE_NUMBER)).handler(this::handleGetInvoiceLineNumber);
    router.route(HttpMethod.GET, resourceByIdPath(VOUCHER_LINES)).handler(this::handleGetVoucherLineById);
    router.route(HttpMethod.GET, resourceByIdPath(VOUCHERS)).handler(this::handleGetVoucherById);
    router.route(HttpMethod.GET, resourceByIdPath(ORDER_LINES)).handler(this::handleGetPoLineById);
    router.route(HttpMethod.GET, resourcesPath(VOUCHER_NUMBER_START)).handler(this::handleGetSequence);
    router.route(HttpMethod.GET, resourcesPath(VOUCHERS)).handler(this::handleGetVouchers);
    router.route(HttpMethod.GET, resourcesPath(VOUCHER_LINES)).handler(this::handleGetVoucherLines);
    router.route(HttpMethod.GET, resourcesPath(VOUCHER_NUMBER)).handler(this::handleGetVoucherNumber);
    router.route(HttpMethod.GET, resourcesPath(FUNDS)).handler(this::handleGetFundRecords);
    router.route(HttpMethod.GET, "/configurations/entries").handler(this::handleConfigurationModuleResponse);
    router.route(HttpMethod.GET, "/invoice-storage/invoices/:id/documents").handler(this::handleGetInvoiceDocuments);
    router.route(HttpMethod.GET, "/invoice-storage/invoices/:id/documents/:documentId").handler(this::handleGetInvoiceDocumentById);
    router.route(HttpMethod.GET, resourcesPath(ACQUISITIONS_MEMBERSHIPS)).handler(this::handleGetAcquisitionsMemberships);
    router.route(HttpMethod.GET, resourcesPath(ACQUISITIONS_UNITS)).handler(this::handleGetAcquisitionsUnits);
    router.route(HttpMethod.GET, resourcesPath(BATCH_VOUCHER_EXPORT_CONFIGS)).handler(this::handleGetBatchVoucherExportConfigs);
    router.route(HttpMethod.GET, "/batch-voucher-storage/export-configurations/:id/credentials").handler(this::handleGetBatchVoucherExportConfigCredentials);
    router.route(HttpMethod.GET, resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS)).handler(this::handleGetBatchVoucherExportConfigById);
    router.route(HttpMethod.GET, resourcesPath(BATCH_GROUPS)).handler(this::handleGetBatchGroups);
    router.route(HttpMethod.GET, resourceByIdPath(BATCH_GROUPS)).handler(this::handleGetBatchGroupById);

    router.route(HttpMethod.DELETE, resourceByIdPath(INVOICES)).handler(ctx -> handleDeleteRequest(ctx, INVOICES));
    router.route(HttpMethod.DELETE, resourceByIdPath(INVOICE_LINES)).handler(ctx -> handleDeleteRequest(ctx, INVOICE_LINES));
    router.route(HttpMethod.DELETE, resourceByIdPath(VOUCHER_LINES)).handler(ctx -> handleDeleteRequest(ctx, VOUCHER_LINES));
    router.route(HttpMethod.DELETE, "/invoice-storage/invoices/:id/documents/:documentId").handler(ctx -> handleDeleteRequest(ctx, INVOICE_DOCUMENTS));
    router.route(HttpMethod.DELETE, resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS)).handler(ctx -> handleDeleteRequest(ctx, BATCH_VOUCHER_EXPORT_CONFIGS));
    router.route(HttpMethod.DELETE, resourceByIdPath(BATCH_GROUPS)).handler(ctx -> handleDeleteRequest(ctx, BATCH_GROUPS));

    router.route(HttpMethod.PUT, resourceByIdPath(INVOICES)).handler(ctx -> handlePutGenericSubObj(ctx, INVOICES));
    router.route(HttpMethod.PUT, resourceByIdPath(INVOICE_LINES)).handler(ctx -> handlePutGenericSubObj(ctx, INVOICE_LINES));
    router.route(HttpMethod.PUT, resourceByIdPath(VOUCHERS)).handler(ctx -> handlePutGenericSubObj(ctx, VOUCHERS));
    router.route(HttpMethod.PUT, resourceByIdPath(VOUCHER_LINES)).handler(ctx -> handlePutGenericSubObj(ctx, VOUCHER_LINES));
    router.route(HttpMethod.PUT, resourceByIdPath(ORDER_LINES)).handler(ctx -> handlePutGenericSubObj(ctx, ResourcePathResolver.ORDER_LINES));
    router.route(HttpMethod.PUT, resourceByIdPath(BATCH_VOUCHER_EXPORT_CONFIGS)).handler(ctx -> handlePutGenericSubObj(ctx, ResourcePathResolver.BATCH_VOUCHER_EXPORT_CONFIGS));
    router.route(HttpMethod.PUT, "/batch-voucher-storage/export-configurations/:id/credentials").handler(ctx -> handlePutGenericSubObj(ctx, BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS));
    router.route(HttpMethod.PUT, resourceByIdPath(BATCH_GROUPS)).handler(ctx -> handlePutGenericSubObj(ctx, BATCH_GROUPS));

    return router;
  }

  private void handlePostAwaitingPayment(RoutingContext ctx) {
    logger.info("handlePostAwaitingPayment got: " + ctx.request().path());
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (POST_AWAITING_PAYMENT_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject body = ctx.getBodyAsJson();
      addServerRqRsData(HttpMethod.POST, AWAITING_PAYMENTS, body);
      serverResponse(ctx, 201, APPLICATION_JSON, EMPTY);
    }
  }

  private void handlePostInvoiceSummary(RoutingContext ctx) {
    logger.info("handlePostInvoiceSummary got: " + ctx.request().path());
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (CREATE_INVOICE_TRANSACTION_SUMMARY_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject body = ctx.getBodyAsJson();
      addServerRqRsData(HttpMethod.POST, INVOICE_TRANSACTION_SUMMARIES, body);
      serverResponse(ctx, 201, APPLICATION_JSON, JsonObject.mapFrom(body).encodePrettily());
    }
  }

  private void handleGetAcquisitionsMemberships(RoutingContext ctx) {
    logger.info("handleGetAcquisitionsMemberships got: " + ctx.request().path());

    String query = StringUtils.trimToEmpty(ctx.request().getParam("query"));
    if (query.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else {

      Matcher userIdMatcher = Pattern.compile(".*userId==(\\S+).*").matcher(query);
      final String userId = userIdMatcher.find() ? userIdMatcher.group(1) : EMPTY;

      AcquisitionsUnitMembershipCollection memberships;
      try {
        memberships = new JsonObject(ApiTestBase.getMockData(ACQUISITIONS_MEMBERSHIPS_COLLECTION)).mapTo(
          AcquisitionsUnitMembershipCollection.class);
      } catch (IOException e) {
        memberships = new AcquisitionsUnitMembershipCollection();
      }

      if (StringUtils.isNotEmpty(userId)) {
        memberships.getAcquisitionsUnitMemberships().removeIf(membership -> !membership.getUserId().equals(userId));
        List<String> acquisitionsUnitIds = extractIdsFromQuery(ACQUISITIONS_UNIT_ID, "==", query);
        if (!acquisitionsUnitIds.isEmpty()) {
          memberships.getAcquisitionsUnitMemberships().removeIf(membership -> !acquisitionsUnitIds.contains(membership.getAcquisitionsUnitId()));
        }
      }

      JsonObject data = JsonObject.mapFrom(memberships.withTotalRecords(memberships.getAcquisitionsUnitMemberships().size()));
      addServerRqRsData(HttpMethod.GET, ACQUISITIONS_MEMBERSHIPS, data);
      serverResponse(ctx, 200, APPLICATION_JSON, data.encodePrettily());
    }
  }

  Supplier<List<AcquisitionsUnit>> getAcqUnitsFromFile = () -> {
    try {
      return new JsonObject(ApiTestBase.getMockData(ACQUISITIONS_UNITS_COLLECTION)).mapTo(AcquisitionsUnitCollection.class)
        .getAcquisitionsUnits();
    } catch (IOException e) {
      return Collections.emptyList();
    }
  };

  private void verifyIntegerParams(RoutingContext ctx, String[] params) {
    for(String param : params) {
      String value = StringUtils.trimToEmpty(ctx.request().getParam(param));
      if(!StringUtils.isEmpty(value)) {
        try {
          Integer.parseInt(value);        
        } catch (Exception e) {
          serverResponse(ctx, 400, TEXT_PLAIN, String.format("For input String: %s", value));
        }
      }
    }
  }
  
  private void handleGetBatchVoucherExportConfigs(RoutingContext ctx) {
    logger.info("handleGetBatchVoucherExportConfigs got: {}?{}", ctx.request().path(), ctx.request().query());

    verifyIntegerParams(ctx, new String[] {LIMIT, OFFSET});  
    
    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR) || ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      Supplier<List<ExportConfig>> getFromFile = () -> {
        try {
          return new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_CONFIGS_SAMPLE_PATH)).mapTo(ExportConfigCollection.class).getExportConfigs();
        } catch (IOException e) {
          return Collections.emptyList();
        }
      };

      ExportConfigCollection exportConfigCollection = new ExportConfigCollection();
      List<ExportConfig> exportConfigs = getMockEntries(BATCH_VOUCHER_EXPORT_CONFIGS, ExportConfig.class).orElseGet(getFromFile);

      exportConfigCollection.setExportConfigs(exportConfigs);
      exportConfigCollection.setTotalRecords(exportConfigCollection.getExportConfigs().size());

      JsonObject exportConfigsJson = JsonObject.mapFrom(exportConfigCollection);
      logger.info(exportConfigsJson.encodePrettily());

      addServerRqRsData(HttpMethod.GET, BATCH_VOUCHER_EXPORT_CONFIGS, exportConfigsJson);
      serverResponse(ctx, 200, APPLICATION_JSON, exportConfigsJson.encode());
    }
  }

  private void handleGetBatchVoucherExportConfigCredentials(RoutingContext ctx) {
    logger.info("handleGetBatchVoucherExportConfigCredentials got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject credentials = getMockCredentials();
      if (credentials == null) {
        ctx.response().setStatusCode(404).end(id);
      } else {
        // validate content against schema
        addServerRqRsData(HttpMethod.GET, BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, credentials);
        serverResponse(ctx, 200, APPLICATION_JSON, credentials.encodePrettily());
      }
    }
  }

  private void handleGetBatchVoucherExportConfigById(RoutingContext ctx) {
    logger.info("handleGetBatchVoucherExportConfigById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject exportConfig = getMockEntry(BATCH_VOUCHER_EXPORT_CONFIGS, id).orElseGet(getJsonObjectFromFile(BATCH_VOUCHER_EXPORT_CONFIG_SAMPLE_PATH, id));
      if (exportConfig == null) {
        ctx.response().setStatusCode(404).end(id);
      } else {
        // validate content against schema
        ExportConfig exportConfigSchema = exportConfig.mapTo(ExportConfig.class);
        exportConfigSchema.setId(id);
        exportConfig = JsonObject.mapFrom(exportConfigSchema);
        addServerRqRsData(HttpMethod.GET, BATCH_VOUCHER_EXPORT_CONFIGS, exportConfig);
        serverResponse(ctx, 200, APPLICATION_JSON, exportConfig.encodePrettily());
      }
    }
  }

  private void handleGetAcquisitionsUnits(RoutingContext ctx) {
    logger.info("handleGetAcquisitionsUnits got: " + ctx.request().path());

    AcquisitionsUnitCollection units = new AcquisitionsUnitCollection();

    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (!PROTECTED_READ_ONLY_TENANT.equals(tenant)) {
      units.setAcquisitionsUnits(getMockEntries(ACQUISITIONS_UNITS, AcquisitionsUnit.class).orElseGet(getAcqUnitsFromFile));
    }

    String query = StringUtils.trimToEmpty(ctx.request().getParam("query"));
    if (query.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
      return;
    } else {
      if (!query.contains(ALL_UNITS_CQL)) {
        List<Boolean> isDeleted;
        if (!query.contains(ALL_UNITS_CQL)) {
          isDeleted = Collections.singletonList(false);
        } else {
          isDeleted = extractIdsFromQuery(IS_DELETED_PROP, query).stream().map(Boolean::valueOf).collect(toList());
        }
        if (!isDeleted.isEmpty()) {
          units.getAcquisitionsUnits().removeIf(unit -> !isDeleted.contains(unit.getIsDeleted()));
        }
      }
    }

    if (query.contains("id==")) {
      List<String> ids = extractIdsFromQuery(query);
      if (!ids.isEmpty()) {
        units.getAcquisitionsUnits().removeIf(unit -> !ids.contains(unit.getId()));
      }
    }

    JsonObject data = JsonObject.mapFrom(units.withTotalRecords(units.getAcquisitionsUnits().size()));
    addServerRqRsData(HttpMethod.GET, ACQUISITIONS_UNITS, data);
    serverResponse(ctx, 200, APPLICATION_JSON, data.encodePrettily());
  }

  private void handleGetInvoiceDocuments(RoutingContext ctx) {
    logger.info("handleDocuments got: {}?{}", ctx.request().path(), ctx.request().query());

    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR) || ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      Supplier<List<Document>> getFromFile = () -> {
        try {
          return new JsonObject(getMockData(INVOICE_SAMPLE_DOCUMENTS_PATH)).mapTo(DocumentCollection.class).getDocuments();
        } catch (IOException e) {
          return Collections.emptyList();
        }
      };

      DocumentCollection documentCollection = new DocumentCollection();
      List<Document> documents = getMockEntries(INVOICE_DOCUMENTS, Document.class).orElseGet(getFromFile);

      documentCollection.setDocuments(documents);
      documentCollection.setTotalRecords(documentCollection.getDocuments().size());

      JsonObject documentsJson = JsonObject.mapFrom(documentCollection);
      logger.info(documentsJson.encodePrettily());

      addServerRqRsData(HttpMethod.GET, INVOICE_DOCUMENTS, documentsJson);
      serverResponse(ctx, 200, APPLICATION_JSON, documentsJson.encode());
    }
  }

  private void handleGetInvoiceDocumentById(RoutingContext ctx) {
    logger.info("handleGetInvoiceDocumentById got: GET " + ctx.request().path());
    String documentId = ctx.request().getParam("documentId");
    logger.info("documentId: " + documentId);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(documentId)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject document = getMockEntry(INVOICE_DOCUMENTS, documentId).orElseGet(getJsonObjectFromFile(INVOICE_DOCUMENTS_SAMPLE_PATH, documentId));
      if (document == null) {
        ctx.response().setStatusCode(404).end(documentId);
      } else {
        // validate content against schema
        InvoiceDocument documentSchema = document.mapTo(InvoiceDocument.class);
        documentSchema.getDocumentMetadata().setId(documentId);
        document = JsonObject.mapFrom(documentSchema);
        addServerRqRsData(HttpMethod.GET, INVOICE_DOCUMENTS, document);
        serverResponse(ctx, 200, APPLICATION_JSON, document.encodePrettily());
      }
    }
  }

  private void handlePostInvoiceDocument(RoutingContext ctx) {
    InvoiceDocument invoiceDocument = ctx.getBodyAsJson().mapTo(InvoiceDocument.class);
    String id = UUID.randomUUID().toString();
    invoiceDocument.getDocumentMetadata().setId(id);
    JsonObject jsonDocument = JsonObject.mapFrom(invoiceDocument);
    addServerRqRsData(HttpMethod.POST, INVOICE_DOCUMENTS, jsonDocument);

    ctx.response().putHeader(HttpHeaders.LOCATION, ctx.request().path() + "/" + id);

    serverResponse(ctx, 201, APPLICATION_JSON, jsonDocument.encodePrettily());
  }

  private void handlePostCredentials(RoutingContext ctx) {
    Credentials credentials = ctx.getBodyAsJson().mapTo(Credentials.class);
    String id = credentials.getId();
    JsonObject jsonObject = JsonObject.mapFrom(credentials);
    addServerRqRsData(HttpMethod.POST, BATCH_VOUCHER_EXPORT_CONFIGS_CREDENTIALS, jsonObject);

    ctx.response().putHeader(HttpHeaders.LOCATION, ctx.request().path() + "/" + id);

    serverResponse(ctx, 201, APPLICATION_JSON, jsonObject.encodePrettily());
  }

  private void handlePostCredentialsTest(RoutingContext ctx) {
    serverResponse(ctx, 200, APPLICATION_JSON, "");
  }

  private void handleGetVoucherLines(RoutingContext ctx) {
    logger.info("handleGetVoucherLines got: {}?{}", ctx.request().path(), ctx.request().query());

    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    String voucherId = EMPTY;
    if (queryParam.contains(VOUCHER_ID)) {
      voucherId = queryParam.split(VOUCHER_ID + "==")[1];
    }
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR) || GET_VOUCHER_LINES_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      Supplier<List<VoucherLine>> getFromFile = () -> {
        try {
          return new JsonObject(getMockData(VOUCHER_LINES_COLLECTION)).mapTo(VoucherLineCollection.class).getVoucherLines();
        } catch (IOException e) {
          return Collections.emptyList();
        }
      };

      VoucherLineCollection voucherLineCollection = new VoucherLineCollection();
      List<VoucherLine> voucherLines = getMockEntries(VOUCHER_LINES, VoucherLine.class).orElseGet(getFromFile);

      Function<VoucherLine, String> voucherIdGetter = VoucherLine::getVoucherId;
      voucherLineCollection.setVoucherLines(filterEntriesByStringValue(voucherId, voucherLines, voucherIdGetter));
      voucherLineCollection.setTotalRecords(voucherLineCollection.getVoucherLines().size());

      JsonObject voucherLinesJson = JsonObject.mapFrom(voucherLineCollection);
      logger.info(voucherLinesJson.encodePrettily());

      addServerRqRsData(HttpMethod.GET, VOUCHER_LINES, voucherLinesJson);
      serverResponse(ctx, 200, APPLICATION_JSON, voucherLinesJson.encode());
    }
  }

  private <T> List<T> filterEntriesByStringValue(String id, List<T> entries, Function<T, String> invoiceIdGetter) {
    if (StringUtils.isNotEmpty(id)) {
      return entries.stream().filter(line -> id.equals(invoiceIdGetter.apply(line))).collect(toList());
    }
    return entries;
  }

  private String resourceByValuePath(String field) {
    return resourcesPath(field) + VALUE_PATH_PARAM;
  }

  private String resourceByIdPath(String field) {
    return resourcesPath(field) + ID_PATH_PARAM;
  }

  private void handleGetInvoiceLines(RoutingContext ctx) {
    logger.info("handleGetInvoiceLines got: {}?{}", ctx.request().path(), ctx.request().query());

    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    addServerRqQuery(INVOICE_LINES, queryParam);

    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR) || GET_INVOICE_LINES_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      Supplier<List<InvoiceLine>> getFromFile = () -> {
        try {
          return new JsonObject(getMockData(INVOICE_LINES_COLLECTION)).mapTo(InvoiceLineCollection.class).getInvoiceLines();
        } catch (IOException e) {
          return Collections.emptyList();
        }
      };

      String invoiceId = EMPTY;
      List<String> includedLineIds = Collections.emptyList();
      List<String> excludedLineIds = getRqRsEntries(HttpMethod.DELETE, INVOICE_LINES).stream()
        .map(json -> json.getString(ID))
        .collect(toList());

      if (queryParam.contains(INVOICE_ID)) {
        Matcher matcher = Pattern.compile(".*" + INVOICE_ID + "==(\\S[^)]+).*").matcher(queryParam);
        invoiceId = matcher.find() ? matcher.group(1) : EMPTY;
        excludedLineIds.addAll(extractIdsFromQuery(queryParam, "<>"));
        logger.debug("Filtering lines by invoice id={} with id not IN {}", invoiceId, excludedLineIds);
      } else if (queryParam.startsWith("id==")) {
        includedLineIds = extractIdsFromQuery(queryParam);
        logger.debug("Filtering lines by id IN {}", includedLineIds);
      }

      InvoiceLineCollection invoiceLineCollection = new InvoiceLineCollection();
      List<InvoiceLine> invoiceLines = getMockEntries(INVOICE_LINES, InvoiceLine.class).orElseGet(getFromFile);
      invoiceLineCollection.setInvoiceLines(invoiceLines);

      Iterator<InvoiceLine> iterator = invoiceLines.iterator();
      while (iterator.hasNext()) {
        InvoiceLine invoiceLine = iterator.next();
        String id = invoiceLine.getId();
        if (excludedLineIds.contains(id) || (includedLineIds.isEmpty() ? !invoiceId.equals(invoiceLine.getInvoiceId()) : !includedLineIds.contains(id))) {
          iterator.remove();
        }
      }
      invoiceLineCollection.setTotalRecords(invoiceLineCollection.getInvoiceLines().size());

      JsonObject invoiceLinesJson = JsonObject.mapFrom(invoiceLineCollection);
      logger.info(invoiceLinesJson.encodePrettily());

      addServerRqRsData(HttpMethod.GET, INVOICE_LINES, invoiceLinesJson);
      serverResponse(ctx, 200, APPLICATION_JSON, invoiceLinesJson.encode());
    }
  }

  private <T> void handlePostEntry(RoutingContext ctx, Class<T> tClass, String entryName) {
    handlePost(ctx, tClass, entryName, true);
  }

  private <T> void handlePost(RoutingContext ctx, Class<T> tClass, String entryName, boolean generateId) {
    logger.info("got: " + ctx.getBodyAsString());
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (ERROR_TENANT.equals(tenant) || CREATE_VOUCHER_ERROR_TENANT.equals(tenant) || CREATE_VOUCHER_LINES_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject body = ctx.getBodyAsJson();
      if (generateId) {
        String id = UUID.randomUUID().toString();
        body.put(ID, id);
      }
      T entry = body.mapTo(tClass);
      addServerRqRsData(HttpMethod.POST, entryName, body);

      serverResponse(ctx, 201, APPLICATION_JSON, JsonObject.mapFrom(entry).encodePrettily());
    }
  }

  private void handleGetInvoiceById(RoutingContext ctx) {
    logger.info("handleGetInvoiceById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject invoice = getMockEntry(INVOICES, id).orElseGet(getJsonObjectFromFile(INVOICE_MOCK_DATA_PATH, id));
      if (invoice == null) {
        ctx.response().setStatusCode(404).end(id);
      } else {
        // validate content against schema
        Invoice invoiceSchema = invoice.mapTo(Invoice.class);
        invoiceSchema.setId(id);
        invoice = JsonObject.mapFrom(invoiceSchema);
        addServerRqRsData(HttpMethod.GET, INVOICES, invoice);
        serverResponse(ctx, 200, APPLICATION_JSON, invoice.encodePrettily());
      }
    }
  }

  private void handleGetVoucherLineById(RoutingContext ctx) {
    logger.info("handleGetVoucherLinesById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);

    try {
      String filePath = String.format(MOCK_DATA_PATH_PATTERN, VOUCHER_LINES_MOCK_DATA_PATH, id);

      JsonObject voucherLine = new JsonObject(getMockData(filePath));

      // validate content against schema
      VoucherLine voucherSchema = voucherLine.mapTo(VoucherLine.class);
      voucherSchema.setId(id);
      voucherLine = JsonObject.mapFrom(voucherSchema);
      addServerRqRsData(HttpMethod.GET, VOUCHER_LINES, voucherLine);
      serverResponse(ctx, 200, APPLICATION_JSON, voucherLine.encodePrettily());
    } catch (IOException e) {
      ctx.response().setStatusCode(404).end(id);
    }
  }

  private void handleGetInvoiceLineById(RoutingContext ctx) {
    logger.info("handleGetInvoiceLinesById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject invoiceLine = getMockEntry(INVOICE_LINES, id).orElseGet(getJsonObjectFromFile(INVOICE_LINES_MOCK_DATA_PATH, id));
      if (invoiceLine != null) {
        // validate content against schema
        InvoiceLine invoiceSchema = invoiceLine.mapTo(InvoiceLine.class);
        invoiceSchema.setId(id);
        invoiceLine = JsonObject.mapFrom(invoiceSchema);
        addServerRqRsData(HttpMethod.GET, INVOICE_LINES, invoiceLine);
        serverResponse(ctx, 200, APPLICATION_JSON, invoiceLine.encodePrettily());
      } else {
        serverResponse(ctx, 404, APPLICATION_JSON, id);
      }
    }
  }

  private void handleGetInvoices(RoutingContext ctx) {
    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    addServerRqQuery(INVOICES, queryParam);
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_DOES_NOT_EXIST)) {
      serverResponse(ctx, 404, APPLICATION_JSON, Response.Status.NOT_FOUND.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else if (queryParam.startsWith(QUERY_PARAM_START_WITH)) {
      Matcher lineIdMatcher = Pattern.compile(".*invoiceLines.id==(\\S+).*").matcher(queryParam);
      final String lineId = lineIdMatcher.find() ? lineIdMatcher.group(1) : EMPTY;

      List<Invoice> invoices;
      InvoiceCollection invoiceCollection = new InvoiceCollection();

      if (lineId.equals(SEARCH_INVOICE_BY_LINE_ID_NOT_FOUND)) {
        invoiceCollection.setInvoices(new ArrayList<Invoice>());
        invoiceCollection.setTotalRecords(invoiceCollection.getInvoices().size());
        JsonObject invoicesJson = JsonObject.mapFrom(invoiceCollection);

        addServerRqRsData(HttpMethod.GET, INVOICES, JsonObject.mapFrom(invoiceCollection));
        serverResponse(ctx, 200, APPLICATION_JSON, invoicesJson.encode());
      } else {
        try {
          invoices = new JsonObject(ApiTestBase.getMockData(MOCK_DATA_INVOICES)).mapTo(InvoiceCollection.class).getInvoices();
        } catch (IOException e) {
          invoices = new ArrayList<Invoice>();
        }

        Optional<List<Invoice>> invoiceOptional = getMockEntries(INVOICES, Invoice.class);
        Invoice invoice0 = invoiceOptional.get().get(0);
        invoices.set(0, invoice0);
        invoiceCollection.setInvoices(invoices);
        invoiceCollection.setTotalRecords(invoiceCollection.getInvoices().size());
        JsonObject invoicesJson = JsonObject.mapFrom(invoiceCollection);

        addServerRqRsData(HttpMethod.GET, INVOICES, JsonObject.mapFrom(invoiceCollection));
        serverResponse(ctx, 200, APPLICATION_JSON, invoicesJson.encode());
      }
    } else {
      JsonObject invoice = new JsonObject();
      Matcher matcher = Pattern.compile(".*vendorInvoiceNo==(\\S[^)]+).*").matcher(queryParam);
      final String vendorNumber = matcher.find() ? matcher.group(1) : EMPTY;
      switch (vendorNumber) {
      case EXISTING_VENDOR_INV_NO:
        invoice.put(TOTAL_RECORDS, 1);
        break;
      case EMPTY:
        invoice.put(TOTAL_RECORDS, 3);
        break;
      default:
        invoice.put(TOTAL_RECORDS, 0);
      }
      addServerRqRsData(HttpMethod.GET, INVOICES, invoice);
      serverResponse(ctx, 200, APPLICATION_JSON, invoice.encodePrettily());
    }
  }

  private void handleGetFolioInvoiceNumber(RoutingContext ctx) {
    if (INVOICE_NUMBER_ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      SequenceNumber seqNumber = new SequenceNumber();
      seqNumber.setSequenceNumber(FOLIO_INVOICE_NUMBER_VALUE);
      JsonObject jsonSequence = JsonObject.mapFrom(seqNumber);

      addServerRqRsData(HttpMethod.GET, FOLIO_INVOICE_NUMBER, jsonSequence);
      serverResponse(ctx, 200, APPLICATION_JSON, jsonSequence.encodePrettily());
    }
  }

  private void handlePostVoucherStartValue(RoutingContext ctx) {
    logger.info("got: " + ctx.getBodyAsString());
    String startValue = ctx.request().getParam("value");
    if (ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else if (startValue.contains(BAD_QUERY) || Integer.parseInt(startValue) < 0) {
      serverResponse(ctx, 400, TEXT_PLAIN, startValue);
    } else {
      serverResponse(ctx, 204, APPLICATION_JSON, "");
    }
  }

  private void handleDeleteRequest(RoutingContext ctx, String type) {
    String id = ctx.request().getParam(ID);
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    // Register request
    addServerRqRsData(HttpMethod.DELETE, type, new JsonObject().put(ID, id));

    if (ID_DOES_NOT_EXIST.equals(id)) {
      serverResponse(ctx, 404, TEXT_PLAIN, id);
    } else if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id) || DELETE_VOUCHER_LINES_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      ctx.response().setStatusCode(204).end();
    }
  }

  private void handleGetPoLineById(RoutingContext ctx) {
    logger.info("got: " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);

    addServerRqRsData(HttpMethod.GET, ResourcePathResolver.ORDER_LINES, new JsonObject().put(ID, id));

    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else if (ID_FOR_INTERNAL_SERVER_ERROR_PUT.equals(id)) {
      CompositePoLine poLine = new CompositePoLine();
      poLine.setId(ID_FOR_INTERNAL_SERVER_ERROR_PUT);
      serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(poLine).encodePrettily());
    } else {
      Supplier<JsonObject> getFromFile = () -> {
        String filePath = String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, id);
        try {
          return new JsonObject(getMockData(filePath));
        } catch (IOException e) {
          return null;
        }
      };

      // Attempt to find POLine in mock server memory
      JsonObject poLine = getMockEntry(ResourcePathResolver.ORDER_LINES, id).orElseGet(getFromFile);
      if (poLine == null) {
        ctx.response().setStatusCode(404).end(id);
      } else {
        // validate content against schema
        CompositePoLine poLineSchema = poLine.mapTo(CompositePoLine.class);
        poLineSchema.setId(id);
        poLine = JsonObject.mapFrom(poLineSchema);

        serverResponse(ctx, 200, APPLICATION_JSON, poLine.encodePrettily());
      }
    }
  }

  private void serverResponse(RoutingContext ctx, int statusCode, String contentType, String body) {
    ctx.response().setStatusCode(statusCode).putHeader(HttpHeaders.CONTENT_TYPE, contentType).end(body);
  }

  public static void addMockEntry(String objName, Object data) {
    addServerRqRsData(HttpMethod.OTHER, objName, data instanceof JsonObject ? (JsonObject) data : JsonObject.mapFrom(data));
  }

  private Optional<JsonObject> getMockEntry(String objName, String id) {
    return getRqRsEntries(HttpMethod.OTHER, objName).stream().filter(obj -> id.equals(obj.getString(ID))).findAny();
  }

  private <T> Optional<List<T>> getMockEntries(String objName, Class<T> tClass) {
    List<T> entryList = getRqRsEntries(HttpMethod.OTHER, objName).stream().map(entries -> entries.mapTo(tClass)).collect(toList());
    return Optional.ofNullable(entryList.isEmpty() ? null : entryList);
  }

  private static void addServerRqRsData(HttpMethod method, String objName, JsonObject data) {
    List<JsonObject> entries = getRqRsEntries(method, objName);
    entries.add(data);
    serverRqRs.put(objName, method, entries);
  }

  public static List<JsonObject> getRqRsEntries(HttpMethod method, String objName) {
    List<JsonObject> entries = serverRqRs.get(objName, method);
    if (entries == null) {
      entries = new ArrayList<>();
    }
    return entries;
  }

  private void handlePutGenericSubObj(RoutingContext ctx, String subObj) {
    logger.info("handlePutGenericSubObj got: PUT " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    addServerRqRsData(HttpMethod.PUT, subObj, ctx.getBodyAsJson());

    if (ID_DOES_NOT_EXIST.equals(id)) {
      serverResponse(ctx, 404, APPLICATION_JSON, id);
    } else if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id) || ID_FOR_INTERNAL_SERVER_ERROR_PUT.equals(id) || UPDATE_VOUCHER_ERROR_TENANT
      .equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      ctx.response().setStatusCode(204).end();
    }
  }

  private void handleGetInvoiceLineNumber(RoutingContext ctx) {
    if (INVOICE_LINE_NUMBER_ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      ctx.response().setStatusCode(500).putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON).end();
    } else {
      SequenceNumber seqNumber = new SequenceNumber();
      seqNumber.setSequenceNumber(INVOICE_LINE_NUMBER_VALUE);
      JsonObject jsonSequence = JsonObject.mapFrom(seqNumber);
      addServerRqRsData(HttpMethod.GET, INVOICE_LINE_NUMBER, jsonSequence);
      serverResponse(ctx, 200, APPLICATION_JSON, jsonSequence.encodePrettily());
    }
  }

  private void handleGetVoucherNumber(RoutingContext ctx) {
    if (VOUCHER_NUMBER_ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      ctx.response().setStatusCode(500).putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON).end();
    } else {
      SequenceNumber seqNumber = new SequenceNumber();
      seqNumber.setSequenceNumber(VOUCHER_NUMBER_VALUE);
      JsonObject jsonSequence = JsonObject.mapFrom(seqNumber);
      addServerRqRsData(HttpMethod.GET, VOUCHER_NUMBER, jsonSequence);
      serverResponse(ctx, 200, APPLICATION_JSON, jsonSequence.encodePrettily());
    }
  }

  private void handleGetVoucherById(RoutingContext ctx) {
    logger.info("handleGetVoucherById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject voucher = getMockEntry(VOUCHERS, id).orElseGet(getJsonObjectFromFile(VOUCHER_MOCK_DATA_PATH, id));
      if (voucher != null) {
        // validate content against schema
        Voucher voucherSchema = voucher.mapTo(Voucher.class);
        voucherSchema.setId(id);
        voucher = JsonObject.mapFrom(voucherSchema);
        addServerRqRsData(HttpMethod.GET, VOUCHERS, voucher);
        serverResponse(ctx, Response.Status.OK.getStatusCode(), APPLICATION_JSON, voucher.encodePrettily());
      } else {
        serverResponse(ctx, Response.Status.NOT_FOUND.getStatusCode(), TEXT_PLAIN, id);
      }
    }
  }

  private void handleGetBatchGroupById(RoutingContext ctx) {
    logger.info("handleGetBatchGroupById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject batchGroup = getMockEntry(BATCH_GROUPS, id).orElseGet(getJsonObjectFromFile(BATCH_GROUP_MOCK_DATA_PATH, id));
      if (batchGroup != null) {
        // validate content against schema
        BatchGroup batchGroupSchema = batchGroup.mapTo(BatchGroup.class);
        batchGroupSchema.setId(id);
        batchGroup = JsonObject.mapFrom(batchGroupSchema);
        addServerRqRsData(HttpMethod.GET, BATCH_GROUPS, batchGroup);
        serverResponse(ctx, Response.Status.OK.getStatusCode(), APPLICATION_JSON, batchGroup.encodePrettily());
      } else {
        serverResponse(ctx, Response.Status.NOT_FOUND.getStatusCode(), TEXT_PLAIN, id);
      }
    }
  }

  private Supplier<JsonObject> getJsonObjectFromFile(String mockDataPath, String id) {
    return () -> {
      String filePath = String.format(MOCK_DATA_PATH_PATTERN, mockDataPath, id);
      try {
        return new JsonObject(getMockData(filePath));
      } catch (IOException e) {
        return null;
      }
    };
  }

  private JsonObject getMockCredentials() {
    try {
      return new JsonObject(getMockData(BATCH_VOUCHER_EXPORT_CONFIG_CREDENTIALS_SAMPLE_PATH_WITH_ID));
    } catch (IOException e) {
      return null;
    }
  }




  private void handleGetVouchers(RoutingContext ctx) {

    logger.info("handleGetVoucher got: {}?{}", ctx.request().path(), ctx.request().query());

    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    String invoiceId = EMPTY;
    if (queryParam.contains(INVOICE_ID)) {
      invoiceId = queryParam.split(INVOICE_ID + "==")[1];
    }
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR) || GET_VOUCHERS_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      Supplier<List<Voucher>> getFromFile = () -> {
        try {
          return new JsonObject(getMockData(VOUCHERS_LIST_PATH))
            .mapTo(VoucherCollection.class).getVouchers();
        } catch (IOException e) {
          return Collections.emptyList();
        }
      };

      VoucherCollection voucherCollection = new VoucherCollection();
      List<Voucher> vouchers  = getMockEntries(VOUCHERS, Voucher.class).orElseGet(getFromFile);

      Function<Voucher, String> voucherIdGetter = Voucher::getInvoiceId;
      voucherCollection.setVouchers(filterEntriesByStringValue(invoiceId, vouchers, voucherIdGetter));
      voucherCollection.setTotalRecords(voucherCollection.getVouchers().size());

      JsonObject vouchersJson = JsonObject.mapFrom(voucherCollection);
      logger.info(vouchersJson.encodePrettily());

      addServerRqRsData(HttpMethod.GET, VOUCHERS, vouchersJson);
      serverResponse(ctx, 200, APPLICATION_JSON, vouchersJson.encode());
    }
  }

  private void handleGetBatchGroups(RoutingContext ctx) {

    logger.info("handleGetBatchGroup got: {}?{}", ctx.request().path(), ctx.request().query());

    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam(QUERY));
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR) || GET_VOUCHERS_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      Supplier<List<BatchGroup>> getFromFile = () -> {
        try {
          return new JsonObject(getMockData(BATCH_GROUPS_LIST_PATH))
            .mapTo(BatchGroupCollection.class).getBatchGroups();
        } catch (IOException e) {
          return Collections.emptyList();
        }
      };

      BatchGroupCollection batchGroupCollection = new BatchGroupCollection();
      List<BatchGroup> batchGroups  = getMockEntries(BATCH_GROUPS, BatchGroup.class).orElseGet(getFromFile);

      batchGroupCollection.setBatchGroups(batchGroups);
      batchGroupCollection.setTotalRecords(batchGroupCollection.getBatchGroups().size());

      JsonObject batchGroupsJson = JsonObject.mapFrom(batchGroupCollection);
      logger.info(batchGroupsJson.encodePrettily());

      addServerRqRsData(HttpMethod.GET, BATCH_GROUPS, batchGroupsJson);
      serverResponse(ctx, 200, APPLICATION_JSON, batchGroupsJson.encode());
    }
  }

  private void handleGetSequence(RoutingContext ctx) {
    if (ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      SequenceNumber sequenceNumber = new SequenceNumber().withSequenceNumber("200");
      serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(sequenceNumber).encode());
    }
  }

  private void handleConfigurationModuleResponse(RoutingContext ctx) {
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    Configs configs = new Configs();
    switch (tenant) {
      case NON_EXIST_CONFIG_TENANT : {
        configs.setTotalRecords(0);
        serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(configs).encodePrettily());
        return;
      }
      case ERROR_CONFIG_TENANT : {
        serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
        return;
      }
      case INVALID_PREFIX_CONFIG_TENANT : {
        Config voucherNumberPrefixConfig = new Config()
          .withModule(INVOICE_CONFIG_MODULE_NAME)
          .withConfigName(VOUCHER_NUMBER_CONFIG_NAME)
          .withValue(new JsonObject().put(VOUCHER_NUMBER_PREFIX_CONFIG, INVALID_PREFIX).toString());
        configs.withConfigs(Collections.singletonList(voucherNumberPrefixConfig)).setTotalRecords(1);
        serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(configs).encodePrettily());
        return;
      }
      default: {

        Config localeConfig = new Config()
          .withModule(SYSTEM_CONFIG_MODULE_NAME)
          .withConfigName(LOCALE_SETTINGS)
          .withValue("{\"locale\":\"en-US\",\"timezone\":\"Pacific/Yap\",\"currency\":\"GBP\"}");
        Config voucherNumberPrefixConfig = new Config()
          .withModule(INVOICE_CONFIG_MODULE_NAME)
          .withConfigName(VOUCHER_NUMBER_CONFIG_NAME)
          .withValue(new JsonObject().put(VOUCHER_NUMBER_PREFIX_CONFIG, TEST_PREFIX).toString());
        configs.getConfigs().add(localeConfig);
        configs.getConfigs().add(voucherNumberPrefixConfig);
        configs.withTotalRecords(configs.getConfigs().size());
        }
        serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(configs).encodePrettily());
      }
  }

  private void handleGetFundRecords(RoutingContext ctx) {
    logger.info("handleGetFundRecords got: " + ctx.request().path());
    String tenant = ctx.request().getHeader(OKAPI_HEADER_TENANT);
    String query = ctx.request().getParam(QUERY);

    if (query.contains(ID_FOR_INTERNAL_SERVER_ERROR) || GET_FUNDS_ERROR_TENANT.equals(tenant)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      try {
        FundCollection fundCollection = new JsonObject(ApiTestBase.getMockData(FUNDS_MOCK_DATA_PATH + "fundsCollection.json")).mapTo(FundCollection.class);
        List<Fund> funds = fundCollection.getFunds();

        if (query.startsWith("id==")) {
          List<String> fundIds = extractIdsFromQuery(query);
          funds.removeIf(item -> !fundIds.contains(item.getId()));
        }

        fundCollection.setTotalRecords(funds.size());
        JsonObject fundsJson = JsonObject.mapFrom(fundCollection);
        addServerRqRsData(HttpMethod.GET, FUNDS, fundsJson);

        ctx.response()
          .setStatusCode(200)
          .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
          .end(fundsJson.encodePrettily());
      } catch (Exception e) {
        serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR.getReasonPhrase());
      }
    }
  }

  private void addServerRqQuery(String objName, String query) {
    serverRqQueries.computeIfAbsent(objName, key -> new ArrayList<>())
      .add(query);
  }

  static List<String> getQueryParams(String resourceType) {
    return serverRqQueries.getOrDefault(resourceType, Collections.emptyList());
  }

  private List<String> extractIdsFromQuery(String query) {
    return extractIdsFromQuery(query, "==");
  }

  private List<String> extractIdsFromQuery(String query, String relation) {
    return extractIdsFromQuery(ID, relation, query);
  }

  private List<String> extractIdsFromQuery(String fieldName, String relation, String query) {
    Matcher matcher = Pattern.compile(".*" + fieldName + relation + "\\(?(.+)\\).*").matcher(query);
    if (matcher.find()) {
      return StreamEx.split(matcher.group(1), " or ").toList();
    } else {
      return Collections.emptyList();
    }
  }
}
