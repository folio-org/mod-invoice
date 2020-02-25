package org.folio.services;

import org.folio.config.ApplicationConfig;
import org.folio.helpers.jaxb.JAXBHelper;
import org.folio.rest.jaxrs.model.BatchVoucherType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ApplicationConfig.class)
public class JAXBHelperTest {
  private static Path XML_BATCH_VOUCHER_EXAMPLES_PATH = Paths.get("ramls/examples", "batch_voucher_xml.sample")
    .toAbsolutePath();

  @Autowired
  JAXBHelper jaxbHelperTest;

  @Test
  public void testShouldMarshalAndUnmarshalWithoutValidation() throws IOException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = jaxbHelperTest.unmarshal(BatchVoucherType.class, content, false);
    String marshaledBatchVoucher = jaxbHelperTest.marshal(unmarshaledBatchVoucherExp, false);
    BatchVoucherType unmarshaledBatchVoucherAct = jaxbHelperTest.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, false);
    Assert.assertEquals(unmarshaledBatchVoucherExp, unmarshaledBatchVoucherAct);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptiondIfMarshalWithValidation() throws IOException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = jaxbHelperTest.unmarshal(BatchVoucherType.class, content, false);
    unmarshaledBatchVoucherExp.setBatchedVouchers(null);
    jaxbHelperTest.marshal(unmarshaledBatchVoucherExp, true);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptiondIfUnmarshalWithValidation() throws IOException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = jaxbHelperTest.unmarshal(BatchVoucherType.class, content, false);
    unmarshaledBatchVoucherExp.setBatchedVouchers(null);
    String marshaledBatchVoucher = jaxbHelperTest.marshal(unmarshaledBatchVoucherExp, false);
    BatchVoucherType unmarshaledBatchVoucherAct = jaxbHelperTest.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, true);
  }
}
