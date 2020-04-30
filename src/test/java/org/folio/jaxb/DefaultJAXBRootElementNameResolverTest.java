package org.folio.jaxb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.folio.rest.jaxrs.model.BatchVoucher;
import org.folio.rest.jaxrs.model.jaxb.BatchVoucherType;
import org.junit.jupiter.api.Test;


public class DefaultJAXBRootElementNameResolverTest {


  @Test
  public void testShouldReturnNameFromInternalMap(){
    Map<Class<?>, QName> resolverMap = new HashMap<>();
    QName exp = new QName("batchVoucher");
    resolverMap.put(BatchVoucherType.class, new QName("batchVoucher"));
    DefaultJAXBRootElementNameResolver elementNameResolver = new DefaultJAXBRootElementNameResolver(resolverMap);
    QName act = elementNameResolver.getName(BatchVoucherType.class);
    assertEquals(exp, act);
  }

  @Test
  public void testShouldReturnRootName(){
    QName exp = new QName("root");
    DefaultJAXBRootElementNameResolver elementNameResolver = new DefaultJAXBRootElementNameResolver();
    QName act = elementNameResolver.getName(BatchVoucher.class);
    assertEquals(exp, act);
  }
}
