package org.folio.rest.impl;

import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.rest.impl.InvoicesApiTest.INVOICE_ID_PATH;
import static org.folio.rest.impl.InvoicesApiTest.OPEN_INVOICE_SAMPLE_PATH;
import static org.folio.rest.impl.MockServer.addMockEntry;
import static org.folio.rest.impl.MockServer.getInvoiceLineUpdates;
import static org.folio.rest.impl.MockServer.getInvoiceUpdates;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.BY_AMOUNT;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.BY_LINE;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.BY_QUANTITY;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.NOT_PRORATED;
import static org.folio.rest.jaxrs.model.Adjustment.Type.AMOUNT;
import static org.folio.rest.jaxrs.model.Adjustment.Type.PERCENTAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class InvoicesProratedAdjustmentsTest extends ApiTestBase {

  private static final Logger logger = LogManager.getLogger(InvoicesProratedAdjustmentsTest.class);

  @ParameterizedTest
  @CsvSource({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT",
    "BY_QUANTITY, PERCENTAGE"
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
    assertThat(invoiceToStorage.getAdjustmentsTotal(), not(0));

    Adjustment adjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(adjustment.getId(), not(is(emptyOrNullString())));
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

    double expectedProratedAdjValue = (type == AMOUNT) ? 15d : 3.75;

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
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    InvoiceLine lineToStorage = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
    assertThat(lineToStorage.getAdjustments(), hasSize(2));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments()
      .stream()
      .filter(adj -> adj.getProrate() == NOT_PRORATED && isNotEmpty(adj.getAdjustmentId()))
      .findAny()
      .orElse(null);

    assertThat(lineAdjustment, notNullValue());
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedProratedAdjValue));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "AMOUNT",
    "PERCENTAGE"
  })
  public void testUpdateInvoiceWithTwoLinesAddingAdjustmentByLines(Adjustment.Type type) {
    logger.info("=== Updating invoice with two lines adding adjustment by lines ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(5d).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(15d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_LINE, type, 9.30d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    /*
     * Total invoice adjustment value is sum of invoice line adjustments and depends on type:
     * "Amount" - this is the same as prorated adjustment value i.e. 9.30$
     * "Percentage" - (20 * 9.30) / 100
     */
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(type == AMOUNT ? 9.30d : 1.86d));

    // Adjustment value is invoice prorated adjustment's value divided by number of lines but adjustment total amount is calculated based on subTotal
    double expectedAdjValue = (type == AMOUNT) ? 4.65d : 0.93;
    getInvoiceLineUpdates().stream()
      .map(json -> json.mapTo(InvoiceLine.class))
      .forEach(line -> {
        assertThat(line.getAdjustments(), hasSize(1));
        Adjustment adj = line.getAdjustments().get(0);
        verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, adj);
        assertThat(adj.getValue(), is(expectedAdjValue));
        assertThat(line.getAdjustmentsTotal(), is(expectedAdjValue));
      });
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "AMOUNT",
    "PERCENTAGE"
  })
  public void testUpdateInvoiceWithTwoLinesAddingAdjustmentByAmount(Adjustment.Type type) {
    logger.info("=== Updating invoice with two lines adding adjustment by amount ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(20d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, type, 15d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    // Depending on type either original prorated amount is split across lines adjustments or percent of subtotal is calculated
    double expectedAdjTotal1 = type == AMOUNT ? 5d : 1.5d;
    double expectedAdjValue1 = type == AMOUNT ? 5d : 1.5d;
    double expectedAdjTotal2 = type == AMOUNT ? 10d : 3d;
    double expectedAdjValue2 = type == AMOUNT ? 10d : 3d;

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

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(0d).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(0d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, AMOUNT, 15d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(2));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

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
  public void testUpdateInvoiceWithThreeLinesAddingAmountAdjustmentByAmount() {
    logger.info("=== Updating invoice with zero subTotal and three lines (mixed subTotals) adding 5$ adjustment by amount ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(5d).withInvoiceLineNumber("n-1")
      .withAdjustments(Collections.singletonList(createAdjustment(NOT_PRORATED, AMOUNT, 10d)));
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(20d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(-25d).withInvoiceLineNumber("n-3");
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, AMOUNT, 5d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    // Prorated adj value + non prorated adj of first line
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(15d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    InvoiceLine lineToStorage1 = getLineToStorageById(invoiceLine1.getId());
    assertThat(lineToStorage1.getAdjustments(), hasSize(2));
    assertThat(lineToStorage1.getAdjustmentsTotal(), is(10.5d));

    // First adjustment is not prorated
    assertThat(lineToStorage1.getAdjustments().get(0).getValue(), is(10d));
    // Second adjustment is prorated
    Adjustment line1Adjustment2 = lineToStorage1.getAdjustments().get(1);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, line1Adjustment2);
    assertThat(line1Adjustment2.getValue(), is(0.5d));

    InvoiceLine lineToStorage2 = getLineToStorageById(invoiceLine2.getId());
    assertThat(lineToStorage2.getAdjustments(), hasSize(1));
    assertThat(lineToStorage2.getAdjustmentsTotal(), is(2d));

    Adjustment line2Adjustment1 = lineToStorage2.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, line2Adjustment1);
    assertThat(line2Adjustment1.getValue(), is(2d));

    InvoiceLine lineToStorage3 = getLineToStorageById(invoiceLine3.getId());
    assertThat(lineToStorage3.getAdjustments(), hasSize(1));
    assertThat(lineToStorage3.getAdjustmentsTotal(), is(2.5d));

    Adjustment line3Adjustment1 = lineToStorage3.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, line3Adjustment1);
    assertThat(line3Adjustment1.getValue(), is(2.5d));
  }

  @Test
  public void testUpdateInvoiceWithThreeLinesAddingAmountAdjustmentByAmountWithMissedPenny() {
    logger.info("=== Updating invoice with zero subTotal and three lines (mixed subTotals) adding adjustment by amount, missing one \"penny\" ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(50d).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(15d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(-5d).withInvoiceLineNumber("n-3");
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, AMOUNT, 5d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    // Prorated adj value + non prorated adj of first line
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(5d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));


    verifyAdjustmentValue(invoiceLine1.getId(), 3.57d); // 5 * 50 / 70 = 3,57(1428571428571…)

    verifyAdjustmentValue(invoiceLine2.getId(), 1.07d); // 5 * 15 / 70 = 1,07(1428571428571…)

    verifyAdjustmentValue(invoiceLine3.getId(), 0.36d); // 5 * 5 / 70 = 0,35(7142857142857…) + 0.01

  }


  @Test
  public void testUpdateInvoiceWithThreeLinesAddingAmountAdjustmentByAmountWithMissed2Pennies() {
    logger.info("=== Updating invoice with zero subTotal and three lines (mixed subTotals) adding adjustment by amount, missing one \"penny\" ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    invoice.setCurrency("JOD");
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(0d).withInvoiceLineNumber("n-3");
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, AMOUNT, 5.001d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    // Prorated adj value + non prorated adj of first line
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(5.001d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    verifyAdjustmentValue(invoiceLine1.getId(), 2.5d);     // 5.001 * 25 / 50 = 2,500(5)

    verifyAdjustmentValue(invoiceLine2.getId(), 2.5d);     // 5.001 * 25 / 50 = 2,500(5)

    verifyAdjustmentValue(invoiceLine3.getId(), 0.001d);    // 5.001 * 0 / 50 = 0 + 0.001

  }

  @Test
  public void testUpdateInvoiceWithThreeLinesAddingNegativeAmountAdjustmentByAmountWithMissed2Pennies() {
    logger.info("=== Updating invoice with zero subTotal and three lines  adding adjustment by amount, missing two \"pennies\" ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withInvoiceLineNumber("n-3");


    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withInvoiceLineNumber("n-4");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(25d).withInvoiceLineNumber("n-13");
    addMockEntry(INVOICE_LINES, invoiceLine3);
    addMockEntry(INVOICE_LINES, invoiceLine1);
    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, AMOUNT, -0.02d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    // Prorated adj value + non prorated adj of first line
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(-0.02d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    verifyAdjustmentValue(invoiceLine1.getId(), 0d); // -0.02 * 25 / 75 = -0,00(6666666666666)

    verifyAdjustmentValue(invoiceLine2.getId(), -0.01d); // -0.02 * 25 / 75 = -0,00(6666666666666) - 0.01

    verifyAdjustmentValue(invoiceLine3.getId(), -0.01d); // -0.02 * 25 / 75 = -0,00(6666666666666) - 0.01

  }


  @Test
  public void testUpdateInvoiceWithSevenLinesAddingAmountAdjustmentByLineWith6MissedPennies() {
    logger.info("=== Updating invoice with zero subTotal and seven lines (mixed subTotals) adding adjustment by line, missing six \"pennies\" ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    invoice.setCurrency("JPY"); // zero decimal places
    addMockEntry(INVOICES, invoice);
    List<InvoiceLine> lines = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      InvoiceLine line = getMockInvoiceLine(invoice.getId()).withInvoiceLineNumber("n-" + (i + 1));
      lines.add(line);
      addMockEntry(INVOICE_LINES, line);
    }

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_LINE, AMOUNT, 6d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(7));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    // Prorated adj value + non prorated adj of first line
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(6d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    verifyAdjustmentValue(lines.get(0).getId(), 0d); // 6 / 7 = 0(.8571428571428571)

    for (int i = 1 ; i < 7; i++) {
      verifyAdjustmentValue(lines.get(i).getId(), 1d); // 6 / 7 = 0(.8571428571428571) + 1

    }

  }

  private void verifyAdjustmentValue(String id, double v) {
    InvoiceLine lineToStorage = getLineToStorageById(id);
    Adjustment adjustment = lineToStorage.getAdjustments().get(0);
    assertThat(adjustment.getValue(), is(v));
  }

  @Test
  public void testUpdateInvoiceWithThreeLinesAddingAmountAdjustmentByQuantityWithMissed2JapaneseYen() {
    logger.info("=== Updating invoice with zero subTotal and three lines adding adjustment by quantity, missing two Japanese yens ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    invoice.setCurrency("JPY"); // zero decimal places
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withQuantity(4).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withQuantity(6).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withQuantity(1).withInvoiceLineNumber("n-3");
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_QUANTITY, AMOUNT, 5d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    // Prorated adj value + non prorated adj of first line
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(5d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    verifyAdjustmentValue(invoiceLine1.getId(), 2d); // 5 * 4 / 11 = 1(.818181818181818)

    verifyAdjustmentValue(invoiceLine2.getId(), 3d); // 5 * 6 / 1 = 2(.727272727272727) + 1

    verifyAdjustmentValue(invoiceLine3.getId(), 0d); //5 * 1 / 11 = 0(.4545454545454545) + 1

  }

  @Test
  public void testUpdateInvoiceWithThreeLinesAddingNegativePercentageAdjustmentByAmount() {
    logger.info("=== Updating invoice with zero subTotal and three lines (mixed subTotals) adding -20% adjustment by amount ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(5d).withInvoiceLineNumber("n-1")
      .withAdjustments(Collections.singletonList(createAdjustment(NOT_PRORATED, AMOUNT, 10d)));
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(20d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(-25d).withInvoiceLineNumber("n-3");
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_AMOUNT, PERCENTAGE, -20d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(10d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    InvoiceLine lineToStorage1 = getLineToStorageById(invoiceLine1.getId());
    assertThat(lineToStorage1.getAdjustments(), hasSize(2));
    assertThat(lineToStorage1.getAdjustmentsTotal(), is(10d));

    // First adjustment is not prorated
    assertThat(lineToStorage1.getAdjustments().get(0).getValue(), is(10d));
    // Second adjustment is prorated
    Adjustment line1Adjustment2 = lineToStorage1.getAdjustments().get(1);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, line1Adjustment2);
    assertThat(line1Adjustment2.getValue(), is(0d));

    InvoiceLine lineToStorage2 = getLineToStorageById(invoiceLine2.getId());
    assertThat(lineToStorage2.getAdjustments(), hasSize(1));
    assertThat(lineToStorage2.getAdjustmentsTotal(), is(0d));

    Adjustment line2Adjustment1 = lineToStorage2.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, line2Adjustment1);
    assertThat(line2Adjustment1.getValue(), is(0d));

    InvoiceLine lineToStorage3 = getLineToStorageById(invoiceLine3.getId());
    assertThat(lineToStorage3.getAdjustments(), hasSize(1));
    assertThat(lineToStorage3.getAdjustmentsTotal(), is(0d));

    Adjustment line3Adjustment1 = lineToStorage3.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, line3Adjustment1);
    assertThat(line3Adjustment1.getValue(), is(0d));
  }

  @Test
  public void testUpdateInvoiceWithTwoLinesAddingAmountAdjustmentByQuantity() {
    logger.info("=== Updating invoice with two lines adding adjustment by quantity ===");

    // Prepare data "from storage"
    Invoice invoice = getMockAsJson(OPEN_INVOICE_SAMPLE_PATH).mapTo(Invoice.class).withId(randomUUID().toString());
    invoice.getAdjustments().clear();
    addMockEntry(INVOICES, invoice);

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withQuantity(275).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withQuantity(725).withInvoiceLineNumber("n-2");
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
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

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

  @ParameterizedTest
  @CsvSource({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT",
    "BY_QUANTITY, PERCENTAGE"
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

  @ParameterizedTest
  @CsvSource({
    "BY_AMOUNT, AMOUNT",
    "BY_AMOUNT, PERCENTAGE",
    "BY_LINE, AMOUNT",
    "BY_LINE, PERCENTAGE",
    "BY_QUANTITY, AMOUNT",
    "BY_QUANTITY, PERCENTAGE"
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

    double expectedAdjValue = (type == AMOUNT) ? 25d : 6.25d;

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
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    InvoiceLine lineToStorage = getInvoiceLineUpdates().get(0).mapTo(InvoiceLine.class);
    assertThat(lineToStorage.getAdjustments(), hasSize(1));
    assertThat(lineToStorage.getAdjustmentsTotal(), is(expectedAdjTotal));

    Adjustment lineAdjustment = lineToStorage.getAdjustments().get(0);
    verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
    assertThat(lineAdjustment.getValue(), is(expectedAdjValue));
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
      assertThat(adjustment.getId(), not(is(emptyOrNullString())));
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

    double adjustment1ExpectedAmount = (type == AMOUNT) ? 15d : 3.75d;
    double adjustment2ExpectedAmount = (type == AMOUNT) ? 10d : 2.5d;
    for (Adjustment adj : lineToStorage.getAdjustments()) {
      if (adjustment1.getId().equals(adj.getAdjustmentId())) {
        verifyInvoiceLineAdjustmentCommon(invoiceAdjToStorage1, adj);
        assertThat(adj.getValue(), is(adjustment1ExpectedAmount));
      } else {
        verifyInvoiceLineAdjustmentCommon(invoiceAdjToStorage2, adj);
        assertThat(adj.getValue(), is(adjustment2ExpectedAmount));
      }
    }
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
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

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

    InvoiceLine invoiceLine1 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d).withInvoiceLineNumber("n-1");
    addMockEntry(INVOICE_LINES, invoiceLine1);

    InvoiceLine invoiceLine2 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d).withInvoiceLineNumber("n-2");
    addMockEntry(INVOICE_LINES, invoiceLine2);

    InvoiceLine invoiceLine3 = getMockInvoiceLine(invoice.getId()).withSubTotal(10d).withInvoiceLineNumber("n-3");
    addMockEntry(INVOICE_LINES, invoiceLine3);

    // Prepare request body
    Invoice invoiceBody = copyObject(invoice);
    invoiceBody.getAdjustments().add(createAdjustment(BY_LINE, PERCENTAGE, 5d));

    // Send update request
    verifyPut(String.format(INVOICE_ID_PATH, invoice.getId()), invoiceBody, "", 204);

    // Verification
    assertThat(getInvoiceUpdates(), hasSize(1));
    assertThat(getInvoiceLineUpdates(), hasSize(3));

    Invoice invoiceToStorage = getInvoiceUpdates().get(0).mapTo(Invoice.class);
    assertThat(invoiceToStorage.getAdjustments(), hasSize(1));
    assertThat(invoiceToStorage.getAdjustmentsTotal(), is(1.5d));
    Adjustment invoiceAdjustment = invoiceToStorage.getAdjustments().get(0);
    assertThat(invoiceAdjustment.getId(), not(is(emptyOrNullString())));

    Stream.of(invoiceLine1.getId(), invoiceLine2.getId(), invoiceLine3.getId())
      .forEach(id -> {
        InvoiceLine lineToStorage = getLineToStorageById(id);
        assertThat(lineToStorage.getAdjustments(), hasSize(1));
        assertThat(lineToStorage.getAdjustmentsTotal(), is(0.5d));

        Adjustment lineAdjustment = lineToStorage.getAdjustments().get(0);
        verifyInvoiceLineAdjustmentCommon(invoiceAdjustment, lineAdjustment);
        assertThat(lineAdjustment.getValue(), is(0.5d));
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
    invoiceLine.setMetadata(new Metadata().withCreatedDate(new Date()));
    return invoiceLine;
  }

  private void verifyInvoiceLineAdjustmentCommon(Adjustment invoiceAdjustment, Adjustment lineAdjustment) {
    assertThat(lineAdjustment.getId(), nullValue());
    assertThat(lineAdjustment.getAdjustmentId(), equalTo(invoiceAdjustment.getId()));
    assertThat(lineAdjustment.getDescription(), equalTo(invoiceAdjustment.getDescription()));
    assertThat(lineAdjustment.getFundDistributions(), equalTo(invoiceAdjustment.getFundDistributions()));
    assertThat(lineAdjustment.getProrate(), equalTo(NOT_PRORATED));
    assertThat(lineAdjustment.getRelationToTotal(), equalTo(invoiceAdjustment.getRelationToTotal()));
    assertThat(lineAdjustment.getType(), equalTo(AMOUNT));
  }
}
