package org.folio.services.vouchers.batch;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.exceptions.BatchVoucherGenerationException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.FundDistribution;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.VoucherLine;
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
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class BatchVoucherGenerateService {
  private static final DateTimeFormatter fromFormatter = //new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSz", Locale.ENGLISH);
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withLocale( Locale.UK ).withZone( ZoneId.of("UTC"));

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
        CompletableFuture<Map<String, List<VoucherLine>>> voucherLines = getVoucherCollectionLinesMap(vouchers);
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
    Set<String> vendorIds = getVendorIds(invoiceMap);
    return vendorHelper.getVendors(vendorIds)
      .thenCombine(batchGroupHelper.getBatchGroup(batchVoucherExport.getBatchGroupId()), (vendors, batchGroup) -> {
        Map<String, Organization> orgMap = vendors.stream().collect(toMap(Organization::getId, Function.identity()));
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

  private Set<String> getVendorIds(Map<String, Invoice> invoiceCollection) {
    return invoiceCollection.values()
      .stream()
      .map(Invoice::getVendorId)
      .collect(Collectors.toSet());
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
    String query = buildInvoiceListQuery(voucherCollection);
    return invoiceHelper.getInvoices(Integer.MAX_VALUE, 0, query)
      .thenApply(invoiceCollection -> invoiceCollection.getInvoices().stream().collect(toMap(Invoice::getId, Function.identity())));
  }

  private CompletableFuture<Map<String, List<VoucherLine>>> getVoucherCollectionLinesMap(VoucherCollection voucherCollection) {
    String query = buildVoucherLinesQuery(voucherCollection);
    return voucherLineHelper.getVoucherLines(Integer.MAX_VALUE, 0, query)
      .thenApply(voucherLineCollection -> voucherLineCollection.getVoucherLines()
        .stream()
        .collect(groupingBy(VoucherLine::getVoucherId)));
  }

  private String buildBatchVoucherQuery(BatchVoucherExport batchVoucherExport) {
    JsonObject voucherJSON = JsonObject.mapFrom(batchVoucherExport);
    String voucherStart = voucherJSON.getString("start");
    String voucherEnd = voucherJSON.getString("end");
    return "batchGroupId==" + batchVoucherExport.getBatchGroupId() + " and voucherDate>=" + voucherStart
        + " and voucherDate<=" + voucherEnd;
  }

  private String buildVoucherLinesQuery(VoucherCollection voucherCollection) {
    List<String> voucherIds = voucherCollection.getVouchers()
      .stream()
      .map(Voucher::getId)
      .collect(Collectors.toList());
    return HelperUtils.convertIdsToCqlQuery(voucherIds, "voucherId", true);
  }

  private String buildInvoiceListQuery(VoucherCollection voucherCollection) {
    List<String> invoiceIds = voucherCollection.getVouchers()
      .stream()
      .map(Voucher::getInvoiceId)
      .collect(Collectors.toList());
    return HelperUtils.convertIdsToCqlQuery(invoiceIds);
  }

  private void closeHttpConnections() {
    voucherHelper.closeHttpClient();
    voucherLineHelper.closeHttpClient();
    invoiceHelper.closeHttpClient();
    voucherHelper.closeHttpClient();
    batchGroupHelper.closeHttpClient();
  }
}
