package org.folio.services.voucher;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import org.folio.exceptions.BatchVoucherGenerationException;
import org.folio.rest.acq.model.FundDistribution;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.impl.BatchGroupHelper;
import org.folio.rest.impl.VoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.VendorRetrieveService;
import org.folio.services.VoucherLinesRetrieveService;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class BatchVoucherGenerateService {
  private final VoucherHelper voucherHelper;
  private final BatchGroupHelper batchGroupHelper;
  @Autowired
  private InvoiceRetrieveService invoiceRetrieveService;
  private final VoucherLinesRetrieveService voucherLinesRetrieveService;
  private final VendorRetrieveService vendorRetrieveService;

  public BatchVoucherGenerateService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    vendorRetrieveService = new VendorRetrieveService(okapiHeaders, ctx, lang);
    voucherLinesRetrieveService = new VoucherLinesRetrieveService(okapiHeaders, ctx, lang);
    voucherHelper = new VoucherHelper(okapiHeaders, ctx, lang);
    batchGroupHelper = new BatchGroupHelper(okapiHeaders, ctx, lang);
  }

  public BatchVoucherGenerateService(Map<String, String> okapiHeaders, Context ctx, String lang
                          , InvoiceRetrieveService invoiceRetrieveService,  VoucherRetrieveService voucherRetrieveService,
                                      VoucherCommandService voucherCommandService) {
    vendorRetrieveService = new VendorRetrieveService(okapiHeaders, ctx, lang);
    voucherLinesRetrieveService = new VoucherLinesRetrieveService(okapiHeaders, ctx, lang);
    voucherHelper = new VoucherHelper(okapiHeaders, ctx, lang, voucherRetrieveService, voucherCommandService);
    batchGroupHelper = new BatchGroupHelper(okapiHeaders, ctx, lang);
    this. invoiceRetrieveService = invoiceRetrieveService;
  }

  public CompletableFuture<BatchVoucher> generateBatchVoucher(BatchVoucherExport batchVoucherExport, RequestContext requestContext) {
    CompletableFuture<BatchVoucher> future = new CompletableFuture<>();
    String voucherCQL = buildBatchVoucherQuery(batchVoucherExport);
    voucherHelper.getVouchers(Integer.MAX_VALUE, 0, voucherCQL)
      .thenCompose(vouchers -> {
        if (!vouchers.getVouchers().isEmpty()) {
          CompletableFuture<Map<String, List<VoucherLine>>> voucherLines = voucherLinesRetrieveService.getVoucherLinesMap(vouchers);
          CompletableFuture<Map<String, Invoice>> invoices = invoiceRetrieveService.getInvoiceMap(vouchers, requestContext);
          return allOf(voucherLines, invoices)
            .thenCompose(v -> buildBatchVoucher(batchVoucherExport, vouchers, voucherLines.join(), invoices.join()))
            .thenAccept(batchVoucher -> {
              future.complete(batchVoucher);
              closeHttpConnections();
            });
        }
       throw new BatchVoucherGenerationException("Vouchers for batch voucher export were not found");
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
    return vendorRetrieveService.getVendorsMap(invoices)
      .thenCombine(batchGroupHelper.getBatchGroup(batchVoucherExport.getBatchGroupId()), (vendorsMap, batchGroup) -> {
        BatchVoucher batchVoucher = new BatchVoucher();
        batchVoucher.setStart(batchVoucherExport.getStart());
        batchVoucher.setEnd(batchVoucherExport.getStart());
        List<BatchedVoucher> batchedVouchers = voucherCollection.getVouchers()
          .stream()
          .map(voucher -> buildBatchedVoucher(voucher, voucherLinesMap, invoiceMap, vendorsMap))
          .collect(toList());
        batchVoucher.setTotalRecords(batchedVouchers.size());
        batchVoucher.withBatchedVouchers(batchedVouchers);
        batchVoucher.setCreated(new Date());
        batchVoucher.setBatchGroup(batchGroup.getName());
        return batchVoucher;
      });
  }

  private BatchedVoucher buildBatchedVoucher(Voucher voucher, Map<String, List<VoucherLine>> mapVoucherLines,
      Map<String, Invoice> mapInvoices, Map<String, Organization> vendorsMap) {
    BatchedVoucher batchedVoucher = new BatchedVoucher();
    batchedVoucher.setVoucherNumber(voucher.getVoucherNumber());
    batchedVoucher.setAccountingCode(voucher.getAccountingCode());
    batchedVoucher.setVoucherDate(voucher.getVoucherDate());
    batchedVoucher.setType(BatchedVoucher.Type.fromValue(voucher.getType().value()));
    batchedVoucher.setAmount(voucher.getAmount());
    batchedVoucher.setSystemCurrency(voucher.getSystemCurrency());
    batchedVoucher.setInvoiceCurrency(voucher.getInvoiceCurrency());
    batchedVoucher.setExchangeRate(voucher.getExchangeRate());
    batchedVoucher.setStatus(BatchedVoucher.Status.fromValue(voucher.getStatus().value()));
    Optional.ofNullable(voucher.getEnclosureNeeded())
            .ifPresentOrElse(batchedVoucher::setEnclosureNeeded, () -> batchedVoucher.setEnclosureNeeded(false));
    Optional.ofNullable(voucher.getAccountNo()).ifPresent(batchedVoucher::setAccountNo);
    Invoice invoice = mapInvoices.get(voucher.getInvoiceId());
    batchedVoucher.setFolioInvoiceNo(invoice.getFolioInvoiceNo());
    batchedVoucher.setVendorInvoiceNo(invoice.getVendorInvoiceNo());
    Organization organization = vendorsMap.get(invoice.getVendorId());
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

  private String buildBatchVoucherQuery(BatchVoucherExport batchVoucherExport) {
    JsonObject voucherJSON = JsonObject.mapFrom(batchVoucherExport);
    String voucherStart = voucherJSON.getString("start");
    String voucherEnd = voucherJSON.getString("end");
    return "batchGroupId==" + batchVoucherExport.getBatchGroupId() + " and voucherDate>=" + voucherStart
      + " and voucherDate<=" + voucherEnd
      + " and exportToAccounting==true";
  }

  private void closeHttpConnections() {
    voucherHelper.closeHttpClient();
    batchGroupHelper.closeHttpClient();
  }
}
