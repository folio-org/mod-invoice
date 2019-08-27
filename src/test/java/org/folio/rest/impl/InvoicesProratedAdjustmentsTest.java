package org.folio.rest.impl;

import static java.util.UUID.randomUUID;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_ID_PATH;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getInvoiceLineUpdates;
import static org.folio.rest.impl.MockServer.getInvoiceUpdates;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.NOT_PRORATED;
import static org.folio.rest.jaxrs.model.Adjustment.Type.AMOUNT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class InvoicesProratedAdjustmentsTest extends ApiTestBase {

  private static final Logger logger = LoggerFactory.getLogger(InvoicesProratedAdjustmentsTest.class);

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testUpdateInvoiceWithoutLinesAddingAdjustment(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating invoice without lines ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(prorate, type, 15d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), empty());

    // Prorated adjustment is not applied if no lines available
    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(0d));

    Adjustment adjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(adjustment.getId(), not(isEmptyOrNullString()));
  }

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testUpdateInvoiceWithOneLineAddingAdjustment(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating invoice with one line adding one prorated adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine line = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withQuantity(10);
    // Add non prorated adjustment
    line.setAdjustments(Collections.singletonList(createAdjustment(NOT_PRORATED, AMOUNT, 10d)));
    addMockEntry(INVOICE_LINES, line);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(prorate, type, 15d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(1));

    // Line adjustment value is always the same as invoice one regardless of type
    double expectedProratedAdjValue = 15d;

    /*
     * Calculated prorated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == AMOUNT) ? 25d : 13.75d;

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    InvoiceLine lineToStorage = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
    assertThat(lineToStorage.getAdjustments(), hasSize(2));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments()
      .stream()
      .filter(adj -> adj.getProrate() != NOT_PRORATED)
      .findAny()
      .orElse(null);

    assertThat(lineAdjustment, notNullValue());
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedProratedAdjValue));
  }

  @Test
  @Parameters({
    "AMOUNT",
    "PERCENTAGE"
  })
  public void testUpdateInvoiceWithTwoLinesAddingAdjustmentByLines(Adjustment.Type type) {
    logger.info("=== Updating invoice with two lines adding adjustment by lines ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(5d);
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(15d);
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(Adjustment.Prorate.BY_LINE, type, 9.30d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    /*
     * Total invoice adjustment value is sum of invoice line adjustments and depends on type:
     * "Amount" - this is the same as prorated adjustment value i.e. 9.30$
     * "Percentage" - this is sum of invoice line adjustment totals i.e. 4.65% (9.30% / 2) of each sub total
     */
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(type == AMOUNT ? 9.30d : 0.93d));

    // Adjustment value is invoice prorated adjustment's value divided by number of lines but adjustment total amount is calculated based on subTotal
    double expectedAdjValue = 4.65d;
    getInvoiceLineUpdates().stream()
      .map(json -> json.mapTo(InvoiceLine.class))
      .forEach(line -> {
        assertThat(line.getAdjustments(), hasSize(1));
        Adjustment adj = line.getAdjustments().get(0);
        verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, adj);
        assertThat(adj.getValue(), is(expectedAdjValue));

        double expectedAdjTotal;
        if (type == AMOUNT) {
          // In case of "Amount" type this is the same as adjustment value
          expectedAdjTotal = expectedAdjValue;
        } else {
          if (invoiceLine1.getId().equals(line.getId())) {
            // In case of "Percentage" type this is the percentage of subTotal i.e. 4.65% of 5$ = 0.2325 rounded to 0.23$
            expectedAdjTotal = 0.23d;
          } else {
            // In case of "Percentage" type this is the percentage of subTotal i.e. 4.65% of 15$ = 0.6975 rounded to 0.70$
            expectedAdjTotal = 0.70d;
          }
        }
        assertThat(line.getAdjustmentsTotal(), is(expectedAdjTotal));
      });
  }

  @Test
  @Parameters({
    "AMOUNT",
    "PERCENTAGE"
  })
  public void testUpdateInvoiceWithTwoLinesAddingAdjustmentByAmount(Adjustment.Type type) {
    logger.info("=== Updating invoice with two lines adding adjustment by amount ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d);
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(20d);
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(Adjustment.Prorate.BY_AMOUNT, type, 15d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    // Depending on type either original prorated amount is split across lines adjustments or percent of subtotal is calculated
    double expectedAdjTotal1 = type == AMOUNT ? 5d : 1.5d;
    double expectedAdjValue1 = type == AMOUNT ? 5d : 15d;
    double expectedAdjTotal2 = type == AMOUNT ? 10d : 3d;
    double expectedAdjValue2 = type == AMOUNT ? 10d : 15d;

    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(expectedAdjTotal1 + expectedAdjTotal2));

    InvoiceLine lineToStorage1 = getLineToStorageById(invoiceLine1.getId());
    assertThat(lineToStorage1.getAdjustments(), hasSize(1));
    assertThat(lineToStorage1.getAdjustmentsTotal(), is(expectedAdjTotal1));

    Adjustment lineAdjustment1 = lineToStorage1.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment1);
    assertThat(lineAdjustment1.getValue(), is(expectedAdjValue1));

    InvoiceLine lineToStorage2 = getLineToStorageById(invoiceLine2.getId());
    assertThat(lineToStorage2.getAdjustments(), hasSize(1));
    assertThat(lineToStorage2.getAdjustmentsTotal(), is(expectedAdjTotal2));

    Adjustment lineAdjustment2 = lineToStorage2.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment2);
    assertThat(lineAdjustment2.getValue(), is(expectedAdjValue2));
  }

  @Test
  public void testUpdateInvoiceWithTwoGiftLinesAddingAdjustmentByAmount() {
    logger.info("=== Updating invoice with two lines (zero subTotal each) adding adjustment by amount ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(0d);
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(0d);
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(Adjustment.Prorate.BY_AMOUNT, AMOUNT, 15d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    // Depending on type either original prorated amount is split across lines adjustments or percent of subtotal is calculated
    double expectedValue = 7.5d;

    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(15d));

    InvoiceLine lineToStorage1 = getLineToStorageById(invoiceLine1.getId());
    assertThat(lineToStorage1.getAdjustments(), hasSize(1));
    assertThat(lineToStorage1.getAdjustmentsTotal(), is(expectedValue));

    Adjustment lineAdjustment1 = lineToStorage1.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment1);
    assertThat(lineAdjustment1.getValue(), is(expectedValue));

    InvoiceLine lineToStorage2 = getLineToStorageById(invoiceLine2.getId());
    assertThat(lineToStorage2.getAdjustments(), hasSize(1));
    assertThat(lineToStorage2.getAdjustmentsTotal(), is(expectedValue));

    Adjustment lineAdjustment2 = lineToStorage2.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment2);
    assertThat(lineAdjustment2.getValue(), is(expectedValue));
  }

  @Test
  public void testUpdateInvoiceWithTwoLinesAddingAmountAdjustmentByQuantity() {
    logger.info("=== Updating invoice with two lines adding adjustment by quantity ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withQuantity(275);
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withQuantity(725);
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(Adjustment.Prorate.BY_QUANTITY, AMOUNT, 10d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(10d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    InvoiceLine lineToStorage1 = getLineToStorageById(invoiceLine1.getId());
    assertThat(lineToStorage1.getAdjustments(), hasSize(1));
    assertThat(lineToStorage1.getAdjustmentsTotal(), is(2.75d));

    Adjustment lineAdjustment1 = lineToStorage1.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment1);
    assertThat(lineAdjustment1.getValue(), is(2.75d));

    InvoiceLine lineToStorage2 = getLineToStorageById(invoiceLine2.getId());
    assertThat(lineToStorage2.getAdjustments(), hasSize(1));
    assertThat(lineToStorage2.getAdjustmentsTotal(), is(7.25d));

    Adjustment lineAdjustment2 = lineToStorage2.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment2);
    assertThat(lineAdjustment2.getValue(), is(7.25d));
  }

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testUpdateInvoiceWithOneLineAndOneAdjustmentWithoutAdjChange(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating invoice with one line and one prorated adjustment but not touching adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());

    Adjustment adjustment = createAdjustment(prorate, type, 15d).withId(randomUUID().toString());
    invoice.setAdjustments(Collections.singletonList(adjustment));

    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine = getMockInvoiceLine(invoice.getId()).withSubTotal(25d)
      .withQuantity(10)
      .withAdjustments(Collections.singletonList(copyObject(adjustment).withAdjustmentId(adjustment.getId())
        .withId(null)));
    addMockEntry(INVOICE_LINES, invoiceLine);

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), copyObject(invoice).withBillTo(randomUUID().toString()), "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), empty());

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == AMOUNT) ? 15d : 3.75d;

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustments(), equalTo(invoice.getAdjustments()));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));
  }

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testUpdateInvoiceWithOneLineAndOneAdjustmentWithAdjChange(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating invoice with one line and one prorated adjustment - updating adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());

    Adjustment adjustment = createAdjustment(prorate, type, 15d).withId(randomUUID().toString());
    invoice.setAdjustments(Collections.singletonList(adjustment));

    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine = getMockInvoiceLine(invoice.getId()).withSubTotal(25d)
      .withQuantity(10)
      .withAdjustments(Collections.singletonList(copyObject(adjustment).withAdjustmentId(adjustment.getId())
        .withId(null)));
    addMockEntry(INVOICE_LINES, invoiceLine);

    // Send update request
    Invoice body = copyObject(invoice);
    body.getAdjustments().get(0).setValue(25d);

    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), body, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(1));


    // Line adjustment value is always the same as invoice one regardless of type
    double expectedAdjValue = 25d;

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 25% of 25$ = 6.25$
     */
    double expectedAdjTotal = (type == AMOUNT) ? expectedAdjValue : 6.25d;

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    InvoiceLine lineToStorage = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
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
  public void testUpdateInvoiceWithOneLineAndOneAdjustmentAddingSecondAdj(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating invoice with one line and one prorated adjustment - adding second adjustment ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());

    Adjustment adjustment1 = createAdjustment(prorate, type, 15d).withId(randomUUID().toString());
    invoice.setAdjustments(Collections.singletonList(adjustment1));

    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine = getMockInvoiceLine(invoice.getId()).withSubTotal(25d)
      .withQuantity(10)
      .withAdjustments(Collections.singletonList(copyObject(adjustment1).withAdjustmentId(adjustment1.getId())
        .withId(null)));
    addMockEntry(INVOICE_LINES, invoiceLine);

    // Send update request adding one more adjustment
    Adjustment adjustment2 = createAdjustment(prorate, type, 10d);
    Invoice body = copyObject(invoice);
    body.getAdjustments().add(adjustment2);

    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), body, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(1));

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value i.e. 10 + 15 = 25
     * "Percentage" - this is the percentage of subTotal i.e. 25% of 25$ = 6.25$
     */
    double expectedAdjTotal = (type == AMOUNT) ? 25d : 6.25d;

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(2));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment invoiceAdjToStorage1 = null;
    Adjustment invoiceAdjToStorage2 = null;
    for (Adjustment adjustment : invoiceToStorage.getAdjustments()) {
      assertThat(adjustment.getId(), not(isEmptyOrNullString()));
      if (adjustment1.getId().equals(adjustment.getId())) {
        invoiceAdjToStorage1 = adjustment;
        assertThat(invoiceAdjToStorage1.getValue(), is(15d));
      } else {
        invoiceAdjToStorage2 = adjustment;
        assertThat(invoiceAdjToStorage2.getValue(), is(10d));
      }
    }

    // Make sure that both adjustments are found
    assertThat(invoiceAdjToStorage1, notNullValue());
    assertThat(invoiceAdjToStorage2, notNullValue());

    InvoiceLine lineToStorage = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
    assertThat(lineToStorage.getAdjustments(), hasSize(2));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    for (Adjustment adj : lineToStorage.getAdjustments()) {
      if (adjustment1.getId().equals(adj.getAdjustmentId())) {
        verifyInvoiceLineAdjustmentCommon(invoiceAdjToStorage1, adj);
        assertThat(adj.getValue(), is(15d));
      } else {
        verifyInvoiceLineAdjustmentCommon(invoiceAdjToStorage2, adj);
        assertThat(adj.getValue(), is(10d));
      }
    }
  }

  @Test
  @Parameters({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT"
  })
  public void testUpdateInvoiceWithOneLineMakingAdjustmentNotProrated(Adjustment.Prorate prorate, Adjustment.Type type) {
    logger.info("=== Updating invoice with one line and one prorated adjustment - making adjustment \"Not prorated\" ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());

    final double adjValue = 15d;
    Adjustment adjustment = createAdjustment(prorate, type, adjValue).withId(randomUUID().toString());
    invoice.setAdjustments(Collections.singletonList(adjustment));

    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine = getMockInvoiceLine(invoice.getId()).withSubTotal(25d)
      .withAdjustments(Collections.singletonList(copyObject(adjustment).withAdjustmentId(adjustment.getId())
        .withId(null)));
    addMockEntry(INVOICE_LINES, invoiceLine);

    // Send update request making adjustment "Not prorated"
    Invoice body = copyObject(invoice);
    body.getAdjustments().get(0).setProrate(NOT_PRORATED);

    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), body, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(1));

    /*
     * Calculated adjustment value depends of type:
     * "Amount" - this is the same as adjustment value
     * "Percentage" - this is the percentage of subTotal i.e. 15% of 25$ = 3.75$
     */
    double expectedAdjTotal = (type == AMOUNT) ? adjValue : 3.75d;

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    // Adjustments should be deleted
    InvoiceLine lineToStorage = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
    assertThat(lineToStorage.getAdjustments(), empty());
    assertThat(lineToStorage.getAdjustmentsTotal(), is(0d));
  }

  @Test
  public void testUpdateInvoiceWithThreeLinesAddingPercentageAdjustmentByLines() {
    logger.info("=== Updating invoice with three lines adding 5% adjustment by lines ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d);
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d);
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d);
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(Adjustment.Prorate.BY_LINE, Adjustment.Type.PERCENTAGE, 5d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(0.51d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(isEmptyOrNullString()));

    Stream.of(invoiceLine1.getId(), invoiceLine2.getId(), invoiceLine3.getId())
      .forEach(id -> {
        InvoiceLine lineToStorage = getLineToStorageById(id);
        assertThat(lineToStorage.getAdjustments(), hasSize(1));
        assertThat(lineToStorage.getAdjustmentsTotal(), is(0.17d));

        Adjustment lineAdjustment = lineToStorage.getAdjustments().get(0);
        verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
        assertThat(lineAdjustment.getValue(), is(1.666666666666667d));
    });
  }

  private InvoiceLine getLineToStorageById(String invoiceLineId) {
    return getInvoiceLineUpdates().stream()
      .filter(line -> invoiceLineId.equals(line.getString("id")))
      .findAny()
      .map(obj -> obj.mapTo(InvoiceLine.class))
      .get();
  }

  private InvoiceLine getMockInvoiceLine(String invoiceId) {
    InvoiceLine invoiceLine = getMinimalContentInvoiceLine(invoiceId).withId(randomUUID().toString());
    invoiceLine.getAdjustments().clear();

    return invoiceLine;
  }

  private Adjustment createAdjustment(Adjustment.Prorate prorate, Adjustment.Type type, double value) {
    return new Adjustment().withDescription("Test")
      .withProrate(prorate)
      .withType(type)
      .withValue(value);
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
}
