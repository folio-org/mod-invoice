package org.folio.invoices.events.handlers;

import static org.folio.invoices.utils.HelperUtils.INVOICE;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_ID;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.getInvoiceLineSearches;
import static org.folio.rest.impl.MockServer.getInvoiceRetrievals;
import static org.folio.rest.impl.MockServer.getInvoiceUpdates;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.ApiTestBase;
import org.folio.rest.impl.MockServer;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.services.invoice.InvoiceService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;

public class InvoiceSummaryTest extends ApiTestBase {
  private static final Logger logger = LogManager.getLogger(InvoiceSummaryTest.class);

  private static final String TEST_ADDRESS = "testAddress";

  private static Vertx vertx;

  @Autowired
  static InvoiceService invoiceService;

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    ApiTestBase.before();

    vertx = Vertx.vertx();
    vertx.eventBus()
      .consumer(TEST_ADDRESS, new InvoiceSummary(vertx, invoiceService));
  }

  @Test
  public void testUpdateInvoiceTotals() {
    logger.info("=== Test case when invoice summary update is expected due to sub-total change ===");

    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class);
    invoice.setAdjustmentsTotal(5d);
    invoice.setSubTotal(10d);
    invoice.setTotal(15d);
    MockServer.addMockEntry(INVOICES, invoice);

    sendEvent(createBody(OPEN_INVOICE_ID), result -> {
      assertThat(getInvoiceRetrievals(), hasSize(1));
      assertThat(getInvoiceLineSearches(), hasSize(1));
      assertThat(getInvoiceUpdates(), hasSize(1));

      Invoice updatedInvoice = getInvoiceUpdates().get(0)
        .mapTo(Invoice.class);
      assertThat(updatedInvoice.getAdjustmentsTotal(), not(invoice.getAdjustmentsTotal()));
      assertThat(updatedInvoice.getSubTotal(), not(invoice.getSubTotal()));
      assertThat(updatedInvoice.getTotal(), not(invoice.getTotal()));
      assertThat(result.result().body(), equalTo(Response.Status.OK.getReasonPhrase()));
    });
  }

  @Test
  public void testUpdateInvoiceTotalsNoLines() {
    logger.info("=== Test case when invoice summary update is expected when no lines found ===");

    // Setting non zero values which should be resulted to zeros
    Invoice invoice = new Invoice().withId(VALID_UUID)
      .withAdjustmentsTotal(5d)
      .withSubTotal(10d)
      .withTotal(15d)
      .withCurrency("USD");

    sendEvent(createBody(invoice), result -> {
      assertThat(getInvoiceRetrievals(), empty());
      assertThat(getInvoiceLineSearches(), hasSize(1));
      assertThat(getInvoiceUpdates(), hasSize(1));

      Invoice updatedInvoice = getInvoiceUpdates().get(0)
        .mapTo(Invoice.class);
      assertThat(updatedInvoice.getAdjustmentsTotal(), is(0d));
      assertThat(updatedInvoice.getSubTotal(), is(0d));
      assertThat(updatedInvoice.getTotal(), is(0d));
      assertThat(result.result().body(), equalTo(Response.Status.OK.getReasonPhrase()));
    });
  }

  @Test
  public void testUpdateNotRequired() {
    logger.info("=== Test case when no invoice update is expected ===");

    Invoice invoice = new Invoice().withId(VALID_UUID)
      .withAdjustmentsTotal(0d)
      .withSubTotal(0d)
      .withTotal(0d)
      .withCurrency("USD");
    MockServer.addMockEntry(INVOICES, invoice);

    sendEvent(createBody(VALID_UUID), result -> {
      assertThat(getInvoiceRetrievals(), hasSize(1));
      assertThat(getInvoiceLineSearches(), hasSize(1));
      assertThat(getInvoiceUpdates(), empty());
      assertThat(result.result().body(), equalTo(Response.Status.OK.getReasonPhrase()));
    });
  }

  @Test
  public void testNonexistentInvoice() {
    logger.info("=== Test case when invoice is not found ===");
    sendEvent(createBody(ID_DOES_NOT_EXIST), result -> {
      assertThat(getInvoiceRetrievals(), empty());
      assertThat(getInvoiceLineSearches(), empty());
      assertThat(getInvoiceUpdates(), empty());
      assertThat(result, instanceOf(ReplyException.class));
      assertThat(((ReplyException) result).failureCode(), is(404));
    });
  }

  private JsonObject createBody(String id) {
    return new JsonObject().put(INVOICE_ID, id);
  }

  private JsonObject createBody(Invoice invoice) {
    return new JsonObject().put(INVOICE, JsonObject.mapFrom(invoice));
  }

  private void sendEvent(JsonObject data, Handler<AsyncResult<Message<String>>> replyHandler) {
    // Add okapi url header
    DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader(X_OKAPI_URL.getName(), X_OKAPI_URL.getValue());

    vertx.eventBus()
      .request(TEST_ADDRESS, data, deliveryOptions, replyHandler);
  }
}
