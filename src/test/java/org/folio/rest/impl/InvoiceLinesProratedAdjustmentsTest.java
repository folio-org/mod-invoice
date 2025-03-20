package org.folio.rest.impl;

import static java.util.UUID.randomUUID;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_LIST_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINES_PATH;
import static org.folio.rest.impl.InvoiceLinesApiTest.INVOICE_LINE_ID_PATH;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getInvoiceLineCreations;
import static org.folio.rest.impl.MockServer.getInvoiceLineUpdates;
import static org.folio.rest.impl.MockServer.getInvoiceUpdates;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.BY_AMOUNT;
import static org.folio.rest.jaxrs.model.Adjustment.RelationToTotal.INCLUDED_IN;
import static org.folio.rest.jaxrs.model.Adjustment.Type.PERCENTAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import io.vertx.junit5.VertxExtension;
import java.util.Collections;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(VertxExtension.class)
public class InvoiceLinesProratedAdjustmentsTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(InvoiceLinesProratedAdjustmentsTest.class);

  @ParameterizedTest
  @CsvSource({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT",
    "BY_QUANTITY, PERCENTAGE"
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
    assertThat(getInvoiceLineUpdates(), Matchers.hasSize(0));
    assertThat(getInvoiceUpdates(), Matchers.hasSize(1));
    compareRecordWithSentToStorage(invoiceLine);


    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == Adjustment.Type.AMOUNT) ? 15d : 3.75d;
    assertThat(invoiceLine.getAdjustments(), hasSize(1));
    assertThat(invoiceLine.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = invoiceLine.getAdjustments().getFirst();
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjTotal));
  }

  @ParameterizedTest
  @CsvSource({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT",
    "BY_QUANTITY, PERCENTAGE"
  })
  public void testUpdateLineForInvoiceWithOneAdj(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating line for invoice with only one line and one prorated adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    Adjustment invoiceAdjustment = createAdjustment(prorate, type, 15d);
    invoice.withAdjustments(Collections.singletonList(invoiceAdjustment));
    addMockEntry(INVOICES, invoice);

    InvoiceLine lineData = getMockInvoiceLine(invoice.getId()).withSubTotal(15d).withQuantity(5);
    lineData.setAdjustments(Collections.singletonList(createAdjustment(Adjustment.Prorate.NOT_PRORATED, type, 15d).withAdjustmentId(invoiceAdjustment.getId())));
    addMockEntry(INVOICE_LINES, lineData);

    // Prepare request body
    InvoiceLine invoiceLineBody = copyObject(lineData).withSubTotal(25d).withQuantity(10);
    String lineId = invoiceLineBody.getId();

    // Send update request
    verifySuccessPut(String.format(INVOICE_LINE_ID_PATH, lineId), invoiceLineBody);

    // Verification
    assertThat(getInvoiceLineUpdates(), Matchers.hasSize(1));
    assertThat(getInvoiceUpdates(), Matchers.hasSize(1));

    InvoiceLine lineToStorage = getLineToStorageById(lineId);
    assertThat(lineToStorage.getAdjustments(), hasSize(1));

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == Adjustment.Type.AMOUNT) ? 15d : 3.75d;
    assertThat(lineToStorage.getAdjustments(), hasSize(1));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments().getFirst();
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjTotal));
  }

  @ParameterizedTest
  @CsvSource({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT",
    "BY_QUANTITY, PERCENTAGE"
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
    assertThat(getInvoiceLineUpdates(), Matchers.hasSize(1));
    assertThat(getInvoiceUpdates(), Matchers.hasSize(1));

    InvoiceLine lineToStorage = getLineToStorageById(line1.getId());
    assertThat(lineToStorage.getAdjustments(), hasSize(1));


    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == Adjustment.Type.AMOUNT) ? 15d : 3.75d;

    assertThat(lineToStorage.getAdjustments(), hasSize(1));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments().getFirst();
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjTotal));
  }

  @Test
  public void testCreateInvoiceWithOnePercentageTypeByAmountProrateIncludedByTotalAdjustment() {
    logger.info("=== Creating invoice with one adjustment by amount prorate included by total ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    Adjustment invoiceAdjustment = new Adjustment()
      .withId(UUID.randomUUID().toString())
      .withDescription("VAT")
      .withProrate(BY_AMOUNT)
      .withType(PERCENTAGE)
      .withRelationToTotal(INCLUDED_IN)
      .withValue(7d);
    invoice.withAdjustments(Collections.singletonList(invoiceAdjustment));
    addMockEntry(INVOICES, invoice);

    // Prepare request body
    InvoiceLine invoiceLineBody = getMockInvoiceLine(invoice.getId()).withAdjustmentsTotal(0d).withSubTotal(30d).withQuantity(1);

    // Send create request
    InvoiceLine invoiceLine = verifySuccessPost(INVOICE_LINES_PATH, invoiceLineBody).as(InvoiceLine.class);

    // Verification
    assertThat(getInvoiceLineUpdates(), Matchers.hasSize(0));
    assertThat(getInvoiceUpdates(), Matchers.hasSize(1));
    compareRecordWithSentToStorage(invoiceLine);

    assertThat(invoiceLine.getAdjustments(), hasSize(1));
    assertThat(invoiceLine.getAdjustmentsTotal(), is(1.96d));
    assertThat(invoiceLine.getSubTotal(), is(28.04d));

    Adjustment lineAdjustment = invoiceLine.getAdjustments().getFirst();
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(1.96d));
  }

  @Test
  public void testDeleteInvoiceWithOnePercentageTypeByAmountProrateIncludedByTotalAdjustment() {
    logger.info("=== Deleting invoice with one adjustment by amount prorate included by total ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    Adjustment invoiceAdjustment = new Adjustment()
      .withId(UUID.randomUUID().toString())
      .withDescription("VAT")
      .withProrate(BY_AMOUNT)
      .withType(PERCENTAGE)
      .withRelationToTotal(INCLUDED_IN)
      .withValue(7d);
    invoice.withAdjustments(Collections.singletonList(invoiceAdjustment));
    addMockEntry(INVOICES, invoice);

    InvoiceLine line1 = getMockInvoiceLine(invoice.getId()).withAdjustmentsTotal(0d).withSubTotal(30d).withQuantity(1);
    addMockEntry(INVOICE_LINES, line1);
    InvoiceLine line2 = getMockInvoiceLine(invoice.getId()).withAdjustmentsTotal(0d).withSubTotal(30d).withQuantity(1);
    addMockEntry(INVOICE_LINES, line2);

    // Send delete request
    verifyDeleteResponse(String.format(INVOICE_LINE_ID_PATH, line2.getId()), "", 204);

    // Verification
    assertThat(getInvoiceLineUpdates(), Matchers.hasSize(1));
    assertThat(getInvoiceUpdates(), Matchers.hasSize(1));

    InvoiceLine lineToStorage = getLineToStorageById(line1.getId());
    assertThat(lineToStorage.getAdjustments(), hasSize(1));

    assertThat(lineToStorage.getAdjustments(), hasSize(1));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(1.96d));
    assertThat(lineToStorage.getSubTotal(), is(28.04));

    Adjustment lineAdjustment = lineToStorage.getAdjustments().getFirst();
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(1.96d));
  }

  private InvoiceLine getLineToStorageById(String invoiceLineId) {
    return getInvoiceLineUpdates().stream()
      .filter(line -> invoiceLineId.equals(line.getString("id")))
      .findAny()
      .map(obj -> obj.mapTo(InvoiceLine.class))
      .orElseThrow();
  }

  private InvoiceLine getMockInvoiceLine(String invoiceId) {
    InvoiceLine invoiceLine = getMockAsJson(INVOICE_LINES_LIST_PATH).mapTo(InvoiceLineCollection.class)
      .getInvoiceLines()
      .getFirst()
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
    assertThat(lineAdjustment.getProrate(), equalTo(Adjustment.Prorate.NOT_PRORATED));
    assertThat(lineAdjustment.getRelationToTotal(), equalTo(invoiceAdjustment.getRelationToTotal()));
    assertThat(lineAdjustment.getType(), equalTo(Adjustment.Type.AMOUNT));
  }

  private void compareRecordWithSentToStorage(InvoiceLine invoiceLine) {
    // Verify that invoice line sent to storage is the same as in response
    assertThat(getInvoiceLineCreations(), Matchers.hasSize(1));
    InvoiceLine invoiceLineToStorage = getInvoiceLineCreations().getFirst().mapTo(InvoiceLine.class);
    assertThat(invoiceLine, equalTo(invoiceLineToStorage));
  }
}
