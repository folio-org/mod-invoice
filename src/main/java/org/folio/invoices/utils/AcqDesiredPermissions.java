package org.folio.invoices.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public enum AcqDesiredPermissions {
  ASSIGN("invoices.acquisitions-units-assignments.assign"),
  MANAGE("invoices.acquisitions-units-assignments.manage"),
  APPROVE("invoice.item.approve"),
  PAY("invoice.item.pay"),
  FISCAL_YEAR_UPDATE("invoices.fiscal-year.update");

  private String permission;
  private static final List<String> values;
  static {
    values = Collections.unmodifiableList(Arrays.stream(AcqDesiredPermissions.values())
      .map(AcqDesiredPermissions::getPermission)
      .collect(Collectors.toList()));
  }

  AcqDesiredPermissions(String permission) {
    this.permission = permission;
  }

  public String getPermission() {
    return permission;
  }

  public static List<String> getValues() {
    return values;
  }
}
