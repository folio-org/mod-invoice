package org.folio.config;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.folio.jaxb.JAXBContextWrapper;
import org.folio.jaxb.JAXBDefaultNamespacePrefixMapper;
import org.folio.jaxb.JAXBUtil;
import org.folio.jaxb.XMLConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xml.sax.SAXException;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

@Configuration
public class JAXBConfig {
  @Bean
  @Autowired
  XMLConverter xmlConverter(JAXBContextWrapper jaxbContextWrapper) {
    return new XMLConverter(jaxbContextWrapper);
  }

  @Bean
  @Autowired
  JAXBContextWrapper jaxbContextWrapper(JAXBContext jaxbContext, Schema schema, NamespacePrefixMapper namespacePrefixMapper) {
    return new JAXBContextWrapper(jaxbContext, schema, namespacePrefixMapper);
  }

  @Bean
  @Value("${jaxb.root.types}")
  JAXBContext jaxbContext(String[] rootClassNames) throws JAXBException {
    List<Class<?>> classList = JAXBUtil.classNamesAsClasses(rootClassNames);
    Class<?>[] classes = new Class<?>[classList.size()];
    return JAXBContext.newInstance(classList.toArray(classes));
  }

  @Bean
  @Value("${jaxb.schemas}")
  Schema schema(String[] schemas) throws SAXException {
    final String SCHEMA_PATH = "ramls" + File.separator + "schemas" + File.separator;
    List<StreamSource> streamSourceList = JAXBUtil.xsdSchemasAsStreamResources(SCHEMA_PATH, schemas);
    StreamSource[] streamSources = new StreamSource[streamSourceList.size()];
    return SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI)
      .newSchema(streamSourceList.toArray(streamSources));
  }

  @Bean
  @Value("#{${jaxb.nameSpacePrefixes}}")
  JAXBDefaultNamespacePrefixMapper prefixMapper(Map<String, String> nameSpacePrefixes) {
    return new JAXBDefaultNamespacePrefixMapper(nameSpacePrefixes);
  }
}
