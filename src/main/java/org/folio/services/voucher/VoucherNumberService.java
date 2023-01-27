package org.folio.services.voucher;

import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_START;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.SequenceNumber;

import io.vertx.core.Future;

public class VoucherNumberService {

  private final RestClient restClient;

  public VoucherNumberService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<SequenceNumber> getNextNumber(RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(VOUCHER_NUMBER_STORAGE));
    return restClient.get(requestEntry, SequenceNumber.class, requestContext);
  }

  public Future<SequenceNumber> getStartValue(RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(VOUCHER_NUMBER_START));
    return restClient.get(requestEntry, SequenceNumber.class, requestContext);
  }

  /**
   * This endpoint is a means for the UI to set/reset the start value of the voucher-number sequence
   * @param value start value to be set/reset
   * @return completable future on success or {@code null} if validation fails or an exception if any issue happens
   */
  public Future<Void> setStartValue(String value, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(resourcesPath(VOUCHER_NUMBER_START) + "/{startNumber}");
    requestEntry.withPathParameter("startNumber", value);
    return restClient.postEmptyBody(requestEntry, requestContext);
  }

}
