package org.folio.invoices.utils;

import java.util.function.Function;

public interface ProtectedField<T> {
  public abstract String getFieldName();
  public abstract Function<T, Object> getGetter();
}
