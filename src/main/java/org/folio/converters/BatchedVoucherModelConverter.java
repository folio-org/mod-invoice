package org.folio.converters;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.folio.jaxb.JAXBUtil;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.VendorAddress;
import org.folio.rest.jaxrs.model.jaxb.*;
import org.springframework.core.convert.converter.Converter;

public class BatchedVoucherModelConverter implements Converter<BatchedVoucher, BatchedVoucherType> {
  private final BatchedVoucherLineModelConverter batchedVoucherLineModelConverter;

  public BatchedVoucherModelConverter(BatchedVoucherLineModelConverter batchedVoucherLineModelConverter) {
    this.batchedVoucherLineModelConverter = batchedVoucherLineModelConverter;
  }

  @Override
  public BatchedVoucherType convert(BatchedVoucher batchedVoucher) {
    BatchedVoucherType batchedVoucherType = new BatchedVoucherType();
    batchedVoucherType.setVoucherNumber(batchedVoucher.getVoucherNumber());
    batchedVoucherType.setAccountingCode(batchedVoucher.getAccountingCode());
    Optional.ofNullable(batchedVoucher.getAccountNo()).ifPresent(batchedVoucherType::setAccountNo);
    batchedVoucherType.setAmount(BigDecimal.valueOf(batchedVoucher.getAmount()));

    batchedVoucherType.setDisbursementNumber(batchedVoucher.getDisbursementNumber());
    if (batchedVoucher.getDisbursementDate() != null) {
      batchedVoucherType.setDisbursementDate(JAXBUtil.convertOldJavaDate(batchedVoucher.getDisbursementDate()));
    }
    batchedVoucherType.setDisbursementAmount(BigDecimal.valueOf(batchedVoucher.getAmount()));
    Optional.ofNullable(batchedVoucher.getEnclosureNeeded())
      .ifPresentOrElse(batchedVoucherType::setEnclosureNeeded, () -> batchedVoucherType.setEnclosureNeeded(false));
    batchedVoucherType.setExchangeRate(BigDecimal.valueOf(batchedVoucher.getExchangeRate()));
    batchedVoucherType.setInvoiceCurrency(batchedVoucher.getInvoiceCurrency());
    batchedVoucherType.setFolioInvoiceNo(batchedVoucher.getFolioInvoiceNo());
    batchedVoucherType.setInvoiceCurrency(batchedVoucher.getInvoiceCurrency());
    batchedVoucherType.setSystemCurrency(batchedVoucher.getSystemCurrency());
    batchedVoucherType.setType(PaymentAccountType.fromValue(batchedVoucher.getType().toString()));

    batchedVoucherType.setVendorInvoiceNo(batchedVoucher.getVendorInvoiceNo());
    batchedVoucherType.setVendorName(batchedVoucher.getVendorName());
    batchedVoucherType.setVendorAddress(convertVendorAddress(batchedVoucher.getVendorAddress()));
    if (batchedVoucher.getVoucherDate() != null) {
      batchedVoucherType.setVoucherDate(JAXBUtil.convertOldJavaDate(batchedVoucher.getVoucherDate()));
    }
    if (batchedVoucher.getInvoiceDate() != null)
    {
      batchedVoucherType.setInvoiceDate(JAXBUtil.convertOldJavaDate(batchedVoucher.getInvoiceDate()));
    }
    batchedVoucherType.setInvoiceTerms(batchedVoucher.getInvoiceTerms());
    batchedVoucherType.setInvoiceNote(batchedVoucher.getInvoiceNote());
    batchedVoucherType.setStatus(batchedVoucher.getStatus().toString());

    BatchedVoucherType.BatchedVoucherLines batchedVoucherLines = convertBatchedVoucherLines(batchedVoucher);
    batchedVoucherType.withBatchedVoucherLines(batchedVoucherLines);

    BatchedVoucherType.Adjustments adjustments = convertAdjustmentsLines(batchedVoucher);
    batchedVoucherType.withAdjustments(adjustments);
    return batchedVoucherType;
  }

  private BatchedVoucherType.Adjustments convertAdjustmentsLines(BatchedVoucher batchedVoucher) {
    BatchedVoucherType.Adjustments adjustments = new BatchedVoucherType.Adjustments();
    List<AdjustmentLineType> adjustmentsList = new ArrayList<>();
    for (Adjustment adjustment : batchedVoucher.getAdjustments()) {
      AdjustmentLineType normalizedAdjustment = new AdjustmentLineType();
      normalizedAdjustment.setDescription(adjustment.getDescription());
      normalizedAdjustment.setRelationToTotal(adjustment.getRelationToTotal().value());
      normalizedAdjustment.setProrate(adjustment.getProrate().value());
      normalizedAdjustment.setType(adjustment.getType().value());
      normalizedAdjustment.setValue(adjustment.getValue());
      adjustmentsList.add(normalizedAdjustment);
    }
    adjustments.withAdjustment(adjustmentsList);
    return adjustments;
  }

  private VendorAddressType convertVendorAddress(VendorAddress address) {
    if (address == null)
      return null;
    return new VendorAddressType()
      .withAddressLine1(address.getAddressLine1())
      .withAddressLine2(address.getAddressLine2())
      .withCity(address.getCity())
      .withStateRegion(address.getStateRegion())
      .withZipCode(address.getZipCode())
      .withCountry(address.getCountry());
  }

  private BatchedVoucherType.BatchedVoucherLines convertBatchedVoucherLines(BatchedVoucher batchedVoucher) {
    BatchedVoucherType.BatchedVoucherLines batchedVoucherLines = new BatchedVoucherType.BatchedVoucherLines();
    List<BatchedVoucherLineType> batchedVouchersList = batchedVoucher.getBatchedVoucherLines()
      .stream()
      .map(batchedVoucherLineModelConverter::convert)
      .collect(Collectors.toList());
    batchedVoucherLines.withBatchedVoucherLine(batchedVouchersList);
    return batchedVoucherLines;
  }
}
