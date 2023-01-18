package org.folio.rest.impl;

import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_DOCUMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByParentIdAndIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Map;

import org.folio.invoices.utils.ResourcePathResolver;
import org.folio.rest.core.InvoiceDocumentRestClient;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.DocumentCollection;
import org.folio.rest.jaxrs.model.InvoiceDocument;

import io.vertx.core.Context;
import io.vertx.core.Future;


class DocumentHelper extends AbstractHelper {
  private static final String GET_DOCUMENTS_BY_QUERY = resourcesPath(INVOICE_DOCUMENTS) + SEARCH_PARAMS;
  // TODO: move restClient to service layer
  private final RestClient restClient;
  DocumentHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.restClient = new RestClient();
  }

  Future<InvoiceDocument> createDocument(String invoiceId, InvoiceDocument document) {
    String endpoint = String.format(resourcesPath(ResourcePathResolver.INVOICE_DOCUMENTS), invoiceId);
    InvoiceDocumentRestClient invoiceDocumentRestClient = new InvoiceDocumentRestClient();

    return invoiceDocumentRestClient.postInvoiceDocument(endpoint, document, new RequestContext(ctx, okapiHeaders));
  }

  Future<DocumentCollection> getDocumentsByInvoiceId(String invoiceId, int limit, int offset, String query) {
    String queryParam = getEndpointWithQuery(query);
    String endpoint = String.format(GET_DOCUMENTS_BY_QUERY, invoiceId, limit, offset, queryParam);

    return restClient.get(endpoint, DocumentCollection.class, buildRequestContext());
  }

  Future<Void> deleteDocument(String invoiceId, String documentId) {
    String endpoint = resourceByParentIdAndIdPath(INVOICE_DOCUMENTS, invoiceId, documentId);
    return restClient.delete(endpoint, buildRequestContext());
  }

  Future<InvoiceDocument> getDocumentByInvoiceIdAndDocumentId(String invoiceId, String documentId) {
    String endpoint = resourceByParentIdAndIdPath(INVOICE_DOCUMENTS, invoiceId, documentId);

    return restClient.get(endpoint, InvoiceDocument.class, buildRequestContext());
  }


}
