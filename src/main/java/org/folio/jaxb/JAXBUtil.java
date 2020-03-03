package org.folio.jaxb;

import org.folio.exceptions.ConversionFailedException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JAXBUtil {
  private static final DateTimeFormatter fromFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private JAXBUtil() {
  }

  public static List<StreamSource> xsdSchemasAsStreamResources(final String schemasDirectory, final String[] fileNames) {
    if (Objects.nonNull(schemasDirectory) && Objects.nonNull(fileNames)) {
      return Stream.of(fileNames)
        .filter(Objects::nonNull)
        .map(schemaFile -> schemasDirectory + schemaFile)
        .map(JAXBUtil.class.getClassLoader()::getResourceAsStream)
        .map(StreamSource::new)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  /**
   *
   * @param dateTime "2019-12-06T00:00:00.000+0000"
   *
   * @return "2019-12-06T00:01:04Z"
   */
  public static XMLGregorianCalendar convertDateTime(String dateTime) {
    Instant instant = Instant.from(fromFormatter.parse(dateTime));
    return getXmlGregorianCalendar(instant);
  }

  /**
   *
   * @param dateTime "2019-12-06T00:00:00.000+0000"
   *
   * @return "2019-12-06T00:01:04Z"
   */
  public static XMLGregorianCalendar convertOldJavaDate(Date dateTime) {
    Instant instant = dateTime.toInstant();
    return getXmlGregorianCalendar(instant);
  }

  private static XMLGregorianCalendar getXmlGregorianCalendar(Instant instant) {
    XMLGregorianCalendar result;
    try {
      result = DatatypeFactory.newInstance()
        .newXMLGregorianCalendar(instant.toString());
    } catch (DatatypeConfigurationException e) {
      throw new ConversionFailedException(Instant.class, XMLGregorianCalendar.class, e);
    }
    return result;
  }
}
