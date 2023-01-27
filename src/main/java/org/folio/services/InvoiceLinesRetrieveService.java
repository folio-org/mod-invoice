package org.folio.services;

import static java.util.stream.Collectors.groupingBy;
import static one.util.streamex.StreamEx.ofSubLists;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;
import static org.folio.rest.impl.InvoiceLineHelper.GET_INVOICE_LINES_BY_QUERY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.invoice.InvoiceLineService;

import io.vertx.core.Future;

public class InvoiceLinesRetrieveService {
  private final InvoiceLineService invoiceLineService;

  public InvoiceLinesRetrieveService(InvoiceLineService invoiceLineService) {
    this.invoiceLineService = invoiceLineService;
  }

  public Future<Map<String, List<InvoiceLine>>> getInvoiceLineMap(VoucherCollection voucherCollection, RequestContext requestContext) {
    return getInvoiceLineByChunks(voucherCollection.getVouchers(), requestContext)
      .map(invoiceLineCollections ->
        invoiceLineCollections.stream()
          .map(InvoiceLineCollection::getInvoiceLines)
          .collect(Collectors.toList()).stream()
          .flatMap(List::stream)
          .collect(Collectors.toList()))
      .map(invoiceLines -> invoiceLines.stream().collect(groupingBy(InvoiceLine::getInvoiceId)));
  }

  public Future<List<InvoiceLineCollection>> getInvoiceLineByChunks(List<Voucher> vouchers, RequestContext requestContext) {
    List<String> invoiceIds = vouchers.stream().map(Voucher::getInvoiceId).collect(Collectors.toList());
    return collectResultsOnSuccess(
      ofSubLists(new ArrayList<>(invoiceIds), MAX_IDS_FOR_GET_RQ)
        .map(ids -> getInvoiceLineChunkByInvoiceIds(ids, requestContext))
        .toList());
  }

  private Future<InvoiceLineCollection> getInvoiceLineChunkByInvoiceIds(List<String> invoiceIds, RequestContext requestContext) {
    String query = "&query=" + convertIdsToCqlQuery(invoiceIds, "invoiceId", true);
    String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, Integer.MAX_VALUE, 0, query);

    return invoiceLineService.getInvoiceLines(endpoint, requestContext);
  }
}
