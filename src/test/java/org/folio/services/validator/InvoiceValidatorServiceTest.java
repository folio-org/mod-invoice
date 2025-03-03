package org.folio.services.validator;

import io.vertx.core.Future;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.finance.fiscalyear.FiscalYearService;
import org.folio.services.order.OrderLineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.folio.invoices.utils.ErrorCodes.PO_LINE_PAYMENT_STATUS_NOT_PRESENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class InvoiceValidatorServiceTest {
  private AutoCloseable mockitoMocks;
  private InvoiceValidator invoiceValidator;

  @Mock
  AdjustmentsService adjustmentsService;
  @Mock
  OrderLineService orderLineService;
  @Mock
  FiscalYearService fiscalYearService;
  @Mock
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    invoiceValidator = new InvoiceValidator(adjustmentsService, orderLineService, fiscalYearService);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  public void validatePoLinePaymentStatusParameterTest() {
    String poLineId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Invoice invoice = new Invoice()
      .withFiscalYearId(fiscalYearId)
      .withStatus(Invoice.Status.APPROVED);
    InvoiceLine invoiceLine = new InvoiceLine()
      .withReleaseEncumbrance(true)
      .withPoLineId(poLineId);
    List<InvoiceLine> invoiceLines = List.of(invoiceLine);
    Invoice invoiceFromStorage = new Invoice()
      .withStatus(Invoice.Status.OPEN);
    PoLine poLine = new PoLine()
      .withId(poLineId)
      .withPaymentStatus(PoLine.PaymentStatus.AWAITING_PAYMENT);
    List<PoLine> poLines = List.of(poLine);
    FiscalYear fiscalYear = new FiscalYear()
      .withPeriodEnd(new Date(new Date().getTime() - 1000));

    when(fiscalYearService.getFiscalYear(eq(fiscalYearId), eq(requestContext)))
      .thenReturn(Future.succeededFuture(fiscalYear));
    when(orderLineService.getPoLinesByIdAndQuery(anyList(), any(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLines));

    Future<Void> result = invoiceValidator.validatePoLinePaymentStatusParameter(invoice, invoiceLines, invoiceFromStorage,
      null, requestContext);

    assertTrue(result.failed());
    assertEquals(PO_LINE_PAYMENT_STATUS_NOT_PRESENT.getCode(),
      ((HttpException)result.cause()).getErrors().getErrors().get(0).getCode());
  }

}
