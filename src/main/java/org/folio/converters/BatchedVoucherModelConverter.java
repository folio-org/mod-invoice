package org.folio.converters;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.jaxb.JAXBUtil;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherLineType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.folio.rest.jaxrs.model.jaxb.PaymentAccountType;
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
    batchedVoucherType.setAmount(BigDecimal.valueOf(batchedVoucher.getAmount()));

    batchedVoucherType.setDisbursementNumber(batchedVoucher.getDisbursementNumber());
    batchedVoucherType.setDisbursementDate(JAXBUtil.convertOldJavaDate(batchedVoucher.getDisbursementDate()));
    batchedVoucherType.setDisbursementAmount(BigDecimal.valueOf(batchedVoucher.getAmount()));

    batchedVoucherType.setExchangeRate(BigDecimal.valueOf(batchedVoucher.getExchangeRate()));
    batchedVoucherType.setInvoiceCurrency(batchedVoucher.getDisbursementNumber());
    batchedVoucherType.setSystemCurrency(batchedVoucher.getSystemCurrency());
    batchedVoucherType.setType(PaymentAccountType.fromValue(batchedVoucher.getType()
      .toString()));

    batchedVoucherType.setVendorInvoiceNo(batchedVoucher.getVendorInvoiceNo());
    batchedVoucherType.setVendorName(batchedVoucher.getVendorName());
    batchedVoucherType.setVoucherDate(JAXBUtil.convertOldJavaDate(batchedVoucher.getVoucherDate()));
    batchedVoucherType.setInvoiceNote(batchedVoucher.getInvoiceNote());
    batchedVoucherType.setStatus(batchedVoucher.getStatus()
      .toString());

    BatchedVoucherType.BatchedVoucherLines batchedVoucherLines = convertBatchedVoucherLines(batchedVoucher);
    batchedVoucherType.withBatchedVoucherLines(batchedVoucherLines);
    return batchedVoucherType;
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
