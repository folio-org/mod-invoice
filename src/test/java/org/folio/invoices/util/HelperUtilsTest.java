package org.folio.invoices.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.folio.invoices.utils.HelperUtils;
import org.junit.jupiter.api.Test;

public class HelperUtilsTest {
  @Test
  public void testDivideLitByChunksShouldReturnEmptyMapIfListIsEmpty(){
    Map<Integer, List<String>> actMap = HelperUtils.buildIdsChunks(new ArrayList<>(), 15);
    assertEquals(Collections.emptyMap(), actMap);
  }

  @Test
  public void testDivideLitByChunksShouldReturnMapIfListLessThenChuckLength(){
    Map<Integer, List<String>> actMap = HelperUtils.buildIdsChunks(Arrays.asList("2","3"), 15);
    assertEquals(2, actMap.get(0).size());
  }

  @Test
  public void testDivideLitByChunksShouldReturnEmptyMapIfListAboveThenChuckLength(){
    Map<Integer, List<String>> actMap = HelperUtils.buildIdsChunks(Arrays.asList("2","3","2"), 2);
    assertEquals(2, actMap.get(0).size());
    assertEquals(1, actMap.get(1).size());

  }
}
