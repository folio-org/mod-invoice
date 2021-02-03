package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_DOCUMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByParentIdAndIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.jaxrs.model.DocumentCollection;
import org.folio.rest.jaxrs.model.InvoiceDocument;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import org.folio.completablefuture.FolioVertxCompletableFuture;

class DocumentHelper extends AbstractHelper {
  private static final String GET_DOCUMENTS_BY_QUERY = resourcesPath(INVOICE_DOCUMENTS) + SEARCH_PARAMS;

  DocumentHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  CompletableFuture<InvoiceDocument> createDocument(String invoiceId, InvoiceDocument document) {
    JsonObject jsonDocument = JsonObject.mapFrom(document);
    String endpoint = String.format(resourcesPath(ResourcePathResolver.INVOICE_DOCUMENTS), invoiceId);
    return createRecordInStorage(jsonDocument, endpoint).thenApply(id -> {
      document.getDocumentMetadata().setId(id);
      return document;
    });
  }

  CompletableFuture<DocumentCollection> getDocumentsByInvoiceId(String invoiceId, int limit, int offset, String query) {
    CompletableFuture<DocumentCollection> future = new FolioVertxCompletableFuture<>(ctx);
    String queryParam = getEndpointWithQuery(query, logger);
    String endpoint = String.format(GET_DOCUMENTS_BY_QUERY, invoiceId, limit, offset, queryParam, lang);

    handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(documents -> FolioVertxCompletableFuture.supplyBlockingAsync(ctx, () -> {
        if (logger.isInfoEnabled()) {
          logger.info("Successfully retrieved documents: {}", documents.encodePrettily());
        }
        return documents.mapTo(DocumentCollection.class);
      }))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        future.completeExceptionally(t.getCause());
        return null;
      });
    return future;
  }

  CompletableFuture<Void> deleteDocument(String invoiceId, String documentId) {
    String endpoint = resourceByParentIdAndIdPath(INVOICE_DOCUMENTS, invoiceId, documentId, lang);
    return handleDeleteRequest(endpoint, httpClient, ctx, okapiHeaders, logger);
  }

  CompletableFuture<InvoiceDocument> getDocumentByInvoiceIdAndDocumentId(String invoiceId, String documentId) {
    String endpoint = resourceByParentIdAndIdPath(INVOICE_DOCUMENTS, invoiceId, documentId, lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApply(jsonDocument -> {
      if (logger.isInfoEnabled()) {
        logger.info("Successfully retrieved document by id: {}", jsonDocument.encodePrettily());
      }
      return jsonDocument.mapTo(InvoiceDocument.class);
    });
  }
}
