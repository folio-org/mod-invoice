package org.folio.services.invoice;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.ERROR_CREATING_INVOICE_LINE;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_LINE_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.Collections;
import java.util.List;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;

import io.vertx.core.Future;

public class InvoiceLineService {

  private static final Logger log = LogManager.getLogger();

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
        if (throwable instanceof HttpException && ((HttpException) throwable).getCode() == 404) {
          List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceLineId").withValue(invoiceLineId));
          throw new HttpException(404, INVOICE_LINE_NOT_FOUND.toError().withParameters(parameters));
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

  public Future<List<InvoiceLine>> createInvoiceLines(List<InvoiceLine> invoiceLines,  RequestContext requestContext) {
    return HelperUtils.executeWithSemaphores(invoiceLines,
      invoiceLine -> createInvoiceLine(invoiceLine, requestContext),
      requestContext);
  }

  public Future<Void> updateInvoiceLines(List<InvoiceLine> invoiceLines,  RequestContext requestContext) {
    return HelperUtils.executeWithSemaphores(invoiceLines,
        invoiceLine -> updateInvoiceLine(invoiceLine, requestContext),
        requestContext)
      .mapEmpty();
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

  public Future<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_LINES_ENDPOINT);
    return restClient.post(requestEntry, invoiceLine, InvoiceLine.class, requestContext)
    .recover(throwable -> {
      Parameter p1 = new Parameter().withKey("invoiceId").withValue(invoiceLine.getInvoiceId());
      Parameter p2 = new Parameter().withKey("invoiceLineNumber").withValue(invoiceLine.getInvoiceLineNumber());
      Error error = ERROR_CREATING_INVOICE_LINE.toError().withParameters(List.of(p1, p2));
      log.error(JsonObject.mapFrom(error));
      throw new HttpException(500, error);
    });
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
