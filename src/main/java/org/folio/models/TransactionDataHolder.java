package org.folio.models;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.money.convert.CurrencyConversion;

import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public abstract class TransactionDataHolder {

  private final Invoice invoice;
  private final List<InvoiceLine> invoiceLines;
  private FiscalYear fiscalYear;
  private CurrencyConversion currencyConversion;

  public TransactionDataHolder(Invoice invoice, List<InvoiceLine> invoiceLines) {
    this.invoice = invoice;
    this.invoiceLines = invoiceLines;
  }

  public TransactionDataHolder withFiscalYear(FiscalYear fiscalYear) {
    this.fiscalYear = fiscalYear;
    return this;
  }

  public TransactionDataHolder withCurrencyConversion(CurrencyConversion currencyConversion) {
    this.currencyConversion = currencyConversion;
    return this;
  }

  public List<String> getFundIds() {
    return Stream.concat(invoice.getAdjustments().stream().flatMap(adjustment -> adjustment.getFundDistributions().stream()),
      invoiceLines.stream().flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()))
      .map(FundDistribution::getFundId)
      .collect(Collectors.toList());
  }

  public List<InvoiceLine> getInvoiceLines() {
    return invoiceLines;
  }

  public Invoice getInvoice() {
    return invoice;
  }

  public FiscalYear getFiscalYear() {
    return fiscalYear;
  }

  public String getCurrency() {
    return fiscalYear.getCurrency();
  }

  public CurrencyConversion getCurrencyConversion() {
    return currencyConversion;
  }

  public List<FundDistribution> getFundDistributions() {
    Stream<FundDistribution> invoiceLinesFDsStream = invoiceLines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream());
    Stream<FundDistribution> invoiceFDsStream = invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream());
    return Stream.concat(invoiceFDsStream, invoiceLinesFDsStream).collect(toList());
  }

  public List<Transaction> toTransactions() {
    List<Transaction> transactions = buildTransactionsFromInvoiceLines();
    transactions.addAll(buildTransactionsFromAdjustments());
    return transactions;
  }

  abstract Transaction buildTransaction(FundDistribution fundDistribution, InvoiceLine invoiceLine);

  abstract Transaction buildTransaction(FundDistribution fundDistribution, Adjustment adjustment);

  private List<Transaction> buildTransactionsFromInvoiceLines() {

    return getInvoiceLines().stream()
      .flatMap(line -> line.getFundDistributions().stream()
        .map(fundDistribution -> buildTransaction(fundDistribution, line)))
      .collect(toList());
  }

  protected Transaction buildBaseTransaction(FundDistribution fundDistribution) {
    return new Transaction()
      .withSource(Transaction.Source.INVOICE)
      .withCurrency(getCurrency())
      .withFiscalYearId(getFiscalYear().getId())
      .withSourceInvoiceId(getInvoice().getId())
      .withFromFundId(fundDistribution.getFundId());
  }

  private List<Transaction> buildTransactionsFromAdjustments() {
    return getInvoice().getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream()
        .map(fundDistribution -> buildTransaction(fundDistribution, adjustment)))
      .collect(toList());
  }

}
