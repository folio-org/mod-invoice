package org.folio.rest.impl;

import static org.folio.invoices.utils.ErrorCodes.APPROVED_OR_PAID_INVOICE_DELETE_FORBIDDEN;
import static org.folio.rest.acq.model.Invoice.Status.APPROVED;
import static org.folio.rest.acq.model.Invoice.Status.PAID;

import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Invoice;

import java.util.concurrent.CompletableFuture;

public class RestrictionsHelper {
  public static CompletableFuture<Invoice> checkIfInvoiceDeletionPermitted(Invoice invoice) {
    if (PAID.value().equals(invoice.getStatus().value()) || APPROVED.value().equals(invoice.getStatus().value())) {
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), APPROVED_OR_PAID_INVOICE_DELETE_FORBIDDEN.toError());
    }
    return CompletableFuture.completedFuture(invoice);
  }
}
