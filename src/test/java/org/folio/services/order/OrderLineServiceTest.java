package org.folio.services.order;

import io.vertx.core.Future;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.acq.model.orders.PoLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class OrderLineServiceTest {
  private AutoCloseable mockitoMocks;
  private OrderLineService orderLineService;

  @Mock
  private RequestContext requestContext;
  @Mock
  private RestClient restClient;
  @Captor
  ArgumentCaptor<RequestEntry> requestEntryCaptor;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    orderLineService = new OrderLineService(restClient);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  public void getPoLinesByIdAndQueryTest() {
    Function<List<String>, String> queryFunction = list -> list.toString() + " AND something";
    List<String> poLineIds = new ArrayList<>();
    List<PoLine> poLines1 = new ArrayList<>();
    for (int i=0; i<MAX_IDS_FOR_GET_RQ; i++) {
      String id = UUID.randomUUID().toString();
      poLineIds.add(id);
      PoLine poLine = new PoLine()
        .withId(id);
      poLines1.add(poLine);
    }
    PoLineCollection poLineCollection1 = new PoLineCollection()
      .withPoLines(poLines1)
      .withTotalRecords(poLines1.size());
    String expectedQuery1 = queryFunction.apply(poLineIds);

    String id = UUID.randomUUID().toString();
    poLineIds.add(id);
    PoLine poLine = new PoLine()
      .withId(id);
    PoLineCollection poLineCollection2 = new PoLineCollection()
      .withPoLines(List.of(poLine))
      .withTotalRecords(1);
    String expectedQuery2 = queryFunction.apply(List.of(id));

    when(restClient.get(any(RequestEntry.class), eq(PoLineCollection.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture(poLineCollection1))
      .thenReturn(Future.succeededFuture(poLineCollection2));

    Future<List<PoLine>> result = orderLineService.getPoLinesByIdAndQuery(poLineIds, queryFunction, requestContext);

    assertTrue(result.succeeded());
    verify(restClient, times(2)).get(requestEntryCaptor.capture(), eq(PoLineCollection.class), eq(requestContext));
    List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
    assertThat(requestEntries, hasSize(2));
    assertEquals(encodeQuery(expectedQuery1), requestEntries.get(0).getQueryParams().get("query"));
    assertEquals(encodeQuery(expectedQuery2), requestEntries.get(1).getQueryParams().get("query"));
  }

  @Test
  public void updatePoLinesTest() {
    PoLine poLine = new PoLine()
      .withId(UUID.randomUUID().toString());
    List<PoLine> poLines = List.of(poLine);

    when(restClient.put(any(RequestEntry.class), any(CompositePoLine.class), eq(requestContext)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = orderLineService.updatePoLines(poLines, requestContext);

    assertTrue(result.succeeded());
  }
}
