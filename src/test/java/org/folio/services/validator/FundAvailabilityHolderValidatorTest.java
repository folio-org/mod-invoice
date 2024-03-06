package org.folio.services.validator;

import static org.folio.invoices.utils.ErrorCodes.FUND_CANNOT_BE_PAID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.Budget;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Ledger;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.jaxrs.model.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;


public class FundAvailabilityHolderValidatorTest {

  @InjectMocks
  private FundAvailabilityHolderValidator fundAvailabilityValidator;


  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void checkEnoughMoneyInBudgetShouldThrowFundCannotBePaidIfTransactionsAmountDifferenceGreaterThanBudgetRemainingAmount() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    Transaction existingTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(50d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Transaction newTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(60d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Fund fund = new Fund()
      .withId(fundId)
      .withName("TestFund")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(59d)
      .withTotalFunding(59d)
      .withAvailable(9d)
      .withUnavailable(50d)
      .withAwaitingPayment(50D)
      .withAllowableExpenditure(100d);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();
    InvoiceWorkflowDataHolder holder = new InvoiceWorkflowDataHolder().withFund(fund)
      .withExistingTransaction(existingTransaction)
            .withNewTransaction(newTransaction)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(new FiscalYear().withId(fiscalYearId).withCurrency("USD"));
    holders.add(holder);

    HttpException httpException = assertThrows(HttpException.class, () -> fundAvailabilityValidator.validate(holders));
    assertEquals(422, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    assertEquals(FUND_CANNOT_BE_PAID.getCode(), error.getCode());
    assertEquals(Collections.singletonList("FC").toString(), error.getParameters().get(0).getValue());
  }


  @Test
  void checkEnoughMoneyInBudgetShouldPassIfTransactionsAmountDifferenceLessThanBudgetRemainingAmount() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    Transaction existingTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(50d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Transaction newTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(60d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(59d)
      .withAvailable(9d)
      .withTotalFunding(59d)
      .withUnavailable(50d)
      .withAwaitingPayment(50D)
      .withAllowableExpenditure(150d);

    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);


    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(true);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();
    InvoiceWorkflowDataHolder holder = new InvoiceWorkflowDataHolder().withExistingTransaction(existingTransaction)
            .withNewTransaction(newTransaction)
            .withBudget(budget)
            .withFund(fund)
            .withRestrictExpenditures(ledger.getRestrictExpenditures())
            .withFiscalYear(new FiscalYear().withId(fiscalYearId).withCurrency("USD"));
    holders.add(holder);

    assertDoesNotThrow(() -> fundAvailabilityValidator.validate(holders));

  }

  @Test
  void checkEnoughMoneyInBudgetShouldPassIfTransactionsAmountDifferenceGreaterThanBudgetRemainingAmountAndBudgetAllowableExpenditureIsNull() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    Transaction existingTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(50d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Transaction newTransaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAmount(60d)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withCurrency("USD");

    Fund fund = new Fund()
      .withId(fundId)
      .withName("TestFund")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(59d)
      .withTotalFunding(59d)
      .withAvailable(0d)
      .withUnavailable(50d)
      .withAwaitingPayment(50D)
      .withAllowableExpenditure(null);


    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();

    InvoiceWorkflowDataHolder holder = new InvoiceWorkflowDataHolder()
            .withFund(fund)
            .withExistingTransaction(existingTransaction)
            .withNewTransaction(newTransaction)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(new FiscalYear().withId(fiscalYearId).withCurrency("USD"));
    holders.add(holder);

    assertDoesNotThrow(() -> fundAvailabilityValidator.validate(holders));

  }

  @Test
  void shouldCountAdjustmentsFundDistributionsDuringBudgetRemainingAmountValidation() {

    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    FiscalYear fiscalYear = new FiscalYear()
      .withCurrency("USD")
      .withId(fiscalYearId);

    Fund fund = new Fund()
      .withId(fundId)
      .withName("TestFund")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(260d)
      .withTotalFunding(260d)
      .withAvailable(210d)
      .withUnavailable(50d)
      .withAwaitingPayment(50d)
      .withAllowableExpenditure(100d);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();
    Transaction adjustmentPendingPayment = new Transaction()
            .withAmount(20d)
            .withCurrency("USD");

    Transaction linePendingPayment = new Transaction().withAmount(200d)
            .withCurrency("USD");

    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
            .withFund(fund)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(fiscalYear)
            .withNewTransaction(linePendingPayment);

    InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
            .withFund(fund)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(fiscalYear)
            .withNewTransaction(adjustmentPendingPayment);

    holders.add(holder1);
    holders.add(holder2);

    HttpException httpException = assertThrows(HttpException.class, () -> fundAvailabilityValidator.validate(holders));
    assertEquals(422, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    assertEquals(FUND_CANNOT_BE_PAID.getCode(), error.getCode());
    assertEquals(Collections.singletonList("FC").toString(), error.getParameters().get(0).getValue());

  }

  @Test
  void shouldPassValidationWhenBudgetRestrictedAndAmountGreaterThanAvailableAndRequiredAmountEncumbered() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    FiscalYear fiscalYear = new FiscalYear()
            .withCurrency("USD")
            .withId(fiscalYearId);

    Fund fund = new Fund()
      .withId(fundId)
      .withName("TestFund")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget = new Budget()
            .withId(budgetId)
            .withFiscalYearId(fiscalYearId)
            .withFundId(fundId)
            .withAllocated(260d)
            .withTotalFunding(260d)
            .withAvailable(20d)
            .withUnavailable(240d)
            .withEncumbered(200d)
            .withAwaitingPayment(40d)
            .withAllowableExpenditure(100d);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();
    Transaction adjustmentPendingPayment = new Transaction()
            .withAmount(20d)
            .withCurrency("USD");

    Transaction encumbrance = new Transaction()
            .withId(UUID.randomUUID().toString())
            .withAmount(200d)
            .withCurrency("USD");

    Transaction linePendingPayment = new Transaction().withAmount(200d)
            .withAwaitingPayment(new AwaitingPayment().withEncumbranceId(encumbrance.getId()).withReleaseEncumbrance(false))
            .withCurrency("USD");

    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
            .withFund(fund)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(fiscalYear)
            .withNewTransaction(linePendingPayment)
            .withEncumbrance(encumbrance);

    InvoiceWorkflowDataHolder holder2 = new InvoiceWorkflowDataHolder()
            .withFund(fund)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(fiscalYear)
            .withNewTransaction(adjustmentPendingPayment);

    holders.add(holder1);
    holders.add(holder2);

    assertDoesNotThrow(()-> fundAvailabilityValidator.validate(holders));

  }

  @Test
  void shouldPassValidationWhenBudgetRestrictedAndFinalExpendedValueGreaterThenMaxBudgetExpended() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    FiscalYear fiscalYear = new FiscalYear()
            .withCurrency("USD")
            .withId(fiscalYearId);
    Fund fund = new Fund()
      .withId(fundId)
      .withName("TestFund")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget = new Budget()
            .withId(budgetId)
            .withFiscalYearId(fiscalYearId)
            .withFundId(fundId)
            .withAllocated(260d)
            .withTotalFunding(260d)
            .withAvailable(0d)
            .withUnavailable(290d)
            .withEncumbered(250d)
            .withAwaitingPayment(30d)
            .withExpenditures(10d)
            .withAllowableExpenditure(100d)
            .withAllowableExpenditure(110d);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();

    Transaction encumbrance = new Transaction()
            .withId(UUID.randomUUID().toString())
            .withAmount(250d)
            .withCurrency("USD");

    Transaction linePendingPayment = new Transaction().withAmount(247d)
            .withAwaitingPayment(new AwaitingPayment().withEncumbranceId(encumbrance.getId()).withReleaseEncumbrance(false))
            .withCurrency("USD");

    InvoiceWorkflowDataHolder holder = new InvoiceWorkflowDataHolder()
            .withFund(fund)
            .withBudget(budget)
            .withRestrictExpenditures(true)
            .withFiscalYear(fiscalYear)
            .withNewTransaction(linePendingPayment)
            .withEncumbrance(encumbrance);


    holders.add(holder);
    HttpException httpException = assertThrows(HttpException.class, () -> fundAvailabilityValidator.validate(holders));

    assertEquals(422, httpException.getCode());
    Error error = httpException.getErrors().getErrors().get(0);
    assertEquals(FUND_CANNOT_BE_PAID.getCode(), error.getCode());
    assertEquals(Collections.singletonList("FC").toString(), error.getParameters().get(0).getValue());
  }

  @Test
  void shouldPassValidationWhenBudgetRestrictedAndInvoiceApproveLessThanOrdersEncumbered() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();
    FiscalYear fiscalYear = new FiscalYear()
      .withCurrency("USD")
      .withId(fiscalYearId);

    Fund fund = new Fund()
      .withId(fundId)
      .withName("TestFund")
      .withLedgerId(ledgerId)
      .withCode("FC")
      .withFundStatus(Fund.FundStatus.ACTIVE);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withAllocated(100d)
      .withTotalFunding(100d)
      .withAvailable(2d)
      .withUnavailable(98d)
      .withEncumbered(98d)
      .withAwaitingPayment(0d)
      .withAllowableExpenditure(100d);

    List<InvoiceWorkflowDataHolder> holders = new ArrayList<>();

    Transaction encumbrance = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withAmount(98d)
      .withCurrency("USD");

    Transaction linePendingPayment = new Transaction().withAmount(96.99d)
      .withAwaitingPayment(new AwaitingPayment().withEncumbranceId(encumbrance.getId()).withReleaseEncumbrance(true))
      .withCurrency("USD");

    InvoiceWorkflowDataHolder holder1 = new InvoiceWorkflowDataHolder()
      .withFund(fund)
      .withBudget(budget)
      .withRestrictExpenditures(true)
      .withFiscalYear(fiscalYear)
      .withNewTransaction(linePendingPayment)
      .withEncumbrance(encumbrance);

    holders.add(holder1);

    assertDoesNotThrow(()-> fundAvailabilityValidator.validate(holders));

  }
}
