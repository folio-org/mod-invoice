package org.folio.services.invoice;

import static java.util.stream.Collectors.toList;
import static org.folio.completablefuture.FolioVertxCompletableFuture.allOf;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;

public class InvoiceLineService {

  private static final String INVOICE_LINES_ENDPOINT = resourcesPath(INVOICE_LINES);
  private static final String INVOICE_LINE_BY_ID_ENDPOINT = INVOICE_LINES_ENDPOINT + "/{id}";

  private static final String INVOICE_ID_QUERY =  "invoiceId==%s";

  final RestClient restClient;

  public InvoiceLineService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<InvoiceLine> getInvoiceLine(String invoiceLineId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINE_BY_ID_ENDPOINT).withId(invoiceLineId);
    return restClient.get(requestEntry, requestContext, InvoiceLine.class)
      .exceptionally(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceLineId").withValue(invoiceLineId));
        throw new HttpException(404, INVOICE_LINE_NOT_FOUND.toError().withParameters(parameters));
      });
  }

  public CompletableFuture<InvoiceLineCollection> getInvoiceLines(String invoiceId, RequestContext requestContext) {
    String query = String.format(INVOICE_ID_QUERY, invoiceId);
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINES_ENDPOINT)
        .withQuery(query)
        .withLimit(Integer.MAX_VALUE)
        .withOffset(0);
    return restClient.get(requestEntry, requestContext, InvoiceLineCollection.class);
  }

  public CompletableFuture<List<InvoiceLine>> getInvoiceLinesRelatedForOrder(List<String> orderPoLineIds, String invoiceId, RequestContext requestContext) {
    return getInvoiceLines(invoiceId, requestContext)
      .thenApply(invoiceLines -> invoiceLines.getInvoiceLines().stream()
        .filter(invoiceLine -> orderPoLineIds.contains(invoiceLine.getPoLineId())).collect(toList()));
  }

  public CompletableFuture<Void> persistInvoiceLines(List<InvoiceLine> lines,  RequestContext requestContext) {
    return allOf(requestContext.getContext(), lines.stream()
                                                   .map(invoiceLine -> persistInvoiceLine(invoiceLine, requestContext))
                                                   .toArray(CompletableFuture[]::new));
  }

  public CompletableFuture<List<InvoiceLine>> getInvoiceLinesWithTotals(Invoice invoice,  RequestContext requestContext) {
    return getInvoiceLines(invoice.getId(), requestContext)
      .thenApply(InvoiceLineCollection::getInvoiceLines)
      .thenApply(lines -> {
        lines.forEach(line -> HelperUtils.calculateInvoiceLineTotals(line, invoice));
        return lines;
      });
  }

  private CompletableFuture<Void> persistInvoiceLine(InvoiceLine invoiceLine,  RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINE_BY_ID_ENDPOINT).withId(invoiceLine.getId());
    return restClient.put(requestEntry, invoiceLine, requestContext);
  }
}
