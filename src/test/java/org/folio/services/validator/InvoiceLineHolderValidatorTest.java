package org.folio.services.validator;

import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_IDS_NOT_UNIQUE;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_ADD_ADJUSTMENTS;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_ADJUSTMENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.junit.jupiter.api.Test;

public class InvoiceLineHolderValidatorTest {

  private final InvoiceLineValidator invoiceLineValidator = new InvoiceLineValidator();

  @Test
  void validateLineAdjustmentsOnUpdate() {

    Invoice invoice = new Invoice();
    List<Adjustment> invoiceAdjustments = new ArrayList<>();
    String deletedId = UUID.randomUUID().toString();
    invoiceAdjustments.add(new Adjustment().withProrate(Adjustment.Prorate.BY_AMOUNT).withId(deletedId));
    invoice.setAdjustments(invoiceAdjustments);

    InvoiceLine invoiceLine = new InvoiceLine();
    Adjustment adjustment = new Adjustment().withAdjustmentId(UUID.randomUUID().toString());
    List<Adjustment> invoiceLineAdjustments = new ArrayList<>();
    invoiceLineAdjustments.add(adjustment);
    invoiceLineAdjustments.add(adjustment);
    invoiceLine.setAdjustments(invoiceLineAdjustments);

    Errors expectedErrors = new Errors().withTotalRecords(3);
    expectedErrors.getErrors().add(ADJUSTMENT_IDS_NOT_UNIQUE.toError());
    expectedErrors.getErrors().add(CANNOT_DELETE_ADJUSTMENTS.toError()
      .withParameters(Collections.singletonList(new Parameter()
        .withKey("adjustmentId")
        .withValue(deletedId)
        )
      )
    );

    expectedErrors.getErrors().add(CANNOT_ADD_ADJUSTMENTS.toError()
      .withParameters(Collections.singletonList(new Parameter()
        .withKey("adjustmentId")
        .withValue(adjustment.getAdjustmentId())
      ))
    );

    HttpException exception = assertThrows(HttpException.class, () -> invoiceLineValidator.validateLineAdjustmentsOnUpdate(invoiceLine, invoice));

    assertEquals(422, exception.getCode());
    assertEquals(expectedErrors, exception.getErrors());
  }

  @Test
  void validateLineAdjustmentsOnCreate() {
    Invoice invoice = new Invoice();
    List<Adjustment> invoiceAdjustments = new ArrayList<>();
    String deletedId = UUID.randomUUID().toString();
    invoiceAdjustments.add(new Adjustment().withProrate(Adjustment.Prorate.BY_AMOUNT).withId(deletedId));
    invoice.setAdjustments(invoiceAdjustments);

    InvoiceLine invoiceLine = new InvoiceLine();
    Adjustment adjustment = new Adjustment().withAdjustmentId(UUID.randomUUID().toString());
    List<Adjustment> invoiceLineAdjustments = new ArrayList<>();
    invoiceLineAdjustments.add(adjustment);
    invoiceLineAdjustments.add(adjustment);
    invoiceLine.setAdjustments(invoiceLineAdjustments);

    Errors expectedErrors = new Errors().withTotalRecords(2);
    expectedErrors.getErrors().add(ADJUSTMENT_IDS_NOT_UNIQUE.toError());

    expectedErrors.getErrors().add(CANNOT_ADD_ADJUSTMENTS.toError()
      .withParameters(Collections.singletonList(new Parameter()
        .withKey("adjustmentId")
        .withValue(adjustment.getAdjustmentId())
      ))
    );

    HttpException exception = assertThrows(HttpException.class, () -> invoiceLineValidator.validateLineAdjustmentsOnCreate(invoiceLine, invoice));

    assertEquals(422, exception.getCode());
    assertEquals(expectedErrors, exception.getErrors());
  }
}
