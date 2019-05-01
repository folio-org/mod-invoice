package org.folio.rest.imp;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.junit.Assert.fail;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.lang3.StringUtils;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MockServer {

  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");
  }
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String BAD_QUERY = "unprocessableQuery";
  private static final String APPLICATION_JSON = "application/json";
  private static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  private static final String EXISTING_VENDOR_INV_NO = "existingVendorInvoiceNo";
  private static final String ID_DOES_NOT_EXIST = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
  static final String ID = "id";

  static Table<String, HttpMethod, List<JsonObject>> serverRqRs = HashBasedTable.create();
  private static final Logger logger = LoggerFactory.getLogger(MockServer.class);

  final int port;
  final Vertx vertx;

  MockServer(int port) {
    this.port = port;
    this.vertx = Vertx.vertx();
  }

  void start(TestContext context) {
    // Setup Mock Server...
    HttpServer server = vertx.createHttpServer();

    final Async async = context.async();
    server.requestHandler(defineRoutes()::accept).listen(port, result -> {
      if (result.failed()) {
        logger.warn(result.cause());
      }
      context.assertTrue(result.succeeded());
      async.complete();
    });
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

  Router defineRoutes() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.route(HttpMethod.GET, resourcesPath(INVOICES)).handler(this::handleGetInvoices);

    router.route(HttpMethod.PUT, resourcePath(INVOICE_LINES)).handler(ctx -> handlePutGenericSubObj(ctx, INVOICE_LINES));
    return router;
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

  private void handleGetInvoices(RoutingContext ctx) {
    String queryParam = StringUtils.trimToEmpty(ctx.request().getParam("query"));
    if (queryParam.contains(BAD_QUERY)) {
      serverResponse(ctx, 400, APPLICATION_JSON, Status.BAD_REQUEST.getReasonPhrase());
    } else if (queryParam.contains(ID_FOR_INTERNAL_SERVER_ERROR)) {
      serverResponse(ctx, 500, APPLICATION_JSON, Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
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

  private void handlePutGenericSubObj(RoutingContext ctx, String subObj) {
    logger.info("handlePutGenericSubObj got: PUT " + ctx.request().path());
    String id = ctx.request().getParam(ID);

    addServerRqRsData(HttpMethod.PUT, subObj, ctx.getBodyAsJson());

    if (ID_DOES_NOT_EXIST.equals(id)) {
      serverResponse(ctx, 404, APPLICATION_JSON, id);
    } else if (ID_FOR_INTERNAL_SERVER_ERROR.equals(id)) {
      serverResponse(ctx, 500, APPLICATION_JSON, INTERNAL_SERVER_ERROR);
    } else {
      ctx.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
        .setStatusCode(204)
        .end();
    }
  }

  private String resourcePath(String subObjName) {
    return resourceByIdPath(subObjName) + ":id";
  }

}
