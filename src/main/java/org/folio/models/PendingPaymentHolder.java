package org.folio.models;

import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;

import java.util.List;

import javax.money.MonetaryAmount;

import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class PendingPaymentHolder extends TransactionDataHolder {

  public PendingPaymentHolder(Invoice invoice, List<InvoiceLine> invoiceLines) {
    super(invoice, invoiceLines);
  }

  protected Transaction buildTransaction(FundDistribution fundDistribution, InvoiceLine invoiceLine)  {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), getInvoice().getCurrency()).with(getCurrencyConversion());
    AwaitingPayment awaitingPayment = null;

    if (fundDistribution.getEncumbrance() != null) {
      awaitingPayment = new AwaitingPayment()
        .withEncumbranceId(fundDistribution.getEncumbrance())
        .withReleaseEncumbrance(invoiceLine.getReleaseEncumbrance());
    }
    return buildBaseTransaction(fundDistribution)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAwaitingPayment(awaitingPayment)
      .withAmount(convertToDoubleWithRounding(amount))
      .withSourceInvoiceLineId(invoiceLine.getId());

  }

  protected Transaction buildTransaction(FundDistribution fundDistribution, Adjustment adjustment) {
    MonetaryAmount amount = getAdjustmentFundDistributionAmount(fundDistribution, adjustment, getInvoice()).with(getCurrencyConversion());

    return buildBaseTransaction(fundDistribution)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(convertToDoubleWithRounding(amount));
  }

}
