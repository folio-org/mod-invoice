package org.folio.services.validator;

import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_FIELD_CHANGING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.order.OrderLineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class InvoiceValidatorTest {

  private InvoiceValidator validator;

  @BeforeEach
  void setUp() {
    validator = new InvoiceValidator(
      mock(AdjustmentsService.class),
      mock(OrderLineService.class),
      mock(FiscalYearService.class)
    );
  }

  /** Creates a minimal approved invoice with the given subTotal and total. */
  private Invoice approvedInvoice(double subTotal, double total) {
    return new Invoice()
      .withStatus(Invoice.Status.APPROVED)
      .withSubTotal(subTotal)
      .withTotal(total);
  }

  @Test
  void validateInvoiceProtectedFields_throwsWhenSubTotalChanged() {
    var stored  = approvedInvoice(10.00, 10.00);
    var updated = approvedInvoice(99.99, 10.00); // subTotal changed

    var ex = assertThrows(HttpException.class,
      () -> validator.validateInvoiceProtectedFields(updated, stored));

    assertEquals(400, ex.getCode());
    assertEquals(PROHIBITED_FIELD_CHANGING.getCode(),
      ex.getErrors().getErrors().getFirst().getCode());
  }

  @Test
  void validateInvoiceProtectedFields_throwsWhenTotalChanged() {
    var stored  = approvedInvoice(10.00, 10.00);
    var updated = approvedInvoice(10.00, 99.99); // total changed

    var ex = assertThrows(HttpException.class,
      () -> validator.validateInvoiceProtectedFields(updated, stored));

    assertEquals(400, ex.getCode());
    assertEquals(PROHIBITED_FIELD_CHANGING.getCode(),
      ex.getErrors().getErrors().getFirst().getCode());
  }

  @Test
  void validateInvoiceProtectedFields_throwsWhenBothSubTotalAndTotalChanged() {
    var stored  = approvedInvoice(10.00, 10.00);
    var updated = approvedInvoice(50.00, 50.00);

    var ex = assertThrows(HttpException.class,
      () -> validator.validateInvoiceProtectedFields(updated, stored));

    assertEquals(400, ex.getCode());
    assertEquals(PROHIBITED_FIELD_CHANGING.getCode(),
      ex.getErrors().getErrors().getFirst().getCode());
  }

  @Test
  void validateInvoiceProtectedFields_doesNotThrowWhenSubTotalAndTotalUnchanged() {
    var stored  = approvedInvoice(10.00, 12.50);
    var updated = approvedInvoice(10.00, 12.50);

    // Must not throw
    validator.validateInvoiceProtectedFields(updated, stored);
  }

  @ParameterizedTest
  @CsvSource({ "Open", "Reviewed" })
  void validateInvoiceProtectedFields_doesNotThrowForPreApprovalStatuses(String storedStatusName) {
    var storedStatus = Invoice.Status.fromValue(storedStatusName);
    var stored  = new Invoice().withStatus(storedStatus).withSubTotal(10.00).withTotal(10.00);
    var updated = new Invoice().withStatus(storedStatus).withSubTotal(99.99).withTotal(99.99);

    // Pre-approval invoices are not protected — must not throw
    validator.validateInvoiceProtectedFields(updated, stored);
  }
}
