package org.folio.converters;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherLineType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.folio.rest.jaxrs.model.jaxb.PaymentAccountType;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class BatchVoucherModelConverter implements Converter<BatchVoucher, BatchVoucherType> {
  private final  DateTimeFormatter fromFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  private final  DateTimeFormatter fromDateFormatter = DateTimeFormatter.ofPattern("E-MM-dd'T'HH:mm:ss.SSSZ");


  @Override
  public BatchVoucherType convert(BatchVoucher batchVoucher) {
    BatchVoucherType xmlBatchVoucherType = new BatchVoucherType();
    xmlBatchVoucherType.setId(batchVoucher.getId());
    xmlBatchVoucherType.setStart(convertDateTime(batchVoucher.getStart()));
    xmlBatchVoucherType.setEnd(convertDateTime(batchVoucher.getEnd()));
    xmlBatchVoucherType.setCreated(convertDateTime(batchVoucher.getCreated()));
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
                  .map(this::convertBatchedVoucher)
                  .collect(Collectors.toList());
    batchedVouchers.withBatchedVouchers(batchedVouchersList);
    return batchedVouchers;
  }

  private BatchedVoucherType convertBatchedVoucher(BatchedVoucher batchedVoucher){
    BatchedVoucherType batchedVoucherType = new BatchedVoucherType();
    batchedVoucherType.setVoucherNumber(batchedVoucher.getVoucherNumber());
    batchedVoucherType.setAccountingCode(batchedVoucher.getAccountingCode());
    batchedVoucherType.setAmount(BigDecimal.valueOf(batchedVoucher.getAmount()));

    batchedVoucherType.setDisbursementNumber(batchedVoucher.getDisbursementNumber());
    batchedVoucherType.setDisbursementDate(convertOldJavaDate(batchedVoucher.getDisbursementDate()));
    batchedVoucherType.setDisbursementAmount(BigDecimal.valueOf(batchedVoucher.getAmount()));

    batchedVoucherType.setExchangeRate(BigDecimal.valueOf(batchedVoucher.getExchangeRate()));
    batchedVoucherType.setInvoiceCurrency(batchedVoucher.getDisbursementNumber());
    batchedVoucherType.setSystemCurrency(batchedVoucher.getSystemCurrency());
    batchedVoucherType.setType(PaymentAccountType.fromValue(batchedVoucher.getType().toString()));

    batchedVoucherType.setVendorInvoiceNo(batchedVoucher.getVendorInvoiceNo());
    batchedVoucherType.setVendorName(batchedVoucher.getVendorName());
    batchedVoucherType.setVoucherDate(convertOldJavaDate(batchedVoucher.getVoucherDate()));

    // batchedVoucherType.setInvoiceNote
    batchedVoucherType.setStatus(batchedVoucher.getStatus().toString());
    BatchedVoucherType.BatchedVoucherLines batchedVoucherLines = convertBatchedVoucherLines(batchedVoucher);
    batchedVoucherType.withBatchedVoucherLines(batchedVoucherLines);
    return batchedVoucherType;
  }

  private BatchedVoucherType.BatchedVoucherLines convertBatchedVoucherLines(BatchedVoucher batchedVoucher){
    BatchedVoucherType.BatchedVoucherLines batchedVoucherLines = new  BatchedVoucherType.BatchedVoucherLines();
    List<BatchedVoucherLineType> batchedVouchersList =
          batchedVoucher.getBatchedVoucherLines().stream()
                    .map(this::convertBatchedVoucherLine)
                    .collect(Collectors.toList());
    batchedVoucherLines.withBatchedVoucherLine(batchedVouchersList);
    return batchedVoucherLines;
  }

  private BatchedVoucherLineType convertBatchedVoucherLine(BatchedVoucherLine batchedVoucherLine) {
    BatchedVoucherLineType batchedVoucherLineType = new BatchedVoucherLineType();
    batchedVoucherLineType.setAmount(BigDecimal.valueOf(batchedVoucherLine.getAmount()));
    batchedVoucherLineType.setExternalAccountNumber(batchedVoucherLine.getExternalAccountNumber());

    BatchedVoucherLineType.FundCodes fundCodes = new BatchedVoucherLineType.FundCodes();
    fundCodes.withFundCode(batchedVoucherLine.getFundCodes());
    batchedVoucherLineType.withFundCodes(fundCodes);
    return batchedVoucherLineType;
  }

  /**
   *
   * @param dateTime  "2019-12-06T00:00:00.000+0000"
   *
   * @return "2019-12-06T00:01:04Z"
   */
  private XMLGregorianCalendar convertDateTime(String dateTime) {
    Instant instant = Instant.from(fromFormatter.parse(dateTime));
    XMLGregorianCalendar result ;
    try {
       result = DatatypeFactory.newInstance().newXMLGregorianCalendar(instant.toString());
    } catch (DatatypeConfigurationException e) {
      throw new IllegalArgumentException(e);
    }
    return result;
  }

  /**
   *
   * @param dateTime  "2019-12-06T00:00:00.000+0000"
   *
   * @return "2019-12-06T00:01:04Z"
   */
  private XMLGregorianCalendar convertOldJavaDate(Date dateTime) {
    Instant instant = dateTime.toInstant();
    XMLGregorianCalendar result ;
    try {
      result = DatatypeFactory.newInstance().newXMLGregorianCalendar(instant.toString());
    } catch (DatatypeConfigurationException e) {
      throw new IllegalArgumentException(e);
    }
    return result;
  }
}
