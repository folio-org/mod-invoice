package org.folio.helpers.converters;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchVoucherType;
import org.springframework.core.convert.converter.Converter;

public class BatchVoucherConverter implements Converter<BatchVoucher, BatchVoucherType> {
  @Override
  public BatchVoucherType convert(BatchVoucher batchVoucher) {
    BatchVoucherType xmlBatchVoucherType = new BatchVoucherType();
    return xmlBatchVoucherType;
  }
}
