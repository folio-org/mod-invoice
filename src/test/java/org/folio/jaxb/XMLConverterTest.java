package org.folio.jaxb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XMLConverterTest {
  private static Path XML_BATCH_VOUCHER_EXAMPLES_PATH = Paths.get("ramls/examples", "batch_voucher_xml.sample")
    .toAbsolutePath();

  XMLConverter xmlConverter = XMLConverter.getInstance();

  @Test
  public void testShouldMarshalAndUnmarshalWithoutValidation() throws IOException, XMLStreamException {
    String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
    BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
    String marshaledBatchVoucher = xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, null, false);
    BatchVoucherType unmarshaledBatchVoucherAct = xmlConverter.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, false);
    assertEquals(unmarshaledBatchVoucherExp, unmarshaledBatchVoucherAct);
  }

  @Test
  public void shouldThrowExceptiondIfMarshalWithValidation() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
      BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
      unmarshaledBatchVoucherExp.setBatchedVouchers(null);
      xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, null, true);
    });
  }

  @Test
  public void shouldThrowExceptiondIfMarshalUnsuportedTypes() {
    Assertions.assertThrows(IllegalStateException.class, () -> xmlConverter.marshal(BatchVoucher.class, new BatchVoucher(), null, true));
  }

  @Test
  public void shouldThrowExceptiondIfUnmarshalWithValidation() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
      BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
      unmarshaledBatchVoucherExp.setBatchedVouchers(null);
      String marshaledBatchVoucher = xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, null, false);
      xmlConverter.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, true);
    });

  }

  @Test
  public void shouldRaiseExceptionIfTheyIncorrect() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      String content = new String(Files.readAllBytes(XML_BATCH_VOUCHER_EXAMPLES_PATH));
      BatchVoucherType unmarshaledBatchVoucherExp = xmlConverter.unmarshal(BatchVoucherType.class, content, false);
      unmarshaledBatchVoucherExp.setBatchedVouchers(null);
      Map<String, String> nameSpaces = new HashMap<>();
      nameSpaces.put("marc", "http://www.loc.gov/MARC21/slim");
      String marshaledBatchVoucher = xmlConverter.marshal(BatchVoucherType.class, unmarshaledBatchVoucherExp, nameSpaces, false);
      xmlConverter.unmarshal(BatchVoucherType.class, marshaledBatchVoucher, true);
    });
  }
}
