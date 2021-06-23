package org.folio.services.voucher;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

public class VoucherRetrieveService {
  public static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  private static final String VOUCHER_ENDPOINT = resourcesPath(VOUCHERS_STORAGE);
  private static final String VOUCHER_BY_ID_ENDPOINT = resourcesPath(VOUCHERS_STORAGE) + "/{id}";
  private final RestClient restClient;

  public VoucherRetrieveService(RestClient restClient) {
    this.restClient = restClient;
  }

  public CompletableFuture<Voucher> getVoucherById(String voucherId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_BY_ID_ENDPOINT).withId(voucherId);
    return restClient.get(requestEntry, requestContext, Voucher.class);
  }

  public CompletableFuture<VoucherCollection> getVouchers(int limit, int offset, String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_ENDPOINT)
        .withQuery(query)
        .withLimit(limit)
        .withOffset(offset);
    return restClient.get(requestEntry, requestContext, VoucherCollection.class);
  }

  public CompletableFuture<Voucher> getVoucherByInvoiceId(String invoiceId, RequestContext requestContext) {
    return getVouchers(1, 0, String.format(QUERY_BY_INVOICE_ID, invoiceId), requestContext)
      .thenApply(VoucherCollection::getVouchers)
      .thenApply(vouchers -> vouchers.isEmpty() ? null : vouchers.get(0));
  }

}
