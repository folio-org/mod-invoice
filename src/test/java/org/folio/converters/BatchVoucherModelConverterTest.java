package org.folio.converters;

import static java.math.BigInteger.valueOf;
import static org.folio.jaxb.JAXBUtil.convertOldJavaDate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class BatchVoucherModelConverterTest {
  public static final String RESOURCES_PATH = "src/test/resources/mockdata/batchVouchers";
  public static final String VALID_BATCH_VOUCHER_JSON = "35657479-83b9-4760-9c39-b58dcd02ee14.json";
  private static final Path JSON_BATCH_VOUCHER_PATH = Paths.get(RESOURCES_PATH, VALID_BATCH_VOUCHER_JSON).toAbsolutePath();

  @InjectMocks
  BatchVoucherModelConverter converter;
  @Mock
  BatchedVoucherModelConverter batchedVoucherModelConverter;

  @Test
  public void testShouldConvertJSONModelToXMLModel() throws IOException {
    String contents = new String(Files.readAllBytes(JSON_BATCH_VOUCHER_PATH));
    BatchVoucher json = new JsonObject(contents).mapTo(BatchVoucher.class);
    BatchVoucherType xml = converter.convert(json);
    assertCommonFields(json, xml);
    assertBatchedVoucher(json.getBatchedVouchers(), xml.getBatchedVouchers().getBatchedVoucher());
  }

  @Test
  public void testShouldSuccessConvertJSONModelToXMLModelIfDatesAreNull() throws IOException {
    String contents = new String(Files.readAllBytes(JSON_BATCH_VOUCHER_PATH));
    BatchVoucher json = new JsonObject(contents).mapTo(BatchVoucher.class);
    json.setStart(null);
    json.setEnd(null);
    json.setCreated(null);
    BatchVoucherType xml = converter.convert(json);
    Assertions.assertNull(xml.getStart());
    Assertions.assertNull(xml.getEnd());
    Assertions.assertNull(xml.getCreated());
    assertBatchedVoucher(json.getBatchedVouchers(), xml.getBatchedVouchers().getBatchedVoucher());
  }

  private void assertCommonFields(BatchVoucher json, BatchVoucherType xml) {
    Assertions.assertEquals(json.getId(), xml.getId());
    Assertions.assertEquals(convertOldJavaDate(json.getStart()), xml.getStart());
    Assertions.assertEquals(convertOldJavaDate(json.getEnd()), xml.getEnd());
    Assertions.assertEquals(convertOldJavaDate(json.getCreated()), xml.getCreated());
    Assertions.assertEquals(valueOf(json.getTotalRecords()), xml.getTotalRecords());
  }

  private void assertBatchedVoucher(List<BatchedVoucher> json, List<BatchedVoucherType> xml) {
    Assertions.assertEquals(json.size(), xml.size());
  }
}
