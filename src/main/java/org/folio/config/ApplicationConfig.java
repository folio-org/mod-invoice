package org.folio.config;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.folio.rest.impl.BatchVoucherHelper;
import org.folio.rest.jaxrs.model.BatchVoucherType;
import org.folio.helpers.jaxb.JAXBHelper;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

@Configuration
@ComponentScan({ "org.folio.rest.impl", "org.folio.invoices" })
public class ApplicationConfig {
  @Bean
  public PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    ClassPathResource location = new ClassPathResource("application.properties");
    configurer.setLocations(location);
    return configurer;
  }

  @Bean
  public JAXBHelper jaxbHelper(@Value("${jaxb.service.schemas}") String[] schemas,
                               @Value("#{${jaxb.service.nameSpacePrefixes}}") Map<String, String> nameSpacePrefixes,
                               @Value("${jaxb.formatted.output:true}") String formattedOutput,
                               @Value("${com.sun.xml.bind.xmlDeclaration:false}") String xmlDeclaration) {
    try {
      System.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, formattedOutput);
      System.setProperty(JAXBHelper.XML_DECLARATION, xmlDeclaration);
      return new JAXBHelper(jaxbContext(), schemas(schemas), prefixMapper(nameSpacePrefixes));
    } catch (JAXBException | SAXException e) {
      throw new BeanInitializationException("JAXBService initialization error");
    }
  }

  @Bean
  public JAXBContext jaxbContext() throws JAXBException {
    return JAXBContext.newInstance(BatchVoucherType.class);
  }

  @Bean
  @Scope(
    value = ConfigurableBeanFactory.SCOPE_PROTOTYPE,
    proxyMode = ScopedProxyMode.TARGET_CLASS)
  public BatchVoucherHelper batchVoucherHelper(){
    return new BatchVoucherHelper();
  }

  private Schema schemas(String[] schemas) throws SAXException {
    StreamSource[] streamSources = Optional.ofNullable(schemas)
      .map(this::createSchemaStreams)
      .orElse(new StreamSource[0]);
    return SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI).newSchema(streamSources);
  }

  private StreamSource[] createSchemaStreams(String[] schemas) {
    final String SCHEMA_PATH = "ramls" + File.separator + "schemas" + File.separator;
    final ClassLoader classLoader = this.getClass().getClassLoader();
    return Stream.of(schemas)
      .map(schemaFile -> SCHEMA_PATH + schemaFile)
      .map(classLoader::getResourceAsStream)
      .map(StreamSource::new)
      .toArray(StreamSource[]::new);
  }

  private NamespacePrefixMapper prefixMapper(Map<String, String> nameSpacePrefixes) {
    NamespacePrefixMapper namespacePrefixMapper = new NamespacePrefixMapper() {
      @Override
      public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
        return nameSpacePrefixes.getOrDefault(namespaceUri, suggestion);
      }
    };
    return namespacePrefixMapper;
  }

}
