package org.folio.converters;

import static java.math.BigInteger.valueOf;
import static org.folio.jaxb.JAXBUtil.convertOldJavaDate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.json.JsonObject;

@RunWith(MockitoJUnitRunner.class)
public class BatchVoucherModelConverterTest {
  public static final String RESOURCES_PATH = "src/test/resources/mockdata/batchVouchers";
  public static final String VALID_BATCH_VOUCHER_JSON = "35657479-83b9-4760-9c39-b58dcd02ee14.json";
  private static Path JSON_BATCH_VOUCHER_PATH = Paths.get(RESOURCES_PATH, VALID_BATCH_VOUCHER_JSON).toAbsolutePath();

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
    assertNull(xml.getStart());
    assertNull(xml.getEnd());
    assertNull(xml.getCreated());
    assertBatchedVoucher(json.getBatchedVouchers(), xml.getBatchedVouchers().getBatchedVoucher());
  }

  private void assertCommonFields(BatchVoucher json, BatchVoucherType xml) {
    assertEquals(json.getId(), xml.getId());
    assertEquals(convertOldJavaDate(json.getStart()), xml.getStart());
    assertEquals(convertOldJavaDate(json.getEnd()), xml.getEnd());
    assertEquals(convertOldJavaDate(json.getCreated()), xml.getCreated());
    assertEquals(valueOf(json.getTotalRecords()), xml.getTotalRecords());
  }

  private void assertBatchedVoucher(List<BatchedVoucher> json, List<BatchedVoucherType> xml) {
    assertEquals(json.size(), xml.size());
  }
}
