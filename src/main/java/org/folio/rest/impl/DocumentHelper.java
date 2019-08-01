package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_DOCUMENTS_ENDPOINT;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.jaxrs.model.DocumentCollection;
import org.folio.rest.jaxrs.model.InvoiceDocument;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

class DocumentHelper extends AbstractHelper {

  DocumentHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  CompletableFuture<InvoiceDocument> createDocument(String invoiceId, InvoiceDocument document) {
    JsonObject jsonDocument = JsonObject.mapFrom(document);
    String endpoint = String.format(resourcesPath(ResourcePathResolver.INVOICE_DOCUMENTS_ENDPOINT), invoiceId);
    return createRecordInStorage(jsonDocument, endpoint).thenApply(id -> {
      document.getDocumentMetadata().setId(id);
      return document;
    });
  }

  CompletableFuture<DocumentCollection> getDocumentsByInvoiceId(String invoiceId) {
    CompletableFuture<DocumentCollection> future = new VertxCompletableFuture<>(ctx);

    String endpoint = String.format(resourcesPath(ResourcePathResolver.INVOICE_DOCUMENTS_ENDPOINT), invoiceId);
    handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(documents -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> {
        if (logger.isInfoEnabled()) {
          logger.info("Successfully retrieved documents", documents.encodePrettily());
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
    return handleDeleteRequest(buildDocumentEndpoint(invoiceId, documentId), httpClient, ctx, okapiHeaders, logger);
  }

  private String buildDocumentEndpoint(String invoiceId, String documentId) {
    String invoiceDocumentsEndpoint = String.format(resourcesPath(INVOICE_DOCUMENTS_ENDPOINT), invoiceId);
    return invoiceDocumentsEndpoint + String.format("/%s?lang=%s", documentId, lang);
  }

  CompletableFuture<InvoiceDocument> getDocumentByInvoiceIdAndDocumentId(String invoiceId, String documentId) {
    String endpoint = buildDocumentEndpoint(invoiceId, documentId);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApply(jsonDocument -> {
      if (logger.isInfoEnabled()) {
        logger.info("Successfully retrieved document by id: " + jsonDocument.encodePrettily());
      }
      return jsonDocument.mapTo(InvoiceDocument.class);
    });
  }
}
