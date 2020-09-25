package org.folio.services.invoice;

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

  final RestClient invoiceLineRestClient;

  public InvoiceLineService(RestClient invoiceLineRestClient) {
    this.invoiceLineRestClient = invoiceLineRestClient;
  }

  public CompletableFuture<Boolean> isInvoiceLineIsLastForOrder(InvoiceLine invoiceLine, RequestContext requestContext) {
    String query = String.format("poLineId==%s and invoiceId==%s", invoiceLine.getPoLineId(), invoiceLine.getInvoiceId());
    return invoiceLineRestClient.get(query,0 , 100, requestContext, InvoiceLineCollection.class)
      .thenApply(line -> line.getTotalRecords() == 1);
  }

  public CompletableFuture<InvoiceLine> getInvoiceLineById(String invoiceLineId, RequestContext requestContext) {
    return invoiceLineRestClient.getById(invoiceLineId, requestContext, InvoiceLine.class)
      .exceptionally(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceLineId").withValue(invoiceLineId));
        throw new HttpException(404, INVOICE_LINE_NOT_FOUND.toError().withParameters(parameters));
      });
  }
}
