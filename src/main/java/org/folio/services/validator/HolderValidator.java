package org.folio.services.validator;

import java.util.List;

import org.folio.models.InvoiceWorkflowDataHolder;

public interface HolderValidator {

    void validate(List<InvoiceWorkflowDataHolder> entity);
}
