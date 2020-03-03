package org.folio.converters;

import org.folio.jaxb.JAXBUtil;
import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class BatchVoucherModelConverter implements Converter<BatchVoucher, BatchVoucherType> {

  private final BatchedVoucherModelConverter batchedVoucherModelConverter;

  private BatchVoucherModelConverter() {
    this.batchedVoucherModelConverter = new BatchedVoucherModelConverter(new BatchedVoucherLineModelConverter());
  }

  public static class SingletonHolder {
    public static final BatchVoucherModelConverter HOLDER_INSTANCE = new BatchVoucherModelConverter();
  }

  public static BatchVoucherModelConverter getInstance() {
    return BatchVoucherModelConverter.SingletonHolder.HOLDER_INSTANCE;
  }


  @Override
  public BatchVoucherType convert(BatchVoucher batchVoucher) {
    BatchVoucherType xmlBatchVoucherType = new BatchVoucherType();
    xmlBatchVoucherType.setId(batchVoucher.getId());
    xmlBatchVoucherType.setStart(JAXBUtil.convertDateTime(batchVoucher.getStart()));
    xmlBatchVoucherType.setEnd(JAXBUtil.convertDateTime(batchVoucher.getEnd()));
    xmlBatchVoucherType.setCreated(JAXBUtil.convertDateTime(batchVoucher.getCreated()));
    xmlBatchVoucherType.setBatchGroup(batchVoucher.getBatchGroup());
    xmlBatchVoucherType.setTotalRecords(BigInteger.valueOf(batchVoucher.getTotalRecords()));

    BatchVoucherType.BatchedVouchers batchedVouchers = convertBatchedVouchers(batchVoucher);
    xmlBatchVoucherType.withBatchedVouchers(batchedVouchers);
    return xmlBatchVoucherType;
  }

  @NotNull
  private BatchVoucherType.BatchedVouchers convertBatchedVouchers(BatchVoucher batchVoucher) {
    BatchVoucherType.BatchedVouchers batchedVouchers =  new BatchVoucherType.BatchedVouchers();
    List<BatchedVoucherType> batchedVouchersList =
          batchVoucher.getBatchedVouchers().stream()
                                           .map(batchedVoucherModelConverter::convert)
                                           .collect(Collectors.toList());
    batchedVouchers.withBatchedVoucher(batchedVouchersList);
    return batchedVouchers;
  }
}
