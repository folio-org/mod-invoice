package org.folio.rest.impl;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.ApiTestBase.BASE_MOCK_DATA_PATH;
import static org.folio.rest.impl.ApiTestBase.FOLIO_INVOICE_NUMBER_VALUE;
import static org.folio.rest.impl.ApiTestBase.ID_DOES_NOT_EXIST;
import static org.folio.rest.impl.ApiTestBase.ID_FOR_INTERNAL_SERVER_ERROR;
import static org.folio.rest.impl.ApiTestBase.ID_FOR_INTERNAL_SERVER_ERROR_PUT;
import static org.folio.rest.impl.ApiTestBase.INVOICE_LINE_NUMBER_VALUE;
import static org.folio.rest.impl.ApiTestBase.getMockData;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_ID;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_MOCK_DATA_PATH;
import static org.folio.rest.impl.VoucherLinesApiTest.VOUCHER_LINES_MOCK_DATA_PATH;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesApiTest.EXISTING_VENDOR_INV_NO;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_MOCK_DATA_PATH;
import static org.folio.rest.impl.VouchersApiTest.VOUCHER_MOCK_DATA_PATH;
import static org.folio.rest.impl.VouchersApiTest.EXISTING_VOUCHER_NUMBER;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.CompositePoLine;
import org.folio.rest.acq.model.Invoice;
import org.folio.rest.acq.model.InvoiceLine;
import org.folio.rest.acq.model.InvoiceLineCollection;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.acq.model.Voucher;
import org.folio.rest.acq.model.VoucherLine;
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

public class MockServer {

  private static final Logger logger = LoggerFactory.getLogger(MockServer.class);
  private static final String ORDER_LINES_BY_ID_PATH = "/orders/order-lines/:id";
  public static final String MOCK_DATA_PATH_PATTERN = "%s%s.json";

  static Table<String, HttpMethod, List<JsonObject>> serverRqRs = HashBasedTable.create();
  private static final String INVOICE_NUMBER_ERROR_TENANT = "po_number_error_tenant";
  private static final String INVOICE_LINE_NUMBER_ERROR_TENANT = "invoice_line_number_error_tenant";
  private static final String ERROR_TENANT = "error_tenant";
  private static final String INVOICE_LINES_COLLECTION = BASE_MOCK_DATA_PATH + "invoiceLines/invoice_lines.json";
  private static final String PO_LINES_MOCK_DATA_PATH = BASE_MOCK_DATA_PATH + "poLines/";
  private static final String ID_PATH_PARAM = ":" + ID;
  private static final String VALUE_PATH_PARAM = ":value";
  private static final String TOTAL_RECORDS = "totalRecords";
  static final String PO_LINES = "poLines";

  static final Header INVOICE_NUMBER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, INVOICE_NUMBER_ERROR_TENANT);
  static final Header ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, ERROR_TENANT);
  static final Header INVOICE_LINE_NUMBER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, INVOICE_LINE_NUMBER_ERROR_TENANT);

  private final int port;
  private final Vertx vertx;

  MockServer(int port) {
    this.port = port;
    this.vertx = Vertx.vertx();
  }

  void start() throws InterruptedException, ExecutionException, TimeoutException {
    // Setup Mock Server...
    HttpServer server = vertx.createHttpServer();
    CompletableFuture<HttpServer> deploymentComplete = new CompletableFuture<>();
    server.requestHandler(defineRoutes()::accept).listen(port, result -> {
      if(result.succeeded()) {
        deploymentComplete.complete(result.result());
      }
      else {
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

  private Router defineRoutes() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.POST, ResourcePathResolver.resourcesPath(INVOICES)).handler(this::handlePostInvoice);
    router.route(HttpMethod.POST, resourcesPath(INVOICE_LINES)).handler(this::handlePostInvoiceLine);
    router.route(HttpMethod.POST, resourceByIdPath(VOUCHER_NUMBER_START, VALUE_PATH_PARAM)).handler(this::handlePostVoucherStartValue);

    router.route(HttpMethod.GET, resourcesPath(INVOICES)).handler(this::handleGetInvoices);
    router.route(HttpMethod.GET, resourcesPath(INVOICE_LINES)).handler(this::handleGetInvoiceLines);
    router.route(HttpMethod.GET, resourceByIdPath(INVOICES, ID_PATH_PARAM)).handler(this::handleGetInvoiceById);
    router.route(HttpMethod.GET, resourcesPath(FOLIO_INVOICE_NUMBER)).handler(this::handleGetFolioInvoiceNumber);
    router.route(HttpMethod.GET, resourceByIdPath(INVOICE_LINES, ID_PATH_PARAM)).handler(this::handleGetInvoiceLineById);
    router.route(HttpMethod.GET, resourcesPath(INVOICE_LINE_NUMBER)).handler(this::handleGetInvoiceLineNumber);
    router.route(HttpMethod.GET, resourceByIdPath(VOUCHER_LINES, ID_PATH_PARAM)).handler(this::handleGetVoucherLineById);
    router.route(HttpMethod.GET, resourceByIdPath(VOUCHERS, ID_PATH_PARAM)).handler(this::handleGetVoucherById);
    router.route(HttpMethod.GET, ORDER_LINES_BY_ID_PATH).handler(this::handleGetPoLineById);
    router.route(HttpMethod.GET, resourcesPath(VOUCHER_NUMBER_START)).handler(this::handleGetSequence);
    router.route(HttpMethod.GET, resourcesPath(VOUCHERS)).handler(this::handleGetVouchers);

    router.route(HttpMethod.DELETE, resourceByIdPath(INVOICES, ID_PATH_PARAM)).handler(ctx -> handleDeleteRequest(ctx, INVOICES));
    router.route(HttpMethod.DELETE, resourceByIdPath(INVOICE_LINES, ID_PATH_PARAM)).handler(ctx -> handleDeleteRequest(ctx, INVOICE_LINES));

    router.route(HttpMethod.PUT, resourceByIdPath(INVOICES, ID_PATH_PARAM)).handler(ctx -> handlePutGenericSubObj(ctx, INVOICES));
    router.route(HttpMethod.PUT, resourceByIdPath(INVOICE_LINES, ID_PATH_PARAM)).handler(ctx -> handlePutGenericSubObj(ctx, INVOICE_LINES));
    router.route(HttpMethod.PUT, resourceByIdPath(VOUCHER_LINES, ID_PATH_PARAM)).handler(ctx -> handlePutGenericSubObj(ctx, VOUCHER_LINES));
    router.route(HttpMethod.PUT, ORDER_LINES_BY_ID_PATH).handler(ctx -> handlePutGenericSubObj(ctx, PO_LINES));
    return router;
  }

  private void handleGetInvoiceLines(RoutingContext ctx) {
    logger.info("handleGetInvoiceLines got: {}?{}", ctx.request().path(), ctx.request().query());

    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam("query"));
    String invoiceId = EMPTY;
    if (queryParam.contains(INVOICE_ID)) {
      invoiceId = queryParam.split(INVOICE_ID + "==")[1];
    }
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      try {
        InvoiceLineCollection invoiceLineCollection = new InvoiceLineCollection();
        List<JsonObject> jsonObjects  = serverRqRs.get(INVOICE_LINES, HttpMethod.POST);
        if (CollectionUtils.isNotEmpty(jsonObjects)) {
          List<InvoiceLine> invoiceLines =  jsonObjects.stream().map(entries -> entries.mapTo(InvoiceLine.class)).collect(toList());
          invoiceLineCollection.setInvoiceLines(invoiceLines);
        } else {
          invoiceLineCollection = new JsonObject(ApiTestBase.getMockData(INVOICE_LINES_COLLECTION)).mapTo(InvoiceLineCollection.class);
        }

        invoiceLineCollection.setInvoiceLines(filterLineByInvoiceId(invoiceId, invoiceLineCollection));
        invoiceLineCollection.setTotalRecords(invoiceLineCollection.getInvoiceLines().size());

        JsonObject poLines = JsonObject.mapFrom(invoiceLineCollection);
        logger.info(poLines.encodePrettily());

        addServerRqRsData(HttpMethod.GET, INVOICE_LINES, poLines);
        serverResponse(ctx, 200, APPLICATION_JSON, poLines.encode());
      } catch (IOException e) {
        InvoiceLineCollection poLineCollection = new InvoiceLineCollection();
        poLineCollection.setTotalRecords(0);
        serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(poLineCollection).encodePrettily());
      }

    }
  }

  private List<InvoiceLine> filterLineByInvoiceId(String invoiceId, InvoiceLineCollection invoiceLineCollection) {
    if (StringUtils.isNotEmpty(invoiceId)) {
      return invoiceLineCollection.getInvoiceLines().stream()
        .filter(invoiceLine -> invoiceId.equals(invoiceLine.getInvoiceId()))
        .collect(toList());
    }
    return invoiceLineCollection.getInvoiceLines();
  }

  private void handlePostInvoice(RoutingContext ctx) {
    logger.info("got: " + ctx.getBodyAsString());
    if (ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      String id = UUID.randomUUID().toString();
      JsonObject body = ctx.getBodyAsJson();
      body.put(ID, id);
      Invoice po = body.mapTo(Invoice.class);
      addServerRqRsData(HttpMethod.POST, INVOICES, body);

      serverResponse(ctx, 201, APPLICATION_JSON, JsonObject.mapFrom(po).encodePrettily());
    }
  }

  private void handleGetInvoiceById(RoutingContext ctx) {
    logger.info("handleGetInvoiceById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);
    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      try {

        String filePath = String.format(MOCK_DATA_PATH_PATTERN, INVOICE_MOCK_DATA_PATH, id);

        JsonObject invoice = new JsonObject(getMockData(filePath));

        // validate content against schema
        Invoice invoiceSchema = invoice.mapTo(Invoice.class);
        invoiceSchema.setId(id);
        invoice = JsonObject.mapFrom(invoiceSchema);
        addServerRqRsData(HttpMethod.GET, INVOICES, invoice);
        serverResponse(ctx, 200, APPLICATION_JSON, invoice.encodePrettily());
      } catch (IOException e) {
        ctx.response().setStatusCode(404).end(id);
      }
    }
  }

  private void handleGetVoucherLineById(RoutingContext ctx) {
    logger.info("handleGetVoucherLinesById got: GET " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);

    try {
      String filePath = null;
      filePath = String.format(MOCK_DATA_PATH_PATTERN, VOUCHER_LINES_MOCK_DATA_PATH, id);

      JsonObject voucherLine = new JsonObject(getMockData(filePath));

      // validate content against schema
      VoucherLine voucherSchema = voucherLine.mapTo(org.folio.rest.acq.model.VoucherLine.class);
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
      try {
        String filePath = String.format(MOCK_DATA_PATH_PATTERN, INVOICE_LINES_MOCK_DATA_PATH, id);
  
        JsonObject invoiceLine = new JsonObject(getMockData(filePath));
  
        // validate content against schema
       InvoiceLine invoiceSchema = invoiceLine.mapTo(InvoiceLine.class);
        invoiceSchema.setId(id);
        invoiceLine = JsonObject.mapFrom(invoiceSchema);
        addServerRqRsData(HttpMethod.GET, INVOICE_LINES, invoiceLine);
        serverResponse(ctx, 200, APPLICATION_JSON, invoiceLine.encodePrettily());
      } catch (IOException e) {
        ctx.response().setStatusCode(404).end(id);
      }
    }
  }

  private void handleGetInvoices(RoutingContext ctx) {
    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam("query"));
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject invoice = new JsonObject();
      final String VENDOR_INVOICE_NUMBER_QUERY = "vendorInvoiceNo==";
      switch (queryParam) {
        case VENDOR_INVOICE_NUMBER_QUERY + EXISTING_VENDOR_INV_NO:
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
    if(INVOICE_NUMBER_ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
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
    String startValue = ctx.request()
      .getParam("value");
     if (ERROR_TENANT.equals(ctx.request()
      .getHeader(OKAPI_HEADER_TENANT))) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else if (startValue.contains(BAD_QUERY) || Integer.parseInt(startValue) < 0) {
      serverResponse(ctx, 400, TEXT_PLAIN, startValue);
    } else {
      serverResponse(ctx, 204, APPLICATION_JSON, "");
    }
  }

  private void handleDeleteRequest(RoutingContext ctx, String type) {
    String id = ctx.request().getParam(ID);

    // Register request
    addServerRqRsData(HttpMethod.DELETE, type, new JsonObject().put(ID, id));

    if (ID_DOES_NOT_EXIST.equals(id)) {
      serverResponse(ctx, 404, TEXT_PLAIN, id);
    } else if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      ctx.response()
        .setStatusCode(204)
        .end();
    }
  }

  private void handleGetPoLineById(RoutingContext ctx) {
    logger.info("got: " + ctx.request().path());
    String id = ctx.request().getParam(ID);
    logger.info("id: " + id);

    addServerRqRsData(HttpMethod.GET, PO_LINES, new JsonObject().put(ID, id));

    if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else if (ID_FOR_INTERNAL_SERVER_ERROR_PUT.equals(id)) {
      CompositePoLine poLine = new CompositePoLine();
      poLine.setId(ID_FOR_INTERNAL_SERVER_ERROR_PUT);
      serverResponse(ctx, 200, APPLICATION_JSON, JsonObject.mapFrom(poLine).encodePrettily());
    } else {
      try {

        JsonObject pol = null;

        // Attempt to find POLine in mock server memory
        Map<String, List<JsonObject>> column = serverRqRs.column(HttpMethod.POST);
        if (MapUtils.isNotEmpty(column) && CollectionUtils.isNotEmpty(column.get(PO_LINES))) {
          List<JsonObject> objects = new ArrayList<>(column.get(PO_LINES));
          Comparator<JsonObject> comparator = Comparator.comparing(o -> o.getString(ID));
          objects.sort(comparator);
          int ind = Collections.binarySearch(objects, new JsonObject().put(ID, id), comparator);
          if(ind > -1) {
            pol = objects.get(ind);
          }
        }

        // If previous step has no result then attempt to find POLine in stubs
        if (pol == null) {
          CompositePoLine poLine = new JsonObject(ApiTestBase.getMockData(String.format(MOCK_DATA_PATH_PATTERN, PO_LINES_MOCK_DATA_PATH, id))).mapTo(CompositePoLine.class);

          pol = JsonObject.mapFrom(poLine);
        }

        serverResponse(ctx, 200, APPLICATION_JSON, pol.encodePrettily());
      } catch (IOException e) {
        serverResponse(ctx, 404, APPLICATION_JSON, id);
      }
    }
  }

  private void serverResponse(RoutingContext ctx, int statusCode, String contentType, String body) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
      .end(body);
  }

  private void addServerRqRsData(HttpMethod method, String objName, JsonObject data) {
    List<JsonObject> entries = serverRqRs.get(objName, method);
    if (entries == null) {
      entries = new ArrayList<>();
    }
    entries.add(data);
    serverRqRs.put(objName, method, entries);
  }

  private void handlePutGenericSubObj(RoutingContext ctx, String subObj) {
    logger.info("handlePutGenericSubObj got: PUT " + ctx.request().path());
    String id = ctx.request().getParam(ID);

    addServerRqRsData(HttpMethod.PUT, subObj, ctx.getBodyAsJson());

    if (ID_DOES_NOT_EXIST.equals(id)) {
      serverResponse(ctx, 404, APPLICATION_JSON, id);
    } else if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id) || ID_FOR_INTERNAL_SERVER_ERROR_PUT.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      ctx.response()
        .setStatusCode(204)
        .end();
    }
  }
  
  private void handlePostInvoiceLine(RoutingContext ctx) {
    logger.info("got: " + ctx.getBodyAsString());
    if (ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      serverResponse(ctx, 500, TEXT_PLAIN, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      String id = UUID.randomUUID().toString();
      JsonObject body = ctx.getBodyAsJson();
      body.put(ID, id);
      InvoiceLine invoiceLine = body.mapTo(InvoiceLine.class);
      addServerRqRsData(HttpMethod.POST, INVOICE_LINES, body);
      serverResponse(ctx, 201, APPLICATION_JSON, JsonObject.mapFrom(invoiceLine).encodePrettily());
    }
  }
  
  private void handleGetInvoiceLineNumber(RoutingContext ctx) {
    if(INVOICE_LINE_NUMBER_ERROR_TENANT.equals(ctx.request().getHeader(OKAPI_HEADER_TENANT))) {
      ctx.response()
        .setStatusCode(500)
        .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
        .end();
    } else {
      SequenceNumber seqNumber = new SequenceNumber();
      seqNumber.setSequenceNumber(INVOICE_LINE_NUMBER_VALUE);
      JsonObject jsonSequence = JsonObject.mapFrom(seqNumber);
      addServerRqRsData(HttpMethod.GET, INVOICE_LINE_NUMBER, jsonSequence);
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
      try {

        String filePath = String.format(MOCK_DATA_PATH_PATTERN, VOUCHER_MOCK_DATA_PATH, id);

        JsonObject voucher = new JsonObject(getMockData(filePath));

        // validate content against schema
        Voucher voucherSchema = voucher.mapTo(Voucher.class);
        voucherSchema.setId(id);
        voucher = JsonObject.mapFrom(voucherSchema);
        addServerRqRsData(HttpMethod.GET, VOUCHERS, voucher);
        serverResponse(ctx, 200, APPLICATION_JSON, voucher.encodePrettily());
      } catch (IOException e) {
        ctx.response().setStatusCode(404).end(id);
      }
    }
  }

  private void handleGetVouchers(RoutingContext ctx) {
    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam("query"));
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Response.Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      JsonObject voucher = new JsonObject();
      final String VOUCHER_NUMBER_QUERY = "voucherNumber==";
      switch (queryParam) {
        case VOUCHER_NUMBER_QUERY + EXISTING_VOUCHER_NUMBER:
          voucher.put(TOTAL_RECORDS, 1);
          break;
        case EMPTY:
          voucher.put(TOTAL_RECORDS, 4);
          break;
        default:
          voucher.put(TOTAL_RECORDS, 0);
      }
      addServerRqRsData(HttpMethod.GET, VOUCHERS, voucher);
      serverResponse(ctx, 200, APPLICATION_JSON, voucher.encodePrettily());
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
}