package org.folio.rest.impl;

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
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Invoice;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.AbstractHelper.ID;
import static org.folio.rest.impl.ApiTestBase.FOLIO_INVOICE_NUMBER_VALUE;
import static org.folio.rest.impl.ApiTestBase.getMockData;
import static org.folio.rest.impl.InvoicesApiTest.BAD_QUERY;
import static org.folio.rest.impl.InvoicesApiTest.EXISTING_VENDOR_INV_NO;
import static org.folio.rest.impl.InvoicesApiTest.ID_FOR_INTERNAL_SERVER_ERROR;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_MOCK_DATA_PATH;
import static org.junit.Assert.fail;

public class MockServer {

  private static final Logger logger = LoggerFactory.getLogger(MockServer.class);

  static Table<String, HttpMethod, List<JsonObject>> serverRqRs = HashBasedTable.create();
  private static final String INVOICE_NUMBER_ERROR_TENANT = "po_number_error_tenant";
  private static final String ERROR_TENANT = "error_tenant";
  static final Header INVOICE_NUMBER_ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, INVOICE_NUMBER_ERROR_TENANT);
  static final Header ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, ERROR_TENANT);

  private static final String ID_PATH_PARAM = ":" + ID;
  private static final String TOTAL_RECORDS = "totalRecords";

  public static final String ID_DOES_NOT_EXIST = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";


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

    router.route(HttpMethod.GET, resourcesPath(INVOICES)).handler(this::handleGetInvoices);
    router.route(HttpMethod.GET, resourceByIdPath(INVOICES, ID_PATH_PARAM)).handler(this::handleGetInvoiceById);
    router.route(HttpMethod.GET, resourcesPath(FOLIO_INVOICE_NUMBER)).handler(this::handleGetFolioInvoiceNumber);

    router.route(HttpMethod.DELETE, resourceByIdPath(INVOICES, ID_PATH_PARAM)).handler(ctx -> handleDeleteRequest(ctx, INVOICES));

    router.route(HttpMethod.PUT, resourceByIdPath(INVOICES, ID_PATH_PARAM)).handler(ctx -> handlePutGenericSubObj(ctx, INVOICES));
    return router;
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

    try {

      String filePath = String.format("%s%s.json", INVOICE_MOCK_DATA_PATH, id);

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

  private void handlePutGenericSubObj(RoutingContext ctx, String subObj) {
    logger.info("handlePutGenericSubObj got: PUT " + ctx.request().path());
    String id = ctx.request().getParam(ID);

    addServerRqRsData(HttpMethod.PUT, subObj, ctx.getBodyAsJson());

    if (ID_DOES_NOT_EXIST.equals(id)) {
      serverResponse(ctx, 404, APPLICATION_JSON, id);
    } else if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id) || ctx.getBodyAsString().contains("500500500500")) {
      serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR.getReasonPhrase());
    } else {
      ctx.response()
        .setStatusCode(204)
        .end();
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


}
