package org.folio.services.vouchers.batch;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.folio.exceptions.BatchVoucherGenerationException;
import org.folio.rest.acq.model.FundDistribution;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.OrganizationCollection;
import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.impl.BatchGroupHelper;
import org.folio.rest.impl.InvoiceHelper;
import org.folio.rest.impl.VendorHelper;
import org.folio.rest.impl.VoucherHelper;
import org.folio.rest.impl.VoucherLineHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;

public class BatchVoucherGenerateService {
  static final int MAX_IDS_FOR_GET_RQ = 15;

  private final VoucherHelper voucherHelper;
  private final VoucherLineHelper voucherLineHelper;
  private final InvoiceHelper invoiceHelper;
  private final VendorHelper vendorHelper;
  private final BatchGroupHelper batchGroupHelper;

  public BatchVoucherGenerateService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    voucherHelper = new VoucherHelper(okapiHeaders, ctx, lang);
    voucherLineHelper = new VoucherLineHelper(okapiHeaders, ctx, lang);
    invoiceHelper = new InvoiceHelper(okapiHeaders, ctx, lang);
    vendorHelper = new VendorHelper(okapiHeaders, ctx, lang);
    batchGroupHelper = new BatchGroupHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<BatchVoucher> generateBatchVoucher(BatchVoucherExport batchVoucherExport) {
    CompletableFuture<BatchVoucher> future = new CompletableFuture<>();
    String voucherCQL = buildBatchVoucherQuery(batchVoucherExport);
    voucherHelper.getVouchers(Integer.MAX_VALUE, 0, voucherCQL)
      .thenApply(voucherCollection -> {
        if (voucherCollection.getVouchers().isEmpty()) {
          throw new BatchVoucherGenerationException("Vouchers for batch voucher export were not found");
        }
        return voucherCollection;
      })
      .thenCompose(vouchers -> {
        CompletableFuture<Map<String, List<VoucherLine>>> voucherLines = getVoucherLinesMap(vouchers);
        CompletableFuture<Map<String, Invoice>> invoices = getInvoiceMap(vouchers);
        return VertxCompletableFuture.allOf(voucherLines, invoices)
          .thenCompose(v -> buildBatchVoucher(batchVoucherExport, vouchers, voucherLines.join(), invoices.join()))
          .thenAccept(batchVoucher -> {
            future.complete(batchVoucher);
            closeHttpConnections();
            });
      })
      .exceptionally(t -> {
        future.completeExceptionally(t);
        closeHttpConnections();
        return null;
      });
     return future;
  }

  private CompletableFuture<BatchVoucher> buildBatchVoucher(BatchVoucherExport batchVoucherExport,
      VoucherCollection voucherCollection, Map<String, List<VoucherLine>> voucherLinesMap, Map<String, Invoice> invoiceMap) {
    List<Invoice> invoices = new ArrayList<>(invoiceMap.values());
    return getOrganizationMap(invoices, MAX_IDS_FOR_GET_RQ)
      .thenCombine(batchGroupHelper.getBatchGroup(batchVoucherExport.getBatchGroupId()), (orgMap, batchGroup) -> {
        BatchVoucher batchVoucher = new BatchVoucher();
        batchVoucher.setStart(batchVoucherExport.getStart());
        batchVoucher.setEnd(batchVoucherExport.getStart());
        List<BatchedVoucher> batchedVouchers = voucherCollection.getVouchers()
          .stream()
          .map(voucher -> buildBatchedVoucher(voucher, voucherLinesMap, invoiceMap, orgMap))
          .collect(toList());
        batchVoucher.setTotalRecords(batchedVouchers.size());
        batchVoucher.withBatchedVouchers(batchedVouchers);
        batchVoucher.setCreated(new Date());
        batchVoucher.setBatchGroup(batchGroup.getName());
        return batchVoucher;
      });
  }

  private BatchedVoucher buildBatchedVoucher(Voucher voucher, Map<String, List<VoucherLine>> mapVoucherLines,
      Map<String, Invoice> mapInvoices, Map<String, Organization> orgMap) {
    BatchedVoucher batchedVoucher = new BatchedVoucher();
    batchedVoucher.setVoucherNumber(voucher.getVoucherNumber());
    batchedVoucher.setVendorInvoiceNo(voucher.getInvoiceId());
    batchedVoucher.setAccountingCode(voucher.getAccountingCode());
    batchedVoucher.setVoucherDate(voucher.getVoucherDate());
    batchedVoucher.setType(BatchedVoucher.Type.fromValue(voucher.getType().value()));
    batchedVoucher.setAmount(voucher.getAmount());
    batchedVoucher.setSystemCurrency(voucher.getSystemCurrency());
    batchedVoucher.setInvoiceCurrency(voucher.getInvoiceCurrency());
    batchedVoucher.setExchangeRate(voucher.getExchangeRate());
    batchedVoucher.setStatus(BatchedVoucher.Status.fromValue(voucher.getStatus().value()));
    Invoice invoice = mapInvoices.get(voucher.getInvoiceId());
    Organization organization = orgMap.get(invoice.getVendorId());
    batchedVoucher.setInvoiceNote(invoice.getNote());
    batchedVoucher.setVendorName(organization.getName());
    if (Objects.nonNull(voucher.getDisbursementNumber())) {
      batchedVoucher.setDisbursementNumber(voucher.getDisbursementNumber());
      batchedVoucher.setDisbursementDate(voucher.getDisbursementDate());
    }
    batchedVoucher.setDisbursementAmount(voucher.getDisbursementAmount());
    batchedVoucher.withBatchedVoucherLines(buildBatchedVoucherLines(voucher.getId(), mapVoucherLines));
    return batchedVoucher;
  }

  private List<BatchedVoucherLine> buildBatchedVoucherLines(String voucherId, Map<String, List<VoucherLine>> mapVoucherLines) {
    return mapVoucherLines.get(voucherId)
      .stream()
      .map(this::buildBatchedVoucherLine)
      .collect(toList());
  }

  private BatchedVoucherLine buildBatchedVoucherLine(VoucherLine voucherLine) {
    BatchedVoucherLine batchedVoucherLine = new BatchedVoucherLine();
    batchedVoucherLine.setAmount(voucherLine.getAmount());
    batchedVoucherLine.setExternalAccountNumber(voucherLine.getExternalAccountNumber());
    List<String> fundCodes = voucherLine.getFundDistributions()
      .stream()
      .map(FundDistribution::getCode)
      .collect(toList());
    batchedVoucherLine.withFundCodes(fundCodes);
    return batchedVoucherLine;
  }

  private CompletableFuture<Map<String, Invoice>> getInvoiceMap(VoucherCollection voucherCollection) {
    return getInvoicesByChunks(voucherCollection.getVouchers(), MAX_IDS_FOR_GET_RQ)
      .thenApply(invoiceCollections ->
              invoiceCollections.stream()
                                .map(InvoiceCollection::getInvoices)
                                .collect(toList()).stream()
                                .flatMap(List::stream)
                                .collect(Collectors.toList()))
      .thenApply(invoices -> invoices.stream().collect(toMap(Invoice::getId, Function.identity())));
  }

  private CompletableFuture<Map<String, Organization>> getOrganizationMap(List<Invoice> invoices, int maxRecordsPerGet) {
    return getVendorsByChunks(invoices, maxRecordsPerGet)
      .thenApply(organizationCollections ->
        organizationCollections.stream()
                               .map(OrganizationCollection::getOrganizations)
                               .collect(toList()).stream()
                               .flatMap(List::stream)
                               .collect(Collectors.toList()))
      .thenApply(organizations -> organizations.stream().collect(toMap(Organization::getId, Function.identity())));
  }

  private CompletableFuture<Map<String, List<VoucherLine>>> getVoucherLinesMap(VoucherCollection voucherCollection) {
    return getVoucherLinesByChunks(voucherCollection.getVouchers(), MAX_IDS_FOR_GET_RQ)
      .thenApply(voucherLineCollections ->
        voucherLineCollections.stream()
                              .map(VoucherLineCollection::getVoucherLines)
                              .collect(toList()).stream()
                              .flatMap(List::stream)
                              .collect(Collectors.toList()))
      .thenApply(voucherLines -> voucherLines.stream().collect(groupingBy(VoucherLine::getVoucherId)));
  }

  private CompletableFuture<List<InvoiceCollection>> getInvoicesByChunks(List<Voucher> vouchers, int maxRecordsPerGet) {
    List<CompletableFuture<InvoiceCollection>> invoiceFutureList = buildIdChunks(vouchers, maxRecordsPerGet).values()
      .stream()
      .map(this::buildInvoiceListQuery)
      .map(query -> invoiceHelper.getInvoices(maxRecordsPerGet, 0, query))
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private CompletableFuture<List<VoucherLineCollection>> getVoucherLinesByChunks(List<Voucher> vouchers, int maxRecordsPerGet) {
    List<CompletableFuture<VoucherLineCollection>> invoiceFutureList = buildIdChunks(vouchers, maxRecordsPerGet).values()
      .stream()
      .map(this::buildVoucherLinesQuery)
      .map(query -> voucherLineHelper.getVoucherLines(maxRecordsPerGet, 0, query))
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private CompletableFuture<List<OrganizationCollection>> getVendorsByChunks(List<Invoice> invoices, int maxRecordsPerGet) {
    List<CompletableFuture<OrganizationCollection>> invoiceFutureList = buildIdChunks(invoices, maxRecordsPerGet).values()
      .stream()
      .map(this::getVendorIds)
      .map(vendorHelper::getVendors)
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private String buildBatchVoucherQuery(BatchVoucherExport batchVoucherExport) {
    JsonObject voucherJSON = JsonObject.mapFrom(batchVoucherExport);
    String voucherStart = voucherJSON.getString("start");
    String voucherEnd = voucherJSON.getString("end");
    return "batchGroupId==" + batchVoucherExport.getBatchGroupId() + " and voucherDate>=" + voucherStart
      + " and voucherDate<=" + voucherEnd;
  }

  private String buildVoucherLinesQuery(List<Voucher> vouchers) {
    List<String> voucherIds = vouchers.stream()
      .map(Voucher::getId)
      .collect(Collectors.toList());
    return convertIdsToCqlQuery(voucherIds, "voucherId", true);
  }

  private String buildInvoiceListQuery(List<Voucher> vouchers) {
    List<String> invoiceIds = vouchers.stream()
      .map(Voucher::getInvoiceId)
      .collect(Collectors.toList());
    return convertIdsToCqlQuery(invoiceIds);
  }

  private Set<String> getVendorIds(List<Invoice> invoices) {
    return invoices.stream()
      .map(Invoice::getVendorId)
      .collect(Collectors.toSet());
  }

  private <T> Map<Integer, List<T>> buildIdChunks(List<T> source, int maxListRecords) {
    int size = source.size();
    if (size <= 0)
      return Collections.emptyMap();
    int fullChunks = (size - 1) / maxListRecords;
    HashMap<Integer, List<T>> idChunkMap = new HashMap<>();
    IntStream.range(0, fullChunks + 1)
                    .forEach(n -> {
                      List<T> subList = source.subList(n * maxListRecords, n == fullChunks ? size : (n + 1) * maxListRecords);
                      idChunkMap.put(n, subList);
                    });
     return idChunkMap;
  }

  private void closeHttpConnections() {
    voucherHelper.closeHttpClient();
    voucherLineHelper.closeHttpClient();
    invoiceHelper.closeHttpClient();
    voucherHelper.closeHttpClient();
    batchGroupHelper.closeHttpClient();
  }
}
