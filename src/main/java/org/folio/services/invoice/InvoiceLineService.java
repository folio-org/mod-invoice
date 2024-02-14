package org.folio.services.invoice;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_LINE_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.List;

import io.vertx.core.Future;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;

public class InvoiceLineService {

  private static final String INVOICE_LINES_ENDPOINT = resourcesPath(INVOICE_LINES);
  private static final String INVOICE_LINE_BY_ID_ENDPOINT = INVOICE_LINES_ENDPOINT + "/{id}";
  private static final String INVOICE_LINE_NUMBER_ENDPOINT = resourcesPath(INVOICE_LINE_NUMBER) + "?" + INVOICE_ID + "=";

  private static final String INVOICE_ID_QUERY =  "invoiceId==%s";

  final RestClient restClient;

  public InvoiceLineService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<InvoiceLine> getInvoiceLine(String invoiceLineId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINE_BY_ID_ENDPOINT).withId(invoiceLineId);
    return restClient.get(requestEntry, InvoiceLine.class, requestContext)
      .recover(throwable -> {
        if (throwable instanceof HttpException httpException &&  httpException.getCode() == 404) {
          String message = String.format(INVOICE_LINE_NOT_FOUND.getDescription() + " : %s", throwable.getMessage());
          var param = new Parameter().withKey("invoiceLineId").withValue(invoiceLineId);
          var error = INVOICE_LINE_NOT_FOUND.toError().withMessage(message).withParameters(List.of(param));
          throw new HttpException(404, error);
        }
        return Future.failedFuture(throwable);
      });
  }

  public Future<InvoiceLineCollection> getInvoiceLinesByInvoiceId(String invoiceId, RequestContext requestContext) {
    String query = String.format(INVOICE_ID_QUERY, invoiceId);
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINES_ENDPOINT)
        .withQuery(query)
        .withLimit(Integer.MAX_VALUE)
        .withOffset(0);
    return restClient.get(requestEntry, InvoiceLineCollection.class, requestContext);
  }

  public Future<InvoiceLineCollection> getInvoiceLines(String endpoint, RequestContext requestContext) {
    return restClient.get(endpoint, InvoiceLineCollection.class, requestContext);
  }

  public Future<InvoiceLineCollection> getInvoiceLines(RequestEntry requestEntry, RequestContext requestContext) {
    return restClient.get(requestEntry.buildEndpoint(), InvoiceLineCollection.class, requestContext);
  }

  public Future<List<InvoiceLine>> getInvoiceLinesRelatedForOrder(List<String> orderPoLineIds, String invoiceId, RequestContext requestContext) {
    return getInvoiceLinesByInvoiceId(invoiceId, requestContext)
      .map(invoiceLines -> invoiceLines.getInvoiceLines().stream()
        .filter(invoiceLine -> orderPoLineIds.contains(invoiceLine.getPoLineId())).collect(toList()));
  }

  public Future<Void> persistInvoiceLines(List<InvoiceLine> lines,  RequestContext requestContext) {
    var futures = lines.stream()
      .map(invoiceLine -> persistInvoiceLine(invoiceLine, requestContext))
      .collect(toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  public Future<Void> updateInvoiceLine(InvoiceLine invoiceLine, RequestContext requestContext) {
    return restClient.put(resourceByIdPath(INVOICE_LINES, invoiceLine.getId()), invoiceLine, requestContext);
  }

  public Future<List<InvoiceLine>> getInvoiceLinesWithTotals(Invoice invoice,  RequestContext requestContext) {
    return getInvoiceLinesByInvoiceId(invoice.getId(), requestContext)
      .map(InvoiceLineCollection::getInvoiceLines)
      .map(lines -> {
        lines.forEach(line -> HelperUtils.calculateInvoiceLineTotals(line, invoice));
        return lines;
      });
  }

  private Future<Void> persistInvoiceLine(InvoiceLine invoiceLine,  RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINE_BY_ID_ENDPOINT).withId(invoiceLine.getId());
    return restClient.put(requestEntry, invoiceLine, requestContext);
  }

  public Future<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINES_ENDPOINT);
    return restClient.post(requestEntry, invoiceLine, InvoiceLine.class, requestContext);
  }

  public Future<Void> deleteInvoiceLine(String lineId, RequestContext requestContext) {
    var endpoint = resourceByIdPath(INVOICE_LINES, lineId);
    return restClient.delete(endpoint, requestContext);
  }

  public Future<String> generateLineNumber(String invoiceId, RequestContext requestContext) {
    return restClient.get(getInvoiceLineNumberEndpoint(invoiceId), SequenceNumber.class, requestContext)
      .map(SequenceNumber::getSequenceNumber);
  }

  private String getInvoiceLineNumberEndpoint(String id) {
    return INVOICE_LINE_NUMBER_ENDPOINT + id;
  }
}
