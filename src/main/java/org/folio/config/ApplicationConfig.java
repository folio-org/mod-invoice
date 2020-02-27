package org.folio.config;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.folio.converters.BatchVoucherModelConverter;
import org.folio.converters.BatchedVoucherLineModelConverter;
import org.folio.converters.BatchedVoucherModelConverter;
import org.folio.jaxb.JAXBContextWrapper;
import org.folio.jaxb.JAXBUtil;
import org.folio.jaxb.XMLConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;
import javax.annotation.Resource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

@Configuration
@ComponentScan({ "org.folio.rest.impl", "org.folio.invoices" })
@Import({JAXBConfig.class, ConvertersConfig.class})
public class ApplicationConfig {
  @Bean
  PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
    PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
    ClassPathResource location = new ClassPathResource("application.properties");
    configurer.setLocations(location);
    return configurer;
  }

}
