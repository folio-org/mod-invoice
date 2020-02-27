package org.folio.jaxb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.lang3.time.StopWatch;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class XMLConverter {
  private static final Logger LOG = LoggerFactory.getLogger(XMLConverter.class);
  private final JAXBContextWrapper jaxbContextWrapper;

  public XMLConverter(JAXBContextWrapper jaxbContextWrapper) {
    this.jaxbContextWrapper = jaxbContextWrapper;
  }

  /**
   * Marshals object and returns string representation
   *
   * @param xmlObject          object to marshal
   * @param isValidationNeeded
   * @return marshaled object as string representation
   */
  public <T> String marshal(T xmlObject, boolean isValidationNeeded) {
    StopWatch timer = LOG.isDebugEnabled() ? StopWatch.createStarted() : null;
    try (StringWriter writer = new StringWriter()) {
      Marshaller jaxbMarshaller = jaxbContextWrapper.createMarshaller(isValidationNeeded);
      jaxbMarshaller.marshal(xmlObject, writer);
      return writer.toString();
    } catch (JAXBException | IOException e) {
      // In case there is an issue to marshal response, there is no way to handle it
      throw new IllegalStateException("The " + xmlObject.getClass()
        .getName() + " response cannot be converted to string representation.", e);
    } finally {
      logExecutionTime(xmlObject.getClass()
        .getName() + " converted to string", timer);
    }
  }

  /**
   * Unmarshals object based on passed string
   *
   * @param xmlStr the string representation of XML
   * @param clazz  returned type
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
      throw new IllegalStateException("The string cannot be converted to OAI-PMH response.", e);
    } finally {
      logExecutionTime("String converted to " + clazz.getName(), timer);
    }
  }

  /**
   * Unmarshals object based on passed string
   *
   * @param byteSource         the byte[] representation of XML
   * @param clazz              returned type
   * @param isValidationNeeded
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
}
