package org.folio.jaxb;

import org.junit.Assert;
import org.junit.Test;
import javax.xml.datatype.XMLGregorianCalendar;

public class JAXBUtilTest {

  @Test()
  public void testShouldConvertWithoutExceptionCorrectStringDate(){
    XMLGregorianCalendar date = JAXBUtil.convertDateTime("2019-12-06T01:02:03.000+0000");
    Assert.assertEquals(2019, date.getYear());
    Assert.assertEquals(12, date.getMonth());
    Assert.assertEquals(6, date.getDay());
    Assert.assertEquals(1, date.getHour());
    Assert.assertEquals(2, date.getMinute());
    Assert.assertEquals(3, date.getSecond());
  }
}
