package org.folio.rest.impl;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_LIST_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINE_ID_PATH;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getInvoiceLineCreations;
import static org.folio.rest.impl.MockServer.getInvoiceLineUpdates;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.util.Collections;

import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class InvoiceLinesProratedAdjustmentsTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceLinesProratedAdjustmentsTest.class);

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testCreateFirstLineForInvoiceWithOneAdj(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Creating first line for invoice with one prorated adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    Adjustment invoiceAdjustment = createAdjustment(prorate, type, 15d);
    invoice.withAdjustments(Collections.singletonList(invoiceAdjustment));
    addMockEntry(INVOICES, invoice);

    // Prepare request body
    InvoiceLine invoiceLineBody = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withQuantity(10);

    // Send update request
    InvoiceLine invoiceLine = verifySuccessPost(INVOICE_LINES_PATH, invoiceLineBody).as(InvoiceLine.class);

    // Verification
    verifyInvoiceLineUpdateCalls(0);
    verifyInvoiceSummaryUpdateEvent(1);
    compareRecordWithSentToStorage(invoiceLine);

    // Line adjustment value is always the same as invoice one regardless of type
    double expectedAdjValue = 15d;

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == Adjustment.Type.AMOUNT) ? 15d : 3.75d;

    assertThat(invoiceLine.getAdjustments(), hasSize(1));
    assertThat(invoiceLine.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = invoiceLine.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjValue));
  }

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testUpdateLineForInvoiceWithOneAdj(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating line for invoice with only one line and one prorated adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    Adjustment invoiceAdjustment = createAdjustment(prorate, type, 15d);
    invoice.withAdjustments(Collections.singletonList(invoiceAdjustment));
    addMockEntry(INVOICES, invoice);

    InvoiceLine lineData = getMockInvoiceLine(invoice.getId()).withSubTotal(15d).withQuantity(5);
    addMockEntry(INVOICE_LINES, lineData);

    // Prepare request body
    InvoiceLine invoiceLineBody = copyObject(lineData).withSubTotal(25d).withQuantity(10);;
    String lineId = invoiceLineBody.getId();

    // Send update request
    verifySuccessPut(String.format(INVOICE_LINE_ID_PATH, lineId), invoiceLineBody);

    // Verification
    verifyInvoiceLineUpdateCalls(1);
    verifyInvoiceSummaryUpdateEvent(1);

    InvoiceLine lineToStorage = getLineToStorageById(lineId);
    assertThat(lineToStorage.getAdjustments(), hasSize(1));

    // Line adjustment value is always the same as invoice one regardless of type
    double expectedAdjValue = 15d;

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == Adjustment.Type.AMOUNT) ? 15d : 3.75d;

    assertThat(lineToStorage.getAdjustments(), hasSize(1));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjValue));
  }

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testDeleteLineForInvoiceWithOneAdj(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Deleting line for invoice with 2 lines and one prorated adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    Adjustment invoiceAdjustment = createAdjustment(prorate, type, 15d);
    invoice.withAdjustments(Collections.singletonList(invoiceAdjustment));
    addMockEntry(INVOICES, invoice);

    InvoiceLine line1 = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withQuantity(10);
    addMockEntry(INVOICE_LINES, line1);
    InvoiceLine line2 = getMockInvoiceLine(invoice.getId()).withSubTotal(15d).withQuantity(5);
    addMockEntry(INVOICE_LINES, line2);

    // Send update request
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, line2.getId()), "", 204);

    // Verification
    verifyInvoiceLineUpdateCalls(1);
    verifyInvoiceSummaryUpdateEvent(1);

    InvoiceLine lineToStorage = getLineToStorageById(line1.getId());
    assertThat(lineToStorage.getAdjustments(), hasSize(1));

    // Line adjustment value is always the same as invoice one regardless of type
    double expectedAdjValue = 15d;

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == Adjustment.Type.AMOUNT) ? 15d : 3.75d;

    assertThat(lineToStorage.getAdjustments(), hasSize(1));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjValue));
  }

  private InvoiceLine getLineToStorageById(String invoiceLineId) {
    return getInvoiceLineUpdates().stream()
      .filter(line -> invoiceLineId.equals(line.getString("id")))
      .findAny()
      .map(obj -> obj.mapTo(InvoiceLine.class))
      .get();
  }

  private InvoiceLine getMockInvoiceLine(String invoiceId) {
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINES_LIST_PATH).mapTo(InvoiceLineCollection.class)
      .getInvoiceLines()
      .get(0)
      .withId(randomUUID().toString())
      .withInvoiceId(invoiceId);
    invoiceLine.getAdjustments().clear();

    return invoiceLine;
  }

  private void verifyInvoiceLineAdjustmentCommon(Adjustment invoiceAdjustment, Adjustment lineAdjustment) {
    assertThat(lineAdjustment.getId(), nullValue());
    assertThat(lineAdjustment.getAdjustmentId(), equalTo(invoiceAdjustment.getId()));
    assertThat(lineAdjustment.getDescription(), equalTo(invoiceAdjustment.getDescription()));
    assertThat(lineAdjustment.getFundDistributions(), equalTo(invoiceAdjustment.getFundDistributions()));
    assertThat(lineAdjustment.getProrate(), equalTo(invoiceAdjustment.getProrate()));
    assertThat(lineAdjustment.getRelationToTotal(), equalTo(invoiceAdjustment.getRelationToTotal()));
    assertThat(lineAdjustment.getType(), equalTo(invoiceAdjustment.getType()));
  }

  private void verifyInvoiceLineUpdateCalls(int msgQty) {
    logger.debug("Verifying calls to update invoice line");
    // Wait until message is registered
    await().atLeast(50, MILLISECONDS)
      .atMost(1, SECONDS)
      .until(MockServer::getInvoiceLineUpdates, Matchers.hasSize(msgQty));
  }

  private void compareRecordWithSentToStorage(InvoiceLine invoiceLine) {
    // Verify that invoice line sent to storage is the same as in response
    assertThat(getInvoiceLineCreations(), Matchers.hasSize(1));
    InvoiceLine invoiceLineToStorage = getInvoiceLineCreations().get(0).mapTo(InvoiceLine.class);
    assertThat(invoiceLine, equalTo(invoiceLineToStorage));
  }
}
