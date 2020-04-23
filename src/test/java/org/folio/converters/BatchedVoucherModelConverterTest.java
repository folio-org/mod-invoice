package org.folio.converters;

import static org.folio.jaxb.JAXBUtil.convertOldJavaDate;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.folio.rest.jaxrs.model.jaxb.PaymentAccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class BatchedVoucherModelConverterTest {
  public static final String RESOURCES_PATH = "src/test/resources/mockdata/batchVouchers";
  public static final String VALID_BATCH_VOUCHER_JSON = "35657479-83b9-4760-9c39-b58dcd02ee14.json";
  private static Path JSON_BATCH_VOUCHER_PATH = Paths.get(RESOURCES_PATH, VALID_BATCH_VOUCHER_JSON)
    .toAbsolutePath();

  @InjectMocks
  BatchedVoucherModelConverter converter;
  @Mock
  BatchedVoucherLineModelConverter batchedVoucherLineModelConverter;

  @Test
  public void testShouldConvertJSONModelToXMLModel() throws IOException {
    String contents = new String(Files.readAllBytes(JSON_BATCH_VOUCHER_PATH));
    BatchVoucher json = new JsonObject(contents).mapTo(BatchVoucher.class);
    BatchedVoucher batchedVoucher = json.getBatchedVouchers().get(0);
    BatchedVoucherType batchedVoucherType = converter.convert(batchedVoucher);

    assertEquals(batchedVoucher.getVoucherNumber(), batchedVoucherType.getVoucherNumber());
    assertEquals(batchedVoucher.getAccountingCode(), batchedVoucherType.getAccountingCode());
    assertEquals(BigDecimal.valueOf(batchedVoucher.getAmount()), batchedVoucherType.getAmount());

    assertEquals(batchedVoucher.getDisbursementNumber(), batchedVoucherType.getDisbursementNumber());
    assertEquals(convertOldJavaDate(batchedVoucher.getDisbursementDate()), batchedVoucherType.getDisbursementDate());
    assertEquals(BigDecimal.valueOf(batchedVoucher.getAmount()), batchedVoucherType.getDisbursementAmount());

    assertEquals(BigDecimal.valueOf(batchedVoucher.getExchangeRate()), batchedVoucherType.getExchangeRate());
    assertEquals(batchedVoucher.getDisbursementNumber(), batchedVoucherType.getInvoiceCurrency());
    assertEquals(batchedVoucher.getSystemCurrency(), batchedVoucherType.getSystemCurrency());
    assertEquals(PaymentAccountType.fromValue(batchedVoucher.getType()
      .toString()), batchedVoucherType.getType());

    assertEquals(batchedVoucher.getVendorInvoiceNo(), batchedVoucherType.getVendorInvoiceNo());
    assertEquals(batchedVoucher.getVendorName(), batchedVoucherType.getVendorName());
    assertEquals(convertOldJavaDate(batchedVoucher.getVoucherDate()), batchedVoucherType.getVoucherDate());
    assertEquals(batchedVoucher.getInvoiceNote(), batchedVoucherType.getInvoiceNote());
    assertEquals(batchedVoucher.getStatus().toString(), batchedVoucherType.getStatus());
    assertEquals(batchedVoucher.getBatchedVoucherLines().size(), batchedVoucherType.getBatchedVoucherLines().getBatchedVoucherLine().size());
  }
}
