package org.folio.services;

import static java.util.stream.Collectors.groupingBy;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.invoice.InvoiceLineService;

public class InvoiceLinesRetrieveService {
  private final InvoiceLineService invoiceLineService;

  public InvoiceLinesRetrieveService(InvoiceLineService invoiceLineService) {
    this.invoiceLineService = invoiceLineService;
  }

  public CompletableFuture<Map<String, List<InvoiceLine>>> getInvoiceLineMap(VoucherCollection voucherCollection, RequestContext requestContext) {
    CompletableFuture<Map<String, List<InvoiceLine>>> future = new CompletableFuture<>();
    getInvoiceLineByChunks(voucherCollection.getVouchers(), requestContext)
      .thenApply(invoiceLineCollections ->
        invoiceLineCollections.stream()
          .map(InvoiceLineCollection::getInvoiceLines)
          .collect(Collectors.toList()).stream()
          .flatMap(List::stream)
          .collect(Collectors.toList()))
      .thenAccept(invoiceLines -> future.complete(invoiceLines.stream().collect(groupingBy(InvoiceLine::getInvoiceId))))
      .exceptionally(t -> {
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public CompletableFuture<List<InvoiceLineCollection>> getInvoiceLineByChunks(List<Voucher> vouchers, RequestContext requestContext) {
    List<String> invoiceIds = vouchers.stream().map(Voucher::getInvoiceId).collect(Collectors.toList());
    return collectResultsOnSuccess(
      ofSubLists(new ArrayList<>(invoiceIds), MAX_IDS_FOR_GET_RQ)
        .map(ids -> getInvoiceLineChunkByInvoiceIds(ids, requestContext))
        .toList());
  }

  private CompletableFuture<InvoiceLineCollection> getInvoiceLineChunkByInvoiceIds(List<String> invoiceIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(invoiceIds, "invoiceId", true);
    return invoiceLineService.getInvoiceLines(query, requestContext);
  }
}
