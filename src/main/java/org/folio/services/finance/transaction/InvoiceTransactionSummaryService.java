package org.folio.services.finance.transaction;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

public class InvoiceTransactionSummaryService {

  private final RestClient invoiceTransactionSummaryRestClient;

  public InvoiceTransactionSummaryService(RestClient invoiceTransactionSummaryRestClient) {
    this.invoiceTransactionSummaryRestClient = invoiceTransactionSummaryRestClient;
  }

  public CompletableFuture<InvoiceTransactionSummary> createInvoiceTransactionSummary(InvoiceTransactionSummary transactionSummary, RequestContext requestContext) {
    return invoiceTransactionSummaryRestClient.post(transactionSummary, requestContext, InvoiceTransactionSummary.class);
  }

  public CompletableFuture<Void> updateInvoiceTransactionSummary(InvoiceTransactionSummary invoiceTransactionSummary, RequestContext requestContext) {
    return invoiceTransactionSummaryRestClient.put(invoiceTransactionSummary.getId(), invoiceTransactionSummary, requestContext);
  }
}
