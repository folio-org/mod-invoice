package org.folio.services.finance.transaction;

import org.folio.rest.acq.model.finance.OrderTransactionSummary;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import java.util.concurrent.CompletableFuture;

import static org.folio.invoices.utils.ResourcePathResolver.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class OrderTransactionSummaryService {
  private static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT = resourcesPath(ORDER_TRANSACTION_SUMMARIES);
  private static final String ORDER_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT = ORDER_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";
  private final RestClient restClient;

  public OrderTransactionSummaryService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<Void> updateOrderTransactionSummary(OrderTransactionSummary orderTransactionSummary,
      RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORDER_TRANSACTION_SUMMARIES_BY_ID_ENDPOINT)
      .withId(orderTransactionSummary.getId());
    return restClient.put(requestEntry, orderTransactionSummary, requestContext);
  }

}
