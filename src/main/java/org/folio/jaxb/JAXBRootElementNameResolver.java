package org.folio.jaxb;

import javax.xml.namespace.QName;

public interface JAXBRootElementNameResolver {
  <T> QName getName(Class<T> clazz);
}
