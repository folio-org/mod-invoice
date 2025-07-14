package org.folio;

import java.lang.reflect.Field;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtils {

  public static void setInternalState(Object target, String field, Object value) {
    Class<?> c = target.getClass();
    try {
      var f = getDeclaredFieldRecursive(field, c);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(
        "Unable to set internal state on a private field. [...]", e);
    }
  }

  private static Field getDeclaredFieldRecursive(String field, Class<?> cls) {
    try {
      return cls.getDeclaredField(field);
    } catch (NoSuchFieldException e) {
      if (cls.getSuperclass() != null) {
        return getDeclaredFieldRecursive(field, cls.getSuperclass());
      }
      throw new RuntimeException(String.format("Unable to find field: %s for class: %s", field, cls.getName()), e);
    }
  }

}
