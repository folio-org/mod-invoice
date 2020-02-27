package org.folio.jaxb;

import javax.xml.transform.stream.StreamSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JAXBUtil {

  public static List<StreamSource> xsdSchemasAsStreamResources(final String schemasDirectory, final String[] fileNames){
    if (Objects.nonNull(schemasDirectory) && Objects.nonNull(fileNames)){
      return Stream.of(fileNames)
                   .filter(Objects::nonNull)
                   .map(schemaFile -> schemasDirectory + schemaFile)
                   .map(JAXBUtil.class.getClassLoader()::getResourceAsStream)
                   .map(StreamSource::new)
                   .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  public static List<Class<?>> classNamesAsClasses(final String[] rootTypeNames){
    if (Objects.nonNull(rootTypeNames)){
      return Arrays.stream(rootTypeNames)
                   .filter(Objects::nonNull)
                   .map(JAXBUtil::loadClass)
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private static Class<?> loadClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }
}
