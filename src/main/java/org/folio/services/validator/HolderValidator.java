package org.folio.services.validator;

import org.folio.models.InvoiceWorkflowDataHolder;

import java.util.List;

public interface HolderValidator {

    void validate(List<InvoiceWorkflowDataHolder> entity);
}
