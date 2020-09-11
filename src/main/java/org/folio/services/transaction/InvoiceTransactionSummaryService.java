package org.folio.services.transaction;

import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

import java.util.concurrent.CompletableFuture;

public class InvoiceTransactionSummaryService {

  private final RestClient invoiceTransactionSummaryRestClient;

  public InvoiceTransactionSummaryService(RestClient invoiceTransactionSummaryRestClient) {
    this.invoiceTransactionSummaryRestClient = invoiceTransactionSummaryRestClient;
  }

  public CompletableFuture<InvoiceTransactionSummary> createInvoiceTransactionSummary(InvoiceTransactionSummary transactionSummary, RequestContext requestContext) {
    return invoiceTransactionSummaryRestClient.post(transactionSummary, requestContext, InvoiceTransactionSummary.class);
  }
}
