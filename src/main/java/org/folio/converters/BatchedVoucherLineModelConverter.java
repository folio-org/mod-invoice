package org.folio.converters;

import java.math.BigDecimal;

import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherLineType;
import org.springframework.core.convert.converter.Converter;

public class BatchedVoucherLineModelConverter implements Converter<BatchedVoucherLine, BatchedVoucherLineType> {
  @Override
  public BatchedVoucherLineType convert(BatchedVoucherLine batchedVoucherLine) {
    BatchedVoucherLineType batchedVoucherLineType = new BatchedVoucherLineType();
    batchedVoucherLineType.setAmount(BigDecimal.valueOf(batchedVoucherLine.getAmount()));
    batchedVoucherLineType.setExternalAccountNumber(batchedVoucherLine.getExternalAccountNumber());

    BatchedVoucherLineType.FundCodes fundCodes = new BatchedVoucherLineType.FundCodes();
    fundCodes.withFundCode(batchedVoucherLine.getFundCodes());
    batchedVoucherLineType.withFundCodes(fundCodes);
    return batchedVoucherLineType;
  }
}
