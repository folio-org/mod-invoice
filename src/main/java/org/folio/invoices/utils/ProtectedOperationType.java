package org.folio.invoices.utils;

import org.folio.rest.acq.model.AcquisitionsUnit;

public enum ProtectedOperationType {

  CREATE {
    @Override
    public boolean isProtected(AcquisitionsUnit unit) {
      return unit.getProtectCreate();
    }
  };

  public abstract boolean isProtected(AcquisitionsUnit unit);
}
