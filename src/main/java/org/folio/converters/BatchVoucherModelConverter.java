package org.folio.converters;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.jaxb.JAXBUtil;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.springframework.core.convert.converter.Converter;

import javax.validation.constraints.NotNull;

public class BatchVoucherModelConverter implements Converter<BatchVoucher, BatchVoucherType> {

  private final BatchedVoucherModelConverter batchedVoucherModelConverter;

  private BatchVoucherModelConverter() {
    this.batchedVoucherModelConverter = new BatchedVoucherModelConverter(new BatchedVoucherLineModelConverter());
  }

  private static class SingletonHolder {
    public static final BatchVoucherModelConverter HOLDER_INSTANCE = new BatchVoucherModelConverter();
  }

  public static BatchVoucherModelConverter getInstance() {
    return BatchVoucherModelConverter.SingletonHolder.HOLDER_INSTANCE;
  }

  @Override
  public BatchVoucherType convert(BatchVoucher batchVoucher) {
    BatchVoucherType xmlBatchVoucherType = new BatchVoucherType();
    xmlBatchVoucherType.setId(batchVoucher.getId());
    if (batchVoucher.getStart() != null) {
      xmlBatchVoucherType.setStart(JAXBUtil.convertOldJavaDate(batchVoucher.getStart()));
    }
    if (batchVoucher.getEnd() != null) {
      xmlBatchVoucherType.setEnd(JAXBUtil.convertOldJavaDate(batchVoucher.getEnd()));
    }
    if (batchVoucher.getCreated() != null) {
      xmlBatchVoucherType.setCreated(JAXBUtil.convertOldJavaDate(batchVoucher.getCreated()));
    }
    xmlBatchVoucherType.setBatchGroup(batchVoucher.getBatchGroup());
    xmlBatchVoucherType.setTotalRecords(BigInteger.valueOf(batchVoucher.getTotalRecords()));

    BatchVoucherType.BatchedVouchers batchedVouchers = convertBatchedVouchers(batchVoucher);
    xmlBatchVoucherType.withBatchedVouchers(batchedVouchers);
    return xmlBatchVoucherType;
  }

  @NotNull
  private BatchVoucherType.BatchedVouchers convertBatchedVouchers(BatchVoucher batchVoucher) {
    BatchVoucherType.BatchedVouchers batchedVouchers = new BatchVoucherType.BatchedVouchers();
    List<BatchedVoucherType> batchedVouchersList = batchVoucher.getBatchedVouchers()
      .stream()
      .map(batchedVoucherModelConverter::convert)
      .collect(Collectors.toList());
    batchedVouchers.withBatchedVoucher(batchedVouchersList);
    return batchedVouchers;
  }
}
