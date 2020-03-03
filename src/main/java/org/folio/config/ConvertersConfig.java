package org.folio.config;

import java.util.HashSet;
import java.util.Set;

import org.folio.converters.BatchVoucherModelConverter;
import org.folio.converters.BatchedVoucherLineModelConverter;
import org.folio.converters.BatchedVoucherModelConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

@Configuration
public class ConvertersConfig {

  @Bean
  @Autowired
  public ConversionService conversionService(Set<Converter> converters) {
    DefaultConversionService service = new DefaultConversionService();
    converters.forEach(converter -> service.addConverter(converter));
    return service;
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
