package org.folio.services.voucher;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

public class VoucherRetrieveService {
  public static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  private final RestClient voucherStorageRestClient;

  public VoucherRetrieveService(RestClient voucherStorageRestClient) {
    this.voucherStorageRestClient = voucherStorageRestClient;
  }

  public CompletableFuture<Voucher> getVoucherById(String voucherId, RequestContext requestContext) {
    return voucherStorageRestClient.getById(voucherId, requestContext, Voucher.class);
  }

  public CompletableFuture<VoucherCollection> getVouchers(int limit, int offset, String query, RequestContext requestContext) {
    return voucherStorageRestClient.get(query, offset, limit, requestContext, VoucherCollection.class);
  }

  public CompletableFuture<Voucher> getVoucherByInvoiceId(String invoiceId, RequestContext requestContext) {
    return getVouchers(1, 0, String.format(QUERY_BY_INVOICE_ID, invoiceId), requestContext)
      .thenApply(VoucherCollection::getVouchers)
      .thenApply(vouchers -> vouchers.isEmpty() ? null : vouchers.get(0));
  }

}
