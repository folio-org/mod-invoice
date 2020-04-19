package org.folio.converters;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

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
    List<String> normalizedFundCodes = batchedVoucherLine.getFundCodes()
      .stream()
      .map(this::normalizeFundCode)
      .collect(Collectors.toList());

    fundCodes.withFundCode(normalizedFundCodes);
    batchedVoucherLineType.setFundCodes(fundCodes);
    return batchedVoucherLineType;
  }

  private String normalizeFundCode(String fundCode){
    return fundCode != null ? fundCode : "Empty";
  }
}
