package org.folio.invoices.util;

import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.money.convert.ConversionQuery;

import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.junit.jupiter.api.Test;

public class HelperUtilsTest {
  @Test
  public void testDivideListByChunksShouldReturnEmptyMapIfListIsEmpty(){
    Map<Integer, List<String>> actMap = HelperUtils.buildIdsChunks(new ArrayList<>(), 15);
    assertEquals(Collections.emptyMap(), actMap);
  }

  @Test
  public void testDivideListByChunksShouldReturnMapIfListLessThenChuckLength(){
    Map<Integer, List<String>> actMap = HelperUtils.buildIdsChunks(Arrays.asList("2","3"), 15);
    assertEquals(2, actMap.get(0).size());
  }

  @Test
  public void testDivideListByChunksShouldReturnEmptyMapIfListAboveThenChuckLength(){
    Map<Integer, List<String>> actMap = HelperUtils.buildIdsChunks(Arrays.asList("2","3","2"), 2);
    assertEquals(2, actMap.get(0).size());
    assertEquals(1, actMap.get(1).size());
  }

  @Test
  public void testShouldBuildQueryWithoutExchangeRate(){
    String systemCurrency = "USD";
    ConversionQuery actQuery = HelperUtils.buildConversionQuery(new Invoice().withCurrency("EUR"), systemCurrency);
    assertEquals(actQuery.getCurrency().getCurrencyCode(), systemCurrency);
    assertNull(actQuery.get(RATE_KEY, Double.class));
  }

  @Test
  public void testShouldBuildQueryWithExchangeRate(){
    String systemCurrency = "USD";
    ConversionQuery actQuery = HelperUtils.buildConversionQuery(new Invoice().withExchangeRate(2d).withCurrency("EUR"), systemCurrency);
    assertEquals(actQuery.getCurrency().getCurrencyCode(), systemCurrency);
    assertEquals(Double.valueOf(2d), actQuery.get(RATE_KEY, Double.class));
  }

  @Test
  public void testShouldCalculateInvoiceLineTotals() {
    Invoice invoice = new Invoice()
      .withCurrency("USD");

    Adjustment adjustment = new Adjustment()
      .withRelationToTotal(Adjustment.RelationToTotal.IN_ADDITION_TO)
      .withType(Adjustment.Type.AMOUNT)
      .withValue(10.0d);

    InvoiceLine invoiceLine = new InvoiceLine()
      .withSubTotal(100.123456)
      .withAdjustments(List.of(adjustment));

    HelperUtils.calculateInvoiceLineTotals(invoiceLine, invoice);
    assertEquals(Double.valueOf(110.12d), invoiceLine.getTotal());
  }
}
