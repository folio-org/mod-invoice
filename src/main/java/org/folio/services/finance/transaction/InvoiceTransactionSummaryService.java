package org.folio.services.finance.transaction;

import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.Future;

public class InvoiceTransactionSummaryService {

  private static final String INVOICE_TRANSACTION_SUMMARIES_ENDPOINT = resourcesPath(INVOICE_TRANSACTION_SUMMARIES);
  private static final String INVOICE_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT = INVOICE_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";

  private final RestClient restClient;

  public InvoiceTransactionSummaryService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<InvoiceTransactionSummary> createInvoiceTransactionSummary(InvoiceTransactionSummary transactionSummary, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT);
    return restClient.post(requestEntry, transactionSummary, InvoiceTransactionSummary.class, requestContext);
  }

  public Future<Void> updateInvoiceTransactionSummary(InvoiceTransactionSummary invoiceTransactionSummary, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT)
        .withId(invoiceTransactionSummary.getId());
    return restClient.put(requestEntry, invoiceTransactionSummary, requestContext);
  }
}
