package org.folio.rest.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceDocument;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class InvoiceDocumentRestClient extends RestClient {
  private static final Logger log = LogManager.getLogger(InvoiceDocumentRestClient.class);

  public Future<InvoiceDocument> postInvoiceDocument(String endpoint, InvoiceDocument document, RequestContext requestContext) {
    if (log.isDebugEnabled()) {
      log.debug("Sending 'POST {}' with body: {}", endpoint, JsonObject.mapFrom(document).encodePrettily());
    }
    var caseInsensitiveHeader = convertToCaseInsensitiveMap(requestContext.getHeaders());
    return getVertxWebClient(requestContext.getContext())
      .postAbs(buildAbsEndpoint(caseInsensitiveHeader, endpoint))
      .putHeaders(caseInsensitiveHeader)
      .expect(SUCCESS_RESPONSE_PREDICATE)
      // TODO: consider to make streaming transfer for large files
      .sendJson(document)
      .map(bufferHttpResponse -> bufferHttpResponse.bodyAsJsonObject().mapTo(InvoiceDocument.class))
      .onFailure(log::error);
  }
}
