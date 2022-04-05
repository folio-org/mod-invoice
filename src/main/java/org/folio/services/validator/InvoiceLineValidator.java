package org.folio.services.validator;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_IDS_NOT_UNIQUE;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_ADD_ADJUSTMENTS;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_ADJUSTMENTS;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.invoices.utils.InvoiceLineProtectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.adjusment.AdjustmentsService;

public class InvoiceLineValidator extends BaseValidator {

  private AdjustmentsService adjustmentsService = new AdjustmentsService();

  public void validateProtectedFields(Invoice existedInvoice, InvoiceLine invoiceLine, InvoiceLine existedInvoiceLine) {
    if(isPostApproval(existedInvoice)) {
      Set<String> fields = findChangedFields(invoiceLine, existedInvoiceLine, InvoiceLineProtectedFields.getFieldNames());
      verifyThatProtectedFieldsUnchanged(fields);
    }
  }

  public void validateLineAdjustmentsOnUpdate(InvoiceLine invoiceLine, Invoice invoice) {

    Errors errors = new Errors();
    List<Error> errorList = errors.getErrors();

    errorList.addAll(validateAdjustmentIdsUnique(invoiceLine));
    errorList.addAll(validateDeletedAdjustments(getAdjustmentIds(invoice),  getAdjustmentIds(invoiceLine)));
    errorList.addAll(validateAddedAdjustments(getAdjustmentIds(invoice),  getAdjustmentIds(invoiceLine)));

    if (CollectionUtils.isNotEmpty(errorList)) {
      throw new HttpException(422, errors, "Invoice line adjustments validation error");
    }
  }

  public void validateLineAdjustmentsOnCreate(InvoiceLine invoiceLine, Invoice invoice) {

    Errors errors = new Errors();
    List<Error> errorList = errors.getErrors();

    errorList.addAll(validateAdjustmentIdsUnique(invoiceLine));
    errorList.addAll(validateAddedAdjustments(getAdjustmentIds(invoice),  getAdjustmentIds(invoiceLine)));

    if (CollectionUtils.isNotEmpty(errorList)) {
      throw new HttpException(422, errors, "Invoice line adjustments validation error");
    }
  }

  private List<Error> validateAdjustmentIdsUnique(InvoiceLine invoiceLine) {
    if (isAdjustmentIdsNotUnique(invoiceLine.getAdjustments())) {
      return Collections.singletonList(ADJUSTMENT_IDS_NOT_UNIQUE.toError());
    }
    return Collections.emptyList();
  }

  private List<Error> validateDeletedAdjustments(Set<String> invoiceAdjustmentIds, Set<String> invoiceLineAdjustmentIds) {

    List<String> deletedAdjIds = invoiceAdjustmentIds.stream().filter(id -> !invoiceLineAdjustmentIds.contains(id)).collect(Collectors.toList());

    return buildAdjustmentError(deletedAdjIds, CANNOT_DELETE_ADJUSTMENTS);
  }

  private List<Error> validateAddedAdjustments(Set<String> invoiceAdjustmentIds, Set<String> invoiceLineAdjustmentIds) {

    List<String> addedAdjIds = invoiceLineAdjustmentIds.stream().filter(id -> !invoiceAdjustmentIds.contains(id)).collect(toList());

    return buildAdjustmentError(addedAdjIds, CANNOT_ADD_ADJUSTMENTS);
  }

  private List<Error> buildAdjustmentError(List<String> adjIds, ErrorCodes errorCode) {
    if (!adjIds.isEmpty()) {
      List<Parameter> parameters = adjIds.stream().map(id -> new Parameter().withKey("adjustmentId").withValue(id)).collect(toList());
      return Collections.singletonList(errorCode.toError().withParameters(parameters));
    }
    return Collections.emptyList();
  }

  private Set<String> getAdjustmentIds(InvoiceLine invoiceLine) {
    return adjustmentsService.getProratedAdjustments(invoiceLine)
      .stream()
      .map(Adjustment::getAdjustmentId)
      .collect(toSet());
  }

  private Set<String> getAdjustmentIds(Invoice invoice) {
    return adjustmentsService.getProratedAdjustments(invoice)
      .stream()
      .map(Adjustment::getId)
      .collect(toSet());
  }

  private boolean isAdjustmentIdsNotUnique(List<Adjustment> adjustments) {
    Map<String, Long> ids = adjustments.stream()
      .filter(adjustment -> isNotEmpty(adjustment.getAdjustmentId()))
      .collect(Collectors.groupingBy(Adjustment::getAdjustmentId, Collectors.counting()));

    return ids.entrySet().stream().anyMatch(entry -> entry.getValue() > 1);
  }


}
