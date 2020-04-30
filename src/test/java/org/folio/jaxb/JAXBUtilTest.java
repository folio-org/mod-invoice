package org.folio.jaxb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.jupiter.api.Test;

public class JAXBUtilTest {

  @Test()
  public void testShouldConvertWithoutExceptionCorrectStringDate() {
    XMLGregorianCalendar date = JAXBUtil.convertDateTime("2019-12-06T01:02:03.000+0000");
    assertEquals(2019, date.getYear());
    assertEquals(12, date.getMonth());
    assertEquals(6, date.getDay());
    assertEquals(1, date.getHour());
    assertEquals(2, date.getMinute());
    assertEquals(3, date.getSecond());
  }
}
