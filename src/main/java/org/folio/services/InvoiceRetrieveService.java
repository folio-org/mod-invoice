package org.folio.services;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.HelperUtils.buildIdsChunks;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.invoice.InvoiceService;

public class InvoiceRetrieveService {
  private final InvoiceService invoiceService;

  public InvoiceRetrieveService(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  public CompletableFuture<Map<String, Invoice>> getInvoiceMap(VoucherCollection voucherCollection, RequestContext requestContext) {
    CompletableFuture<Map<String, Invoice>> future = new CompletableFuture<>();
    getInvoicesByChunks(voucherCollection.getVouchers(), requestContext)
      .thenApply(invoiceCollections ->
        invoiceCollections.stream()
          .map(InvoiceCollection::getInvoices)
          .collect(toList()).stream()
          .flatMap(List::stream)
          .collect(Collectors.toList()))
      .thenAccept(invoices -> future.complete(invoices.stream().distinct().collect(toMap(Invoice::getId, Function.identity()))))
      .exceptionally(t -> {
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public CompletableFuture<List<InvoiceCollection>> getInvoicesByChunks(List<Voucher> vouchers, RequestContext requestContext) {
    List<String> invoiceIds = vouchers.stream().map(Voucher::getInvoiceId).collect(Collectors.toList());
    return collectResultsOnSuccess(
      ofSubLists(new ArrayList<>(invoiceIds), MAX_IDS_FOR_GET_RQ)
            .map(ids -> getInvoicesChunkByInvoiceIds(ids, requestContext))
            .toList());
  }

  private CompletableFuture<InvoiceCollection> getInvoicesChunkByInvoiceIds(Collection<String> invoiceIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(invoiceIds);
    return invoiceService.getInvoices(query, 0, Integer.MAX_VALUE, requestContext);
  }
}
