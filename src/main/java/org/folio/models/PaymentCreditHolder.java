package org.folio.models;

import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;

import java.util.List;

import javax.money.MonetaryAmount;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class PaymentCreditHolder extends TransactionDataHolder {

  public PaymentCreditHolder(Invoice invoice, List<InvoiceLine> invoiceLines) {
    super(invoice, invoiceLines);
  }

  @Override
  Transaction buildTransaction(FundDistribution fundDistribution, InvoiceLine invoiceLine) {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), getInvoice().getCurrency()).with(getCurrencyConversion());

    Transaction transaction = buildBaseTransaction(fundDistribution);
    if (amount.isNegative()) {
      transaction.setToFundId(fundDistribution.getFundId());
      transaction.setFromFundId(null);
    }
    return transaction
      .withTransactionType(amount.isPositive() ? Transaction.TransactionType.PAYMENT : Transaction.TransactionType.CREDIT)
      .withAmount(convertToDoubleWithRounding(amount.abs()))
      .withSourceInvoiceLineId(invoiceLine.getId());
  }

  @Override
  Transaction buildTransaction(FundDistribution fundDistribution, Adjustment adjustment) {
    MonetaryAmount amount = getAdjustmentFundDistributionAmount(fundDistribution, adjustment, getInvoice()).with(getCurrencyConversion());
    Transaction transaction = buildBaseTransaction(fundDistribution);
    if (amount.isNegative()) {
      transaction.setToFundId(fundDistribution.getFundId());
      transaction.setFromFundId(null);
    }
    return transaction
      .withTransactionType(amount.isPositive() ? Transaction.TransactionType.PAYMENT : Transaction.TransactionType.CREDIT)
      .withAmount(convertToDoubleWithRounding(amount.abs()));
  }


}
