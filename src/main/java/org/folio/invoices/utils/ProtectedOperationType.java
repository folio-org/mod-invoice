package org.folio.invoices.utils;

import org.folio.rest.acq.model.AcquisitionsUnit;

public enum ProtectedOperationType {

  CREATE {
    @Override
    public boolean isProtected(AcquisitionsUnit unit) {
      return unit.getProtectCreate();
    }
  },
  READ {
    @Override
    public boolean isProtected(AcquisitionsUnit unit) {
      return unit.getProtectRead();
    }
  },
  UPDATE {
    @Override
    public boolean isProtected(AcquisitionsUnit unit) {
      return unit.getProtectUpdate();
    }
  },
  DELETE {
    @Override
    public boolean isProtected(AcquisitionsUnit unit) {
      return unit.getProtectDelete();
    }
  };

  public abstract boolean isProtected(AcquisitionsUnit unit);
}
