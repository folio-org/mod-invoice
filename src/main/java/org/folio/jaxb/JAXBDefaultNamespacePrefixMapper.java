package org.folio.jaxb;

import java.util.Map;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

/**
 * Implementation of {@link NamespacePrefixMapper} that maps the schema
 * namespaces more to readable names. Used by the jaxb marshaller. Requires
 * setting the property "com.sun.xml.bind.namespacePrefixMapper" to an instance
 * of this class.
 * <p>
 * Requires dependency on JAXB implementation jars
 * </p>
 */
@SuppressWarnings("squid:S1191") //The com.sun.xml.bind.marshaller.NamespacePrefixMapper is part of jaxb logic
public class JAXBDefaultNamespacePrefixMapper extends NamespacePrefixMapper {

  private final Map<String, String> namespaceMap;

  /**
   * Create mappings.
   */
  public JAXBDefaultNamespacePrefixMapper(Map<String, String> namespaceMap) {
    this.namespaceMap = namespaceMap;
  }

  /* (non-Javadoc)
   * Returning null when not found based on spec.
   * @see com.sun.xml.bind.marshaller.NamespacePrefixMapper#getPreferredPrefix(java.lang.String, java.lang.String, boolean)
   */
  @Override
  public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
    return namespaceMap.getOrDefault(namespaceUri, suggestion);
  }
}
