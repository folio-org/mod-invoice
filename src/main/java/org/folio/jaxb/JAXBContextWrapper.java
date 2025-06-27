package org.folio.jaxb;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT;

import java.io.File;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

public final class JAXBContextWrapper {
  public static final String XML_DECLARATION = "com.sun.xml.bind.xmlDeclaration";

  private final JAXBContext jaxbContext;
  private final Schema schema;
  private final boolean isOutputFormatted;
  private final boolean hasXmlDeclaration;

  /**
   * The main purpose is to initialize JAXB Marshaller and Unmarshaller to use the instances for business logic operations
   */
  public JAXBContextWrapper(Class<?>[] rootClassNames, String[] schemas) throws JAXBException, SAXException {
    this.jaxbContext = initJaxbContext(rootClassNames);
    this.schema = initSchema(schemas);
    this.isOutputFormatted = Boolean.parseBoolean(System.getProperty(JAXB_FORMATTED_OUTPUT, Boolean.TRUE.toString()));
    this.hasXmlDeclaration = Boolean.parseBoolean(System.getProperty(XML_DECLARATION, Boolean.FALSE.toString()));
  }

  public Marshaller createMarshaller(boolean isValidationNeeded) throws JAXBException {
    // Marshaller is not thread-safe, so we should create every time a new one
    Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
    if (isValidationNeeded) {
      jaxbMarshaller.setSchema(schema);
    }
    jaxbMarshaller.setProperty(XML_DECLARATION, hasXmlDeclaration);
    jaxbMarshaller.setProperty(JAXB_FORMATTED_OUTPUT, isOutputFormatted);
    return jaxbMarshaller;
  }

  public Unmarshaller createUnmarshaller(boolean isValidationNeeded) throws JAXBException {
    // Unmarshaller is not thread-safe, so we should create every time a new one
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    if (isValidationNeeded) {
      jaxbUnmarshaller.setSchema(schema);
    }
    return jaxbUnmarshaller;
  }

  private JAXBContext initJaxbContext(final Class<?>[] rootClassNames) throws JAXBException {
    return JAXBContext.newInstance(rootClassNames);
  }

  private Schema initSchema(final String[] schemas) throws SAXException {
    final String SCHEMA_PATH = "ramls" + File.separator + "schemas" + File.separator;
    List<StreamSource> streamSourceList = JAXBUtil.xsdSchemasAsStreamResources(SCHEMA_PATH, schemas);
    StreamSource[] streamSources = new StreamSource[streamSourceList.size()];
    var schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
    // prevent XML external entity injection (XXE)
    // https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#schemafactory
    // https://semgrep.dev/docs/cheat-sheets/java-xxe#3g-schemafactory
    schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return schemaFactory.newSchema(streamSourceList.toArray(streamSources));
  }
}
