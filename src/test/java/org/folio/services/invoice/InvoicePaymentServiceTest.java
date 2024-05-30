package org.folio.services.invoice;

import io.vertx.core.json.JsonObject;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.folio.rest.impl.ApiTestBase.getMockData;

class InvoicePaymentServiceTest {

  @Test
  void testOrderLinePaymentStatusAwaitingPayment() throws IOException {
    CompositePoLine poLine = new JsonObject(
        getMockData("mockdata/compositeOrders/e9496a5c-84d1-4f95-89ad-a764be51ca29.json"))
        .mapTo(CompositePoLine.class);
    Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus = Map.ofEntries(
        Map.entry(poLine, CompositePoLine.PaymentStatus.FULLY_PAID));
    InvoicePaymentService invoicePaymentService = new InvoicePaymentService();
    boolean actual = invoicePaymentService.isPaymentStatusUpdateRequired(compositePoLinesWithStatus, poLine);

    Assertions.assertTrue(actual);
  }

  @Test
  void testOrderLinePaymentStatusPaymentNotRequiredIgnored() throws IOException {
    CompositePoLine poLine = new JsonObject(
        getMockData("mockdata/compositeOrders/443bcf4c-41e9-4a07-8e70-dcc71ca56069.json"))
        .mapTo(CompositePoLine.class);
    Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus = Map.ofEntries(
        Map.entry(poLine, CompositePoLine.PaymentStatus.FULLY_PAID));
    InvoicePaymentService invoicePaymentService = new InvoicePaymentService();
    boolean actual = invoicePaymentService.isPaymentStatusUpdateRequired(compositePoLinesWithStatus, poLine);

    Assertions.assertFalse(actual);
  }
}
