package org.folio.services.invoice;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_LINE_NOT_FOUND;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;

public class InvoiceLineService {

  private static final String INVOICE_ID_QUERY =  "invoiceId==%s";

  final RestClient invoiceLineRestClient;

  public InvoiceLineService(RestClient invoiceLineRestClient) {
    this.invoiceLineRestClient = invoiceLineRestClient;
  }

  public CompletableFuture<InvoiceLine> getInvoiceLine(String invoiceLineId, RequestContext requestContext) {
    return invoiceLineRestClient.getById(invoiceLineId, requestContext, InvoiceLine.class)
      .exceptionally(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceLineId").withValue(invoiceLineId));
        throw new HttpException(404, INVOICE_LINE_NOT_FOUND.toError().withParameters(parameters));
      });
  }

  public CompletableFuture<InvoiceLineCollection> getInvoiceLines(String invoiceId, RequestContext requestContext) {
    String query = String.format(INVOICE_ID_QUERY, invoiceId);
    return invoiceLineRestClient.get(query, 0, 100, requestContext, InvoiceLineCollection.class);
  }

  public CompletableFuture<List<InvoiceLine>> getInvoiceLinesRelatedForOrder(List<String> orderPoLineIds, String invoiceId, RequestContext requestContext) {
    return getInvoiceLines(invoiceId, requestContext)
      .thenApply(invoiceLines -> invoiceLines.getInvoiceLines().stream()
        .filter(invoiceLine -> orderPoLineIds.contains(invoiceLine.getPoLineId())).collect(toList()));
  }
}
