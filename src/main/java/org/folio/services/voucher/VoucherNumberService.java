package org.folio.services.voucher;

import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.SequenceNumber;

public class VoucherNumberService {

  private final RestClient restClient;

  public VoucherNumberService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<SequenceNumber> getNextNumber(RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(VOUCHER_NUMBER_STORAGE));
    return restClient.get(requestEntry, requestContext, SequenceNumber.class);
  }

  public CompletableFuture<SequenceNumber> getStartValue(RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(VOUCHER_NUMBER_START));
    return restClient.get(requestEntry, requestContext, SequenceNumber.class);
  }

  /**
   * This endpoint is a means for the UI to set/reset the start value of the voucher-number sequence
   * @param value start value to be set/reset
   * @return completable future on success or {@code null} if validation fails or an exception if any issue happens
   */
  public CompletableFuture<Void> setStartValue(String value, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(VOUCHER_NUMBER_START) + "/{startNumber}");
    requestEntry.withPathParameter("startNumber", value);
    return restClient.post(requestEntry, null, requestContext, SequenceNumber.class)
        .thenAccept(sequenceNumber -> {});
  }

}
