package org.folio.jaxb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang3.time.StopWatch;
import org.folio.exceptions.ClassInitializationException;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.xml.sax.SAXException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public final class XMLConverter {
  private static final Logger LOG = LogManager.getLogger(XMLConverter.class);
  private JAXBContextWrapper jaxbContextWrapper;
  private final JAXBRootElementNameResolver rootElementNameResolver;
  private final Class<?>[] rootClassNames = new Class<?>[] { BatchVoucherType.class };
  private final String[] schemas = new String[] { "batch_voucher.xsd" };

  private XMLConverter() {
    try {
      this.jaxbContextWrapper = new JAXBContextWrapper(rootClassNames, schemas);
    } catch (JAXBException | SAXException e) {
      throw new ClassInitializationException(e.getMessage(), e);
    }
    this.rootElementNameResolver = initJAXBRootElementNameResolver();
  }

  private static class SingletonHolder {
    public static final XMLConverter HOLDER_INSTANCE = new XMLConverter();
  }

  public static XMLConverter getInstance() {
    return SingletonHolder.HOLDER_INSTANCE;
  }

  /**
   * Marshals object and returns string representation
   *
   * @param xmlObject          object to marshal
   * @param isValidationNeeded if set to true, then validate by XSD schema
   * @return marshaled object as string representation
   */
  public <T> String marshal(Class<T> clazz, T xmlObject, Map<String, String> nameSpaces, boolean isValidationNeeded)
      throws XMLStreamException {
    StopWatch timer = LOG.isDebugEnabled() ? StopWatch.createStarted() : null;
    XMLStreamWriter xmlOut = null;
    try (StringWriter writer = new StringWriter()) {
      xmlOut = XMLOutputFactory.newFactory()
        .createXMLStreamWriter(writer);
      JAXBElement<T> element = new JAXBElement<>(rootElementNameResolver.getName(clazz), clazz, xmlObject);
      xmlOut.writeStartDocument();

      fillNameSpaces(nameSpaces, xmlOut);

      Marshaller jaxbMarshaller = jaxbContextWrapper.createMarshaller(isValidationNeeded);
      jaxbMarshaller.marshal(element, xmlOut);
      xmlOut.writeEndDocument();
      close(xmlOut);
      return writer.toString();
    } catch (JAXBException | IOException e) {
      // In case there is an issue to marshal response, there is no way to handle it
      throw new IllegalStateException("The " + xmlObject.getClass()
        .getName() + " response cannot be converted to string representation.", e);
    } finally {
      close(xmlOut);
      logExecutionTime(xmlObject.getClass()
        .getName() + " converted to string", timer);
    }
  }

  private void fillNameSpaces(Map<String, String> nameSpaces, XMLStreamWriter xmlOut) throws XMLStreamException {
    if (Objects.nonNull(nameSpaces)) {
      for (Map.Entry<String, String> pair : nameSpaces.entrySet()) {
        xmlOut.writeNamespace(pair.getKey(), pair.getValue());
      }
    }
  }

  /**
   * Unmarshals object based on passed string
   *
   * @param xmlStr             the string representation of XML
   * @param clazz              returned type
   * @param isValidationNeeded if set to true, then validate by XSD schema
   * @return the {@link T} object based on passed string
   */
  public <T> T unmarshal(Class<T> clazz, String xmlStr, boolean isValidationNeeded) {
    StopWatch timer = LOG.isDebugEnabled() ? StopWatch.createStarted() : null;
    try (StringReader reader = new StringReader(xmlStr)) {
      // Unmarshaller is not thread-safe, so we should create every time a new one
      Unmarshaller jaxbUnmarshaller = jaxbContextWrapper.createUnmarshaller(isValidationNeeded);
      Object response = jaxbUnmarshaller.unmarshal(reader);
      return clazz.cast(response);
    } catch (JAXBException e) {
      // In case there is an issue to unmarshal response, there is no way to handle it
      throw new IllegalStateException("The string cannot be converted to " + clazz.getName() + " response.", e);
    } finally {
      logExecutionTime("String converted to " + clazz.getName(), timer);
    }
  }

  /**
   * Unmarshals object based on passed string
   *
   * @param byteSource         the byte[] representation of XML
   * @param clazz              returned type
   * @param isValidationNeeded if set to true, then validate by XSD schema
   * @return the {@link T} object based on passed string
   */
  public <T> T unmarshal(Class<T> clazz, byte[] byteSource, boolean isValidationNeeded) {
    StopWatch timer = LOG.isDebugEnabled() ? StopWatch.createStarted() : null;
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(byteSource)) {
      Unmarshaller jaxbUnmarshaller = jaxbContextWrapper.createUnmarshaller(isValidationNeeded);
      Object response = jaxbUnmarshaller.unmarshal(inputStream);
      return clazz.cast(response);
    } catch (JAXBException | IOException e) {
      // In case there is an issue to unmarshal byteSource, there is no way to handle it
      throw new IllegalStateException("The byte array cannot be converted to JAXB object response.", e);
    } finally {
      logExecutionTime("Array of bytes converted to Object", timer);
    }
  }

  private void logExecutionTime(final String msg, StopWatch timer) {
    if (timer != null) {
      timer.stop();
      LOG.debug("{} after {} ms", msg, timer.getTime());
    }
  }

  private void close(XMLStreamWriter streamWriter) throws XMLStreamException {
    if (Objects.nonNull(streamWriter)) {
      streamWriter.close();
    }
  }

  private JAXBRootElementNameResolver initJAXBRootElementNameResolver() {
    Map<Class<?>, QName> elementNames = new HashMap<>();
    elementNames.put(BatchVoucherType.class, new QName("batchVoucher"));
    return new DefaultJAXBRootElementNameResolver(elementNames);
  }
}
