package org.folio.invoices.util;

import static org.folio.services.exchange.ExchangeRateProviderResolver.RATE_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.jaxrs.model.Invoice;
import org.junit.jupiter.api.Test;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;

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
  public void testSouldBuildQueryWithoutExchangeRate(){
    String systemCurrency = "USD";
    ConversionQuery actQuery = HelperUtils.buildConversionQuery(new Invoice(), systemCurrency);
    assertEquals(actQuery.getCurrency().getCurrencyCode(), systemCurrency);
    assertNull(actQuery.get(RATE_KEY, Double.class));
  }

  @Test
  public void testSouldBuildQueryWithExchangeRate(){
    String systemCurrency = "USD";
    ConversionQuery actQuery = HelperUtils.buildConversionQuery(new Invoice().withExchangeRate(2d), systemCurrency);
    assertEquals(actQuery.getCurrency().getCurrencyCode(), systemCurrency);
    assertEquals(new Double(2d), actQuery.get(RATE_KEY, Double.class));
  }
}
