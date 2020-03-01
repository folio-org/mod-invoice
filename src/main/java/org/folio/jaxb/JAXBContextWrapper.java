package org.folio.jaxb;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;

import static javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT;

public class JAXBContextWrapper {
  public static final String XML_DECLARATION = "com.sun.xml.bind.xmlDeclaration";

  private final NamespacePrefixMapper namespacePrefixMapper;
  private final JAXBContext jaxbContext;
  private final Schema schema;
  private final boolean isOutputFormatted;
  private final boolean hasXmlDeclaration;

  /**
   * The main purpose is to initialize JAXB Marshaller and Unmarshaller to use the instances for business logic operations
   */
  public JAXBContextWrapper(JAXBContext jaxbContext, Schema schema, NamespacePrefixMapper namespacePrefixMapper) {
    this.jaxbContext = jaxbContext;
    this.schema = schema;
    this.namespacePrefixMapper = namespacePrefixMapper;
    this.isOutputFormatted =  Boolean.parseBoolean(System.getProperty(JAXB_FORMATTED_OUTPUT, Boolean.TRUE.toString()));
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
    // needed to replace the namespace prefixes with a more readable format.
    jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", namespacePrefixMapper);
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
}
