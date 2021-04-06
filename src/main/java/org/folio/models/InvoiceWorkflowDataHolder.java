package org.folio.models;

import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import javax.money.convert.CurrencyConversion;
import java.util.Objects;
import java.util.Optional;

import static org.folio.invoices.utils.HelperUtils.calculateAdjustment;

public class InvoiceWorkflowDataHolder {

    private Budget budget;
    private boolean restrictExpenditures;
    private FiscalYear fiscalYear;
    private InvoiceLine invoiceLine;
    private FundDistribution fundDistribution;
    private Invoice invoice;
    private Transaction encumbrance;
    private Adjustment adjustment;
    private ExpenseClass expenseClass;
    private Transaction newTransaction;
    private Transaction existingTransaction;
    private CurrencyConversion conversion;

    public String getLedgerId() {
        return fund.getLedgerId();
    }

    private Fund fund;

    public Budget getBudget() {
        return budget;
    }

    public InvoiceWorkflowDataHolder withBudget(Budget budget) {
        this.budget = budget;
        return this;
    }

    public FiscalYear getFiscalYear() {
        return fiscalYear;
    }

    public InvoiceWorkflowDataHolder withFiscalYear(FiscalYear fiscalYear) {
        this.fiscalYear = fiscalYear;
        return this;
    }

    public InvoiceLine getInvoiceLine() {
        return invoiceLine;
    }

    public InvoiceWorkflowDataHolder withInvoiceLine(InvoiceLine invoiceLine) {
        this.invoiceLine = invoiceLine;
        return this;
    }

    public FundDistribution getFundDistribution() {
        return fundDistribution;
    }

    public String getFundId() {
        return fundDistribution.getFundId();
    }

    public InvoiceWorkflowDataHolder withFundDistribution(FundDistribution fundDistribution) {
        this.fundDistribution = fundDistribution;
        return this;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public InvoiceWorkflowDataHolder withInvoice(Invoice invoice) {
        this.invoice = invoice;
        return this;
    }

    public Transaction getEncumbrance() {
        return encumbrance;
    }

    public InvoiceWorkflowDataHolder withEncumbrance(Transaction transaction) {
        this.encumbrance = transaction;
        return this;
    }

    public InvoiceWorkflowDataHolder withAdjustment(Adjustment adjustment) {
        this.adjustment = adjustment;
        return this;
    }

    public Adjustment getAdjustment() {
        return adjustment;
    }

    public InvoiceWorkflowDataHolder withFund(Fund fund) {
        this.fund = fund;
        return this;
    }

    public Fund getFund() {
        return fund;
    }

    public boolean isRestrictExpenditures() {
        return restrictExpenditures;
    }

    public InvoiceWorkflowDataHolder withRestrictExpenditures(boolean restrictExpenditures) {
        this.restrictExpenditures = restrictExpenditures;
        return this;
    }

    public String getExpenseClassId() {
        return fundDistribution.getExpenseClassId();
    }

    public InvoiceWorkflowDataHolder withExpenseClass(ExpenseClass expenseClass) {
        this.expenseClass = expenseClass;
        return this;
    }

    public ExpenseClass getExpenseClass() {
        return expenseClass;
    }

    public String getFyCurrency() {
        return fiscalYear.getCurrency();
    }

    public Transaction getNewTransaction() {
        return newTransaction;
    }

    public InvoiceWorkflowDataHolder withNewTransaction(Transaction newTransaction) {
        this.newTransaction = newTransaction;
        return this;
    }

    public Transaction getExistingTransaction() {
        return existingTransaction;
    }

    public InvoiceWorkflowDataHolder withExistingTransaction(Transaction existingTransaction) {
        this.existingTransaction = existingTransaction;
        return this;
    }

    public String getInvoiceLineId() {
        return Optional.ofNullable(invoiceLine).map(InvoiceLine::getId).orElse(null);
    }

    public double getTotal() {
        return Objects.nonNull(invoiceLine) ? invoiceLine.getTotal() : calculateAdjustment(adjustment, invoice);
    }

    public String getInvoiceCurrency() {
        return invoice.getCurrency();
    }

    public CurrencyConversion getConversion() {
        return conversion;
    }

    public InvoiceWorkflowDataHolder withConversion(CurrencyConversion conversion) {
        this.conversion = conversion;
        return this;
    }
}
