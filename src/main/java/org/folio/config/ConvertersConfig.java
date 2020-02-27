package org.folio.config;

import org.folio.converters.BatchVoucherModelConverter;
import org.folio.converters.BatchedVoucherLineModelConverter;
import org.folio.converters.BatchedVoucherModelConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class ConvertersConfig {

  @Bean
  @Autowired
  public ConversionService conversionService(ConversionServiceFactoryBean factory) {
    return factory.getObject();
  }

  @Bean
  @Autowired
  ConversionServiceFactoryBean factory(Set<Converter> converters) {
    ConversionServiceFactoryBean bean = new ConversionServiceFactoryBean();
    bean.setConverters(converters);
    return bean;
  }

  @Bean
  @Autowired
  Set<Converter> converters(BatchVoucherModelConverter batchVoucherModelConverter) {
    Set<Converter> converters = new HashSet<>();
    converters.add(batchVoucherModelConverter);
    return converters;
  }

  @Bean
  @Autowired
  public BatchVoucherModelConverter batchVoucherModelConverter(BatchedVoucherModelConverter batchedVoucherModelConverter) {
    return new BatchVoucherModelConverter(batchedVoucherModelConverter);
  }

  @Bean
  @Autowired
  public BatchedVoucherModelConverter batchedVoucherModelConverter(
    BatchedVoucherLineModelConverter batchedVoucherLineModelConverter) {
    return new BatchedVoucherModelConverter(batchedVoucherLineModelConverter);
  }

  @Bean
  public BatchedVoucherLineModelConverter batchedVoucherLineModelConverter() {
    return new BatchedVoucherLineModelConverter();
  }
}
