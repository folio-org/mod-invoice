package org.folio.jaxb;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.junit.Assert;
import org.junit.Test;
import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

public class DefaultJAXBRootElementNameResolverTest {


  @Test
  public void testShouldReturnNameFromInternalMap(){
    Map<Class<?>, QName> resolverMap = new HashMap<>();
    QName exp = new QName("batchVoucher");
    resolverMap.put(BatchVoucherType.class, new QName("batchVoucher"));
    DefaultJAXBRootElementNameResolver elementNameResolver = new DefaultJAXBRootElementNameResolver(resolverMap);
    QName act = elementNameResolver.getName(BatchVoucherType.class);
    Assert.assertEquals(exp, act);
  }

  @Test
  public void testShouldReturnRootName(){
    QName exp = new QName("root");
    DefaultJAXBRootElementNameResolver elementNameResolver = new DefaultJAXBRootElementNameResolver();
    QName act = elementNameResolver.getName(BatchVoucher.class);
    Assert.assertEquals(exp, act);
  }
}
