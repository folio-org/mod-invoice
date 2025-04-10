package org.folio;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CopilotGenerated {

  @AliasFor("partiallyGenerated")
  boolean value() default false;

  @AliasFor("value")
  boolean partiallyGenerated() default false;

  @AliasFor("model")
  String model() default "";
}
