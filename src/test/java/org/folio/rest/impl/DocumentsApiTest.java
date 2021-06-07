package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.commons.io.FileUtils.ONE_MB;
import static org.folio.invoices.utils.ErrorCodes.DOCUMENT_IS_TOO_LARGE;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_DOCUMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.RestAssured;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


@ExtendWith(VertxExtension.class)
public class DocumentsApiTest extends ApiTestBase {
  private static final Logger logger = LogManager.getLogger(DocumentsApiTest.class);
  private static final String INVOICE_ID = "733cafd3-895f-4e33-87b7-bf40dc3c8069";
  private static final String DOCUMENT_ENDPOINT = "/invoice/invoices/" + INVOICE_ID + "/documents";
  private static final String DOCUMENT_ENDPOINT_WITH_ID = "/invoice/invoices/" + INVOICE_ID + "/documents/%s";

  static final String INVOICE_DOCUMENTS_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "documents/";
  static final String INVOICE_DOCUMENT_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "documents/10a34f8a-98d1-45af-a9f6-14b7174ceb51.json";
  static final String INVOICE_SAMPLE_DOCUMENTS_PATH = BASE_MOCK_DATA_PATH + "documents/documents.json";
  static final String DOCUMENT_ID = "10a34f8a-98d1-45af-a9f6-14b7174ceb51";
  static final int size = (int) (30 * ONE_MB);


  @Test
  void testPostAndDocument(VertxTestContext context) throws Throwable {
    logger.info("=== Test create document ===");

    String mock = getMockData(INVOICE_DOCUMENT_SAMPLE_PATH);

    HttpRequest<Buffer> request = WebClient.wrap(Vertx.vertx().createHttpClient()).requestAbs(HttpMethod.POST, RestAssured.baseURI + DOCUMENT_ENDPOINT);


    prepareRequestHeaders(request);
    context.assertComplete(request.sendBuffer(Buffer.buffer(mock)))
      .onComplete(event -> {
        if (event.succeeded()) {
          verifySuccessResponse(context, event.result());
        } else {
          processException(context, event.cause());
        }
      });
    assertTrue(context.awaitCompletion(30, TimeUnit.SECONDS));

    if (context.failed()) {
      fail(context.causeOfFailure());
    }
  }



  @Test
  void testPostAndDocumentOversize(VertxTestContext context) throws Throwable {
    logger.info("=== Test create document - oversize ===");

    InvoiceDocument document = new JsonObject(getMockData(INVOICE_DOCUMENT_SAMPLE_PATH)).mapTo(InvoiceDocument.class);
    String stringData = StringUtils.repeat("*", size);

    document.getContents().setData(stringData);
    HttpRequest<Buffer> request = WebClient.wrap(Vertx.vertx().createHttpClient()).requestAbs(HttpMethod.POST, RestAssured.baseURI + DOCUMENT_ENDPOINT);
    prepareRequestHeaders(request);

    context.assertComplete(request.sendBuffer(Buffer.buffer(JsonObject.mapFrom(document).encode())))
            .onComplete(event -> {
              if (event.succeeded()) {
                verifyErrorResponse(context, event.result());
              } else {
                processException(context, event.cause());
              }
            });
    assertTrue(context.awaitCompletion(60, TimeUnit.SECONDS));

    if (context.failed()) {
      fail(context.causeOfFailure());
    }
  }

  @Test
  public void testGetDocument() {
    logger.info("=== Test get document by id ===");

    String endpoint = String.format(DOCUMENT_ENDPOINT_WITH_ID, DOCUMENT_ID);

    final InvoiceDocument retrievedDocument = verifySuccessGet(endpoint, InvoiceDocument.class);
    assertThat(MockServer.serverRqRs.get(INVOICE_DOCUMENTS, HttpMethod.GET), hasSize(1));
    assertEquals(DOCUMENT_ID, retrievedDocument.getDocumentMetadata().getId());
  }

  @Test
  public void testGetDocuments() {
    logger.info("=== Test Get Invoices by without query - get 200 by successful retrieval of documents ===");

    final InvoiceCollection resp = verifySuccessGet(DOCUMENT_ENDPOINT, InvoiceCollection.class);

    assertEquals(2, resp.getTotalRecords().intValue());
  }

  @Test
  public void testUpdateDocument() throws IOException {
    logger.info("=== Test edit document - error 400 expected ===");

    JsonObject jsonBody = new JsonObject(getMockData(INVOICE_DOCUMENT_SAMPLE_PATH));
    String id = jsonBody.getJsonObject("documentMetadata").getString("id");
    verifyPut(String.format(DOCUMENT_ENDPOINT_WITH_ID, id), jsonBody, "", 405);
  }

  @Test
  public void testDeleteDocument() {
    logger.info("=== Test delete document by id ===");
    verifyDeleteResponse(String.format(DOCUMENT_ENDPOINT_WITH_ID, VALID_UUID), "", 204);
  }

  private void verifySuccessResponse(VertxTestContext context, HttpResponse<Buffer> response) {
    context.verify(() -> {
      assertEquals(201, response.statusCode());
      assertNotNull(response.headers().get(CONTENT_TYPE));
      assertEquals(1 , MockServer.serverRqRs.get(INVOICE_DOCUMENTS, HttpMethod.POST).size());
      assertNotNull(new JsonObject(response.body()).mapTo(InvoiceDocument.class).getDocumentMetadata().getId());
    });
    context.completeNow();
  }

  private void verifyErrorResponse(VertxTestContext context, HttpResponse<Buffer> response) {
    context.verify(() -> {
      assertEquals(413, response.statusCode());
      assertNotNull(response.headers().get(CONTENT_TYPE));
      assertNull(MockServer.serverRqRs.get(INVOICE_DOCUMENTS, HttpMethod.POST));
      Errors errors = new JsonObject(response.body()).mapTo(Errors.class);
      List<Error> errs = errors.getErrors();
      assertEquals(1, errors.getTotalRecords());
      assertEquals(1, errs.size());
      assertEquals(errs.get(0).getCode(), DOCUMENT_IS_TOO_LARGE.getCode());
    });
    context.completeNow();
  }

  private void processException(VertxTestContext context, Throwable t) {
    logger.info("Exception calling: " + DOCUMENT_ENDPOINT, t);
    context.failNow(t);
  }

  private void prepareRequestHeaders(HttpRequest<Buffer> request) {

    MultiMap headers = new HeadersMultiMap();
    headers.add(X_OKAPI_URL.getName(), X_OKAPI_URL.getValue())
            .add(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue())
            .add(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue())
            .add("Content-type", "application/octet-stream")
            .add("Accept", "application/json, text/plain");
    request.putHeaders(headers);
  }
}
