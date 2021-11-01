package org.folio.services;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.buildIdsChunks;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.impl.VoucherLineHelper;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

import io.vertx.core.Context;

public class VoucherLinesRetrieveService {
  static final int MAX_IDS_FOR_GET_RQ = 15;
  private final VoucherLineHelper voucherLineHelper;

  public VoucherLinesRetrieveService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this.voucherLineHelper = new VoucherLineHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Map<String, List<VoucherLine>>> getVoucherLinesMap(VoucherCollection voucherCollection) {
    CompletableFuture<Map<String, List<VoucherLine>>> future = new CompletableFuture<>();
    getVoucherLinesByChunks(voucherCollection.getVouchers())
      .thenApply(voucherLineCollections ->
        voucherLineCollections.stream()
          .map(VoucherLineCollection::getVoucherLines)
          .collect(toList()).stream()
          .flatMap(List::stream)
          .collect(Collectors.toList()))
      .thenAccept(voucherLines -> future.complete(voucherLines.stream().collect(groupingBy(VoucherLine::getVoucherId))))
      .thenAccept(v -> voucherLineHelper.closeHttpClient())
      .exceptionally(t -> {
        future.completeExceptionally(t);
        voucherLineHelper.closeHttpClient();
        return null;
      });
    return future;
  }

  public CompletableFuture<List<VoucherLineCollection>> getVoucherLinesByChunks(List<Voucher> vouchers) {
    List<CompletableFuture<VoucherLineCollection>> invoiceFutureList = buildIdsChunks(vouchers, MAX_IDS_FOR_GET_RQ).values()
      .stream()
      .map(this::buildVoucherLinesQuery)
      .map(query -> voucherLineHelper.getVoucherLines(Integer.MAX_VALUE, 0, query))
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private String buildVoucherLinesQuery(List<Voucher> vouchers) {
    List<String> voucherIds = vouchers.stream()
      .map(Voucher::getId)
      .collect(Collectors.toList());
    return convertIdsToCqlQuery(voucherIds, "voucherId", true);
  }
}
