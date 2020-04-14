package org.folio.jaxb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.junit.Assert;
import org.junit.Test;
import javax.xml.stream.XMLStreamException;

public class XMLConverterTest {
  private static Path XML_BATCH_VOUCHER_EXAMPLES_PATH = Paths.get("ramls/examples", "batch_voucher_sample.xml")
    .toAbsolutePath();

  XMLConverter xmlConverter = XMLConverter.getInstance();

  @Test
  public void testShouldMarshalAndUnmarshalWithoutValidation() throws IOException, XMLStreamException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
    String marshaledBatchVoucher = xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, null,false);
    BatchVoucherType unmarshaledBatchVoucherAct = xmlConverter.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, false);
    Assert.assertEquals(unmarshaledBatchVoucherExp, unmarshaledBatchVoucherAct);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptiondIfMarshalWithValidation() throws IOException, XMLStreamException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
    unmarshaledBatchVoucherExp.setBatchedVouchers(null);
    xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, null,true);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptiondIfMarshalUnsuportedTypes() throws IOException, XMLStreamException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    xmlConverter.marshal(BatchVoucher.class, new BatchVoucher(), null,true);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowExceptiondIfUnmarshalWithValidation() throws IOException, XMLStreamException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
    unmarshaledBatchVoucherExp.setBatchedVouchers(null);
    String marshaledBatchVoucher = xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, null,false);
    xmlConverter.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, true);
  }

  @Test(expected = IllegalStateException.class)
  public void shouldRaiseExceptionIfTheyIncorrect() throws IOException, XMLStreamException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
    unmarshaledBatchVoucherExp.setBatchedVouchers(null);
    Map<String, String> nameSpaces = new HashMap<>();
    nameSpaces.put("marc", "http://www.loc.gov/MARC21/slim");
    String marshaledBatchVoucher = xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, nameSpaces,false);
    xmlConverter.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, true);
  }
}
