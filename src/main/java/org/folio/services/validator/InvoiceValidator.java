package org.folio.services.validator;

import static org.folio.invoices.utils.ErrorCodes.ACCOUNTING_CODE_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_IDS_NOT_UNIQUE;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_PAY_INVOICE_WITHOUT_APPROVAL;
import static org.folio.invoices.utils.ErrorCodes.FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION;
import static org.folio.invoices.utils.ErrorCodes.LINE_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH;
import static org.folio.invoices.utils.ErrorCodes.LOCK_AND_CALCULATED_TOTAL_MISMATCH;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.invoices.utils.InvoiceUnprotectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.adjusment.AdjustmentsService;
import org.javamoney.moneta.Money;

public class InvoiceValidator extends BaseValidator {

  public static final String TOTAL = "total";
  public static final String NO_INVOICE_LINES_ERROR_MSG = "An invoice cannot be approved if there are no corresponding lines of invoice.";

  private AdjustmentsService adjustmentsService = new AdjustmentsService();

  public void validateInvoiceProtectedFields(Invoice invoice, Invoice invoiceFromStorage) {
    if(isPostApproval(invoiceFromStorage)) {
      Set<String> fields = findChangedFields(invoice, invoiceFromStorage, InvoiceProtectedFields.getFieldNames());
      verifyThatProtectedFieldsUnchanged(fields);
    }
  }

  public boolean  isInvoiceUnprotectedFieldsChanged(Invoice invoice, Invoice invoiceFromStorage) {
    if (invoice.getStatus() == Invoice.Status.PAID
      && invoiceFromStorage.getStatus() == Invoice.Status.PAID ){
        Set<String> fields = findChangedFields(invoice, invoiceFromStorage, InvoiceUnprotectedFields.getFieldNames());
        return !(CollectionUtils.isNotEmpty(fields));
    }
    return false;
  }

  public void validateInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    if(!isInvoiceUnprotectedFieldsChanged(invoice, invoiceFromStorage)){
    validateInvoiceStatusTransition(invoice, invoiceFromStorage);
    }
    validateInvoiceProtectedFields(invoice, invoiceFromStorage);
  }

  public void validateInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    if (!(invoice.getStatus() == Invoice.Status.PAID && invoiceFromStorage.getStatus() == Invoice.Status.PAID)
    && (invoice.getStatus() == Invoice.Status.PAID && invoiceFromStorage.getStatus() != Invoice.Status.APPROVED)) {
      throw new HttpException(400, CANNOT_PAY_INVOICE_WITHOUT_APPROVAL);
    }
   }

  public void validateIncomingInvoice(Invoice invoice) {

    Errors errors = new Errors();
    List<Error> errorList = errors.getErrors();

    if (!isPostApproval(invoice) && (invoice.getApprovalDate() != null || invoice.getApprovedBy() != null)) {
      errorList.add(INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION.toError());
    }
    if (isAdjustmentIdsNotUnique(invoice.getAdjustments())) {
      errorList.add(ADJUSTMENT_IDS_NOT_UNIQUE.toError());
    }
    if (CollectionUtils.isNotEmpty(errorList)) {
      throw new HttpException(422, errors, "Invoice validation error");
    }
  }

  public void validateInvoiceTotals(Invoice invoice) {
    if (invoice.getLockTotal() != null && invoice.getTotal() != null
      && Double.compare(invoice.getLockTotal(), invoice.getTotal()) != 0) {
      throw new HttpException(400, LOCK_AND_CALCULATED_TOTAL_MISMATCH);
    }
  }

  private boolean isAdjustmentIdsNotUnique(List<Adjustment> adjustments) {
    Map<String, Long> ids = adjustments.stream()
      .filter(adjustment -> StringUtils.isNotEmpty(adjustment.getId()))
      .collect(Collectors.groupingBy(Adjustment::getId, Collectors.counting()));

    return ids.entrySet().stream().anyMatch(entry -> entry.getValue() > 1);
  }

  public void validateBeforeApproval(Invoice invoice, List<InvoiceLine> lines) {
    checkVendorHasAccountingCode(invoice);
    validateInvoiceTotals(invoice);
    verifyInvoiceLineNotEmpty(lines);
    validateInvoiceLineFundDistributions(lines, Monetary.getCurrency(invoice.getCurrency()));
    validateInvoiceAdjustmentsDistributions(adjustmentsService.getNotProratedAdjustments(invoice) , Monetary.getCurrency(invoice.getCurrency()));
  }

  private void checkVendorHasAccountingCode(Invoice invoice) {
    // accounting code is erpCode from organization record
    if (Boolean.TRUE.equals(invoice.getExportToAccounting()) && StringUtils.isEmpty(invoice.getAccountingCode())) {
      throw new HttpException(400, ACCOUNTING_CODE_NOT_PRESENT);
    }
  }

  private void verifyInvoiceLineNotEmpty(List<InvoiceLine> invoiceLines) {
    if (invoiceLines.isEmpty()) {
      throw new HttpException(500, NO_INVOICE_LINES_ERROR_MSG);
    }
  }

  private void validateInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines, CurrencyUnit currencyUnit) {
    for (InvoiceLine line : invoiceLines){
      if (CollectionUtils.isEmpty(line.getFundDistributions())) {
        throw new HttpException(400, FUND_DISTRIBUTIONS_NOT_PRESENT);
      }

      if (isFundDistributionSummaryNotValid(line, currencyUnit)) {
        throw new HttpException(400, LINE_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH);
      }
    }
  }

  private boolean isFundDistributionSummaryNotValid(InvoiceLine line, CurrencyUnit currencyUnit) {
    return ObjectUtils.notEqual(sumMixedDistributions(line, currencyUnit), line.getTotal());
  }

  private Double sumMixedDistributions(InvoiceLine line, CurrencyUnit currencyUnit) {
    return sumMixedDistributions(line.getFundDistributions(), line.getTotal(), currencyUnit);
  }

  private void validateInvoiceAdjustmentsDistributions(List<Adjustment> adjustments, CurrencyUnit currencyUnit) {
    for (Adjustment adjustment : adjustments){
      if (CollectionUtils.isEmpty(adjustment.getFundDistributions())) {
        throw new HttpException(400, ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT);
      }

      if (isFundDistributionSummaryNotValid(adjustment, currencyUnit)) {
        throw new HttpException(400, ADJUSTMENT_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH);
      }
    }
  }

  private boolean isFundDistributionSummaryNotValid(Adjustment adjustment, CurrencyUnit currencyUnit) {
    return ObjectUtils.notEqual(sumMixedDistributions(adjustment.getFundDistributions(), adjustment.getValue(), currencyUnit), adjustment.getValue());
  }

  private Double sumMixedDistributions(List<FundDistribution> fundDistributions, double total, CurrencyUnit currencyUnit) {
    return fundDistributions.stream()
      .map(fundDistribution -> getFundDistributionAmount(fundDistribution, total, currencyUnit))
      .reduce(MonetaryAmount::add)
      .orElse(Money.zero(currencyUnit))
      .getNumber().doubleValue();
  }
}
