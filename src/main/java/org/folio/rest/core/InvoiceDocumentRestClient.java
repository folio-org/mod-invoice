package org.folio.rest.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceDocument;

import io.vertx.core.Future;

public class InvoiceDocumentRestClient extends RestClient {
  private static final Logger logger = LogManager.getLogger();

  public Future<InvoiceDocument> postInvoiceDocument(String endpoint, InvoiceDocument document, RequestContext requestContext) {
    var caseInsensitiveHeader = convertToCaseInsensitiveMap(requestContext.getHeaders());
    return getVertxWebClient(requestContext.getContext())
      .postAbs(buildAbsEndpoint(caseInsensitiveHeader, endpoint))
      .putHeaders(caseInsensitiveHeader)
      .expect(SUCCESS_RESPONSE_PREDICATE)
      // TODO: consider to make streaming transfer for large files
      .sendJson(document)
      .map(bufferHttpResponse -> bufferHttpResponse.bodyAsJsonObject().mapTo(InvoiceDocument.class))
      .onFailure(logger::error);
  }
}
