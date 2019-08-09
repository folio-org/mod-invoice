package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_DOCUMENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceDocument;
import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DocumentsApiTest extends ApiTestBase {
  private static final Logger logger = LoggerFactory.getLogger(DocumentsApiTest.class);
  private static final String INVOICE_ID = "733cafd3-895f-4e33-87b7-bf40dc3c8069";
  private static final String DOCUMENT_ENDPOINT = "/invoice/invoices/" + INVOICE_ID + "/documents";
  private static final String DOCUMENT_ENDPOINT_WITH_ID = "/invoice/invoices/" + INVOICE_ID + "/documents/%s";

  static final String INVOICE_DOCUMENTS_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "documents/";
  static final String INVOICE_DOCUMENT_SAMPLE_PATH = BASE_MOCK_DATA_PATH + "documents/10a34f8a-98d1-45af-a9f6-14b7174ceb51.json";
  static final String INVOICE_SAMPLE_DOCUMENTS_PATH = BASE_MOCK_DATA_PATH + "documents/documents.json";
  static final String DOCUMENT_ID = "10a34f8a-98d1-45af-a9f6-14b7174ceb51";


  @Test
  public void testPostAndDocument() throws IOException {
    logger.info("=== Test create document ===");

    String body = getMockData(INVOICE_DOCUMENT_SAMPLE_PATH);

    final InvoiceDocument createdDoc = verifyPostResponse(DOCUMENT_ENDPOINT, body, prepareHeaders(X_OKAPI_TENANT), APPLICATION_JSON,201).as(InvoiceDocument.class);

    String docId = createdDoc.getDocumentMetadata().getId();
    assertThat(docId, notNullValue());
    assertThat(MockServer.serverRqRs.get(INVOICE_DOCUMENTS, HttpMethod.POST), hasSize(1));
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
  public void testGetDocuments() throws IOException {
    logger.info("=== Test Get Invoices by without query - get 200 by successful retrieval of documents ===");

    final InvoiceCollection resp = verifySuccessGet(DOCUMENT_ENDPOINT, InvoiceCollection.class);

    assertEquals(2, resp.getTotalRecords().intValue());
  }

  @Test
  public void testUpdateDocument() throws IOException {
    logger.info("=== Test edit document - error 400 expected ===");

    JsonObject jsonBody = new JsonObject(getMockData(INVOICE_DOCUMENT_SAMPLE_PATH));
    String id = jsonBody.getJsonObject("documentMetadata").getString("id");
    String response = verifyPut(String.format(DOCUMENT_ENDPOINT_WITH_ID, id), jsonBody, TEXT_PLAIN, 400).body().asString();
    Assert.assertTrue(response.contains("API resource does not support this HTTP method"));
  }

  @Test
  public void testDeleteDocument() {
    logger.info("=== Test delete document by id ===");
    verifyDeleteResponse(String.format(DOCUMENT_ENDPOINT_WITH_ID, VALID_UUID), "", 204);
  }

}
