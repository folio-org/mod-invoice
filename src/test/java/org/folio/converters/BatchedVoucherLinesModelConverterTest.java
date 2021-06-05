package org.folio.converters;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.BatchedVoucherLine;
import org.folio.rest.jaxrs.model.jaxb.BatchedVoucherLineType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class BatchedVoucherLinesModelConverterTest {
  public static final String RESOURCES_PATH = "src/test/resources/mockdata/batchVouchers";
  public static final String VALID_BATCH_VOUCHER_JSON = "35657479-83b9-4760-9c39-b58dcd02ee14.json";
  private static Path JSON_BATCH_VOUCHER_PATH = Paths.get(RESOURCES_PATH, VALID_BATCH_VOUCHER_JSON)
    .toAbsolutePath();

  @InjectMocks
  BatchedVoucherLineModelConverter converter;

  @Test
  public void testShouldConvertJSONModelToXMLModel() throws IOException {
    String contents = new String(Files.readAllBytes(JSON_BATCH_VOUCHER_PATH));
    BatchVoucher json = new JsonObject(contents).mapTo(BatchVoucher.class);
    BatchedVoucherLine batchedVoucherLine =
      json.getBatchedVouchers().get(0).getBatchedVoucherLines().get(0);
    BatchedVoucherLineType batchedVoucherLineType = converter.convert(batchedVoucherLine);

    Assertions.assertEquals(BigDecimal.valueOf(batchedVoucherLine.getAmount()), batchedVoucherLineType.getAmount());
    Assertions.assertEquals(batchedVoucherLineType.getExternalAccountNumber(), batchedVoucherLineType.getExternalAccountNumber());
    assertThat(batchedVoucherLineType.getFundCodes().getFundCode(), is(batchedVoucherLine.getFundCodes()));
  }
}
