package org.folio.jaxb;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.Map;

public class DefaultJAXBRootElementNameResolver implements JAXBRootElementNameResolver{
  private final Map<Class<?>, QName> rootElementNames;

  public DefaultJAXBRootElementNameResolver() {
    this(Collections.emptyMap());
  }

  public DefaultJAXBRootElementNameResolver(Map<Class<?>, QName> rootElementNames){
    this.rootElementNames = rootElementNames;
  }

  public <T> QName getName(Class<T> clazz){
    return rootElementNames.getOrDefault(clazz, new QName("root"));
  }
}
