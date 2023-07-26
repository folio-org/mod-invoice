package org.folio.services.validator;

import static java.math.RoundingMode.HALF_EVEN;
import static org.folio.invoices.utils.ErrorCodes.ACCOUNTING_CODE_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_IDS_NOT_UNIQUE;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_MIX_TYPES_FOR_ZERO_PRICE;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_PAY_INVOICE_WITHOUT_APPROVAL;
import static org.folio.invoices.utils.ErrorCodes.FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION;
import static org.folio.invoices.utils.ErrorCodes.INCORRECT_FUND_DISTRIBUTION_TOTAL;
import static org.folio.invoices.utils.ErrorCodes.LOCK_AND_CALCULATED_TOTAL_MISMATCH;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.AMOUNT;
import static org.folio.rest.jaxrs.model.FundDistribution.DistributionType.PERCENTAGE;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.adjusment.AdjustmentsService;

import com.google.common.collect.Lists;

public class InvoiceValidator {

  public static final String TOTAL = "total";
  public static final String NO_INVOICE_LINES_ERROR_MSG = "An invoice cannot be approved if there are no corresponding lines of invoice.";

  public static final String REMAINING_AMOUNT_FIELD = "remainingAmount";
  private static final BigDecimal ZERO_REMAINING_AMOUNT = BigDecimal.ZERO.setScale(2, HALF_EVEN);
  private static final BigDecimal ONE_HUNDRED_PERCENT = BigDecimal.valueOf(100);

  private final AdjustmentsService adjustmentsService = new AdjustmentsService();

  public void validateInvoiceProtectedFields(Invoice invoice, Invoice invoiceFromStorage) {
    if(isPostApproval(invoiceFromStorage)) {
      ProtectedFieldsValidator.validate(invoice, invoiceFromStorage, InvoiceProtectedFields.values());
    }
  }

  public void validateInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    if (invoice.getStatus() != invoiceFromStorage.getStatus()) {
      validateInvoiceStatusTransition(invoice, invoiceFromStorage);
    }
    validateInvoiceProtectedFields(invoice, invoiceFromStorage);
  }

  public void validateInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    if (invoice.getStatus() == Invoice.Status.PAID && invoiceFromStorage.getStatus() != Invoice.Status.APPROVED) {
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

  public boolean isAdjustmentIdsNotUnique(List<Adjustment> adjustments) {
    Map<String, Long> ids = adjustments.stream()
      .filter(adjustment -> StringUtils.isNotEmpty(adjustment.getId()))
      .collect(Collectors.groupingBy(Adjustment::getId, Collectors.counting()));

    return ids.entrySet().stream().anyMatch(entry -> entry.getValue() > 1);
  }

  public void validateBeforeApproval(Invoice invoice, List<InvoiceLine> lines) {
    checkVendorHasAccountingCode(invoice);
    validateInvoiceTotals(invoice);
    verifyInvoiceLineNotEmpty(lines);
    validateInvoiceLineFundDistributions(lines);
    validateAdjustments(adjustmentsService.getNotProratedAdjustments(invoice));
  }

  private void checkVendorHasAccountingCode(Invoice invoice) {
    // accounting code is erpCode from organization record
    if (Boolean.TRUE.equals(invoice.getExportToAccounting()) && StringUtils.isEmpty(invoice.getAccountingCode())) {
      throw new HttpException(400, ACCOUNTING_CODE_NOT_PRESENT);
    }
  }

  private void verifyInvoiceLineNotEmpty(List<InvoiceLine> invoiceLines) {
    if (invoiceLines.isEmpty()) {
      throw new HttpException(400, NO_INVOICE_LINES_ERROR_MSG);
    }
  }

  private void validateInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines) {
    for (InvoiceLine line : invoiceLines){
      if (CollectionUtils.isEmpty(line.getFundDistributions())) {
        throw new HttpException(400, FUND_DISTRIBUTIONS_NOT_PRESENT);
      }
      validateFundDistributions(line.getTotal(), line.getFundDistributions());
    }
  }


  public void validateAdjustments(List<Adjustment> adjustments) {
    for (Adjustment adjustment : adjustments) {
      if (CollectionUtils.isEmpty(adjustment.getFundDistributions())) {
        throw new HttpException(400, ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT);
      }
      validateFundDistributions(adjustment.getValue(), adjustment.getFundDistributions());
    }
  }

  public void validateFundDistributions(Double subtotal, List<FundDistribution> fundDistributions) {
    if (subtotal != null && CollectionUtils.isNotEmpty(fundDistributions)) {
      if (subtotal == 0d) {
        validateZeroPrice(fundDistributions);
        return;
      }
      BigDecimal remainingPercent = ONE_HUNDRED_PERCENT;
      for (FundDistribution fundDistribution : fundDistributions) {
        FundDistribution.DistributionType dType = fundDistribution.getDistributionType();
        Double value = fundDistribution.getValue();
        if (dType == PERCENTAGE) {
          remainingPercent = remainingPercent.subtract(BigDecimal.valueOf(fundDistribution.getValue()));
        } else {
          BigDecimal percentageValue = BigDecimal.valueOf(value)
            .divide(BigDecimal.valueOf(subtotal), 15, HALF_EVEN)
            .movePointRight(2);
          remainingPercent = remainingPercent.subtract(percentageValue);
        }
      }
      checkRemainingPercentMatchesToZero(remainingPercent, subtotal);
    }
  }

  private void validateZeroPrice(List<FundDistribution> fdList) {
   FundDistribution.DistributionType firstFdType = fdList.get(0).getDistributionType();
    if (fdList.stream().skip(1).anyMatch(fd -> fd.getDistributionType() != firstFdType))
      throw new HttpException(422, CANNOT_MIX_TYPES_FOR_ZERO_PRICE);
    if (firstFdType == AMOUNT) {
      for (FundDistribution fd : fdList) {
        if (fd.getValue() != 0)
          throwExceptionWithIncorrectAmount(ZERO_REMAINING_AMOUNT);
      }
    } else {
      BigDecimal remainingPercent = ONE_HUNDRED_PERCENT;
      for (FundDistribution fd : fdList) {
        remainingPercent = remainingPercent.subtract(BigDecimal.valueOf(fd.getValue()));
      }
      checkRemainingPercentMatchesToZero(remainingPercent, 0d);
    }
  }

  private void checkRemainingPercentMatchesToZero(BigDecimal remainingPercent, Double subtotal) {
      BigDecimal epsilon = BigDecimal.valueOf(1e-10);
      if (remainingPercent.abs().compareTo(epsilon) > 0) {
        throwExceptionWithIncorrectAmount(remainingPercent, subtotal);
      }
  }

  private void throwExceptionWithIncorrectAmount(BigDecimal remainingPercent, Double subtotal) {
    BigDecimal total = BigDecimal.valueOf(subtotal);
    BigDecimal remainingAmount = remainingPercent.multiply(total).divide(ONE_HUNDRED_PERCENT, 2, HALF_EVEN);
    throwExceptionWithIncorrectAmount(remainingAmount);
  }

  private void throwExceptionWithIncorrectAmount(BigDecimal remainingAmount) {
    throw new HttpException(422,INCORRECT_FUND_DISTRIBUTION_TOTAL, Lists.newArrayList(new Parameter()
      .withKey(REMAINING_AMOUNT_FIELD)
      .withValue(remainingAmount.toString())));
  }

}
