package org.folio.services.voucher;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.converters.AddressConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.Address;
import org.folio.rest.acq.model.FundDistribution;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.VoucherLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherExport;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.BatchGroupService;
import org.folio.services.InvoiceLinesRetrieveService;
import org.folio.services.InvoiceRetrieveService;
import org.folio.services.VendorRetrieveService;
import org.folio.services.VoucherLineService;

public class BatchVoucherGenerateService {
  private static final Logger logger = LogManager.getLogger();

  private final VoucherService voucherService;

  private final InvoiceRetrieveService invoiceRetrieveService;

  private final InvoiceLinesRetrieveService invoiceLinesRetrieveService;

  private final VendorRetrieveService vendorRetrieveService;
  private final VoucherLineService voucherLineService;

  private final AddressConverter addressConverter;

  private final BatchGroupService batchGroupService;

  public BatchVoucherGenerateService(VoucherService voucherService, InvoiceRetrieveService invoiceRetrieveService,
      InvoiceLinesRetrieveService invoiceLinesRetrieveService, VoucherLineService voucherLineService,
      VendorRetrieveService vendorRetrieveService, AddressConverter addressConverter, BatchGroupService batchGroupService) {
    this.voucherService = voucherService;
    this.invoiceRetrieveService = invoiceRetrieveService;
    this.invoiceLinesRetrieveService = invoiceLinesRetrieveService;
    this.voucherLineService = voucherLineService;
    this.vendorRetrieveService = vendorRetrieveService;
    this.addressConverter = addressConverter;
    this.batchGroupService = batchGroupService;

  }


  public Future<BatchVoucher> buildBatchVoucherObject(BatchVoucherExport batchVoucherExport, RequestContext requestContext) {
    String voucherCQL = buildBatchVoucherQuery(batchVoucherExport);
    return voucherService.getVouchers(voucherCQL, 0, Integer.MAX_VALUE, requestContext)
      .compose(vouchers -> {
        if (vouchers.getVouchers().isEmpty()) {
          var param = new Parameter().withKey("voucherCQL").withValue(voucherCQL);
          var error = new Error().withMessage("Vouchers for batch voucher export were not found").withParameters(List.of(param));
          logger.error("buildBatchVoucherObject:: Vouchers for batch voucher export were not found. '{}'", JsonObject.mapFrom(error).encodePrettily());
          throw new HttpException(404, error);
        }
        Future<Map<String, List<VoucherLine>>> voucherLines = voucherLineService.getVoucherLinesMap(vouchers, requestContext)
          .onFailure(t -> logger.error("buildBatchVoucherObject:: Error retrieving voucher lines", t));
        Future<Map<String, Invoice>> invoices = invoiceRetrieveService.getInvoiceMap(vouchers, requestContext)
          .onFailure(t -> logger.error("buildBatchVoucherObject:: Error retrieving invoices", t));
        Future<Map<String, List<InvoiceLine>>> invoiceLines = invoiceLinesRetrieveService.getInvoiceLineMap(vouchers, requestContext)
          .onFailure(t -> logger.error("buildBatchVoucherObject:: Error retrieving invoice lines", t));
        return CompositeFuture.join(voucherLines, invoices, invoiceLines)
          .compose(v -> buildBatchVoucher(batchVoucherExport, vouchers, voucherLines.result(), invoices.result(), invoiceLines.result(), requestContext));
      });
  }

  private Future<BatchVoucher> buildBatchVoucher(BatchVoucherExport batchVoucherExport,
      VoucherCollection voucherCollection, Map<String, List<VoucherLine>> voucherLinesMap, Map<String, Invoice> invoiceMap, Map<String, List<InvoiceLine>> invoiceLinesMap, RequestContext requestContext) {
    List<Invoice> invoices = new ArrayList<>(invoiceMap.values());
    var vendorsMapFuture = vendorRetrieveService.getVendorsMap(invoices, requestContext);
    return vendorsMapFuture
      .compose(vendorsMap -> batchGroupService.getBatchGroup(batchVoucherExport.getBatchGroupId(), requestContext))
      .map(batchGroup -> {
        BatchVoucher batchVoucher = new BatchVoucher();
        batchVoucher.setStart(batchVoucherExport.getStart());
        batchVoucher.setEnd(batchVoucherExport.getStart());
        List<BatchedVoucher> batchedVouchers = voucherCollection.getVouchers()
          .stream()
          .map(voucher -> buildBatchedVoucher(voucher, voucherLinesMap, invoiceMap, invoiceLinesMap, vendorsMapFuture.result()))
          .collect(toList());
        batchVoucher.setTotalRecords(batchedVouchers.size());
        batchVoucher.withBatchedVouchers(batchedVouchers);
        batchVoucher.setCreated(new Date());
        batchVoucher.setBatchGroup(batchGroup.getName());
        return batchVoucher;
      });
  }

  private BatchedVoucher buildBatchedVoucher(Voucher voucher, Map<String, List<VoucherLine>> mapVoucherLines,
      Map<String, Invoice> mapInvoices, Map<String, List<InvoiceLine>> invoiceLinesMap, Map<String, Organization> vendorsMap) {
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
    batchedVoucher.setInvoiceDate(invoice.getInvoiceDate());
    batchedVoucher.setInvoiceTerms(invoice.getPaymentTerms());
    batchedVoucher.setInvoiceNote(invoice.getNote());
    Organization organization = vendorsMap.get(invoice.getVendorId());
    batchedVoucher.setVendorName(organization.getName());
    List<Adjustment> adjustments = new ArrayList<>();
    List<InvoiceLine> invoiceLines = invoiceLinesMap.get(invoice.getId());
    List<String> invoiceAdjustmentIds = invoice.getAdjustments().stream()
      .map(Adjustment::getId)
      .collect(Collectors.toList());
    if (invoiceLines != null && !invoiceLines.isEmpty()) {
      for (InvoiceLine invoiceLine : invoiceLines) {
        adjustments.addAll(getInvoiceLineAdjustments(invoiceLine, invoiceAdjustmentIds, voucher.getExchangeRate()));
      }
    }
    List<Adjustment> invoiceAdjustmentToExport = invoice.getAdjustments().stream()
      .filter(Adjustment::getExportToAccounting)
      .map(adjustment -> calculateTotalAmount(adjustment, invoice.getSubTotal(), voucher.getExchangeRate()))
      .toList();

    adjustments.addAll(invoiceAdjustmentToExport);
    batchedVoucher.setAdjustments(adjustments);
    List<Address> addresses = organization.getAddresses();
    if (addresses != null && !addresses.isEmpty()) {
      Address primaryAddress = addresses.stream()
        .filter(a -> a.getIsPrimary() != null && a.getIsPrimary())
        .findFirst()
        .orElse(addresses.get(0));
      batchedVoucher.setVendorAddress(addressConverter.convert(primaryAddress));
    }
    if (Objects.nonNull(voucher.getDisbursementNumber())) {
      batchedVoucher.setDisbursementNumber(voucher.getDisbursementNumber());
      batchedVoucher.setDisbursementDate(voucher.getDisbursementDate());
    }
    batchedVoucher.setDisbursementAmount(voucher.getDisbursementAmount());
    batchedVoucher.withBatchedVoucherLines(buildBatchedVoucherLines(voucher.getId(), mapVoucherLines));
    return batchedVoucher;
  }

  private List<Adjustment> getInvoiceLineAdjustments(InvoiceLine invoiceLine, List<String> adjustmentIds, Double exchangeRate) {
    return invoiceLine.getAdjustments().stream()
      .filter(adjustment -> isOnlyUniqueAndExportingAdjustments(adjustment, adjustmentIds))
      .map(adjustment -> calculateTotalAmount(adjustment, invoiceLine.getSubTotal(), exchangeRate))
      .collect(Collectors.toList());
  }

  private Adjustment calculateTotalAmount(Adjustment adjustment, Double subTotal, Double exchangeRate) {
    double totalAmount;
    if (adjustment.getType().equals(Adjustment.Type.AMOUNT)) {
      totalAmount = adjustment.getValue() * exchangeRate;
    } else {
      totalAmount = (subTotal / 100 * adjustment.getValue()) * exchangeRate;
    }
    adjustment.setTotalAmount(totalAmount);

    return adjustment;
  }

  private boolean isOnlyUniqueAndExportingAdjustments(Adjustment adjustment, List<String> adjustmentIds) {
    return adjustment.getId() == null && adjustment.getExportToAccounting() && !adjustmentIds.contains(adjustment.getAdjustmentId());
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
}
