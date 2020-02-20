package org.folio.schemas.xsd;

import org.folio.rest.jaxrs.model.BatchVoucherType;
import org.junit.Test;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;


public class BatchVoucherSchemaXSDTest {
  private static Path XSD_BATCH_VOUCHER_SCHEMA_PATH = Paths.get("", "ramls/schemas/batch_voucher.xsd").toAbsolutePath();
  private static Path XML_BATCH_VOUCHER_EXAMPLES_PATH = Paths.get("ramls/examples","batch_voucher_xml.sample").toAbsolutePath();

  @Test
  public void testValidateSampleDataBySchema() throws Exception {
    InputStream xsd = new FileInputStream(XSD_BATCH_VOUCHER_SCHEMA_PATH.toString());

    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Schema schema = factory.newSchema(new StreamSource(xsd));

    Validator validator = schema.newValidator();
    validator.validate(new StreamSource(new FileReader(XML_BATCH_VOUCHER_EXAMPLES_PATH.toString())));
  }
}
