package org.folio.services.invoice;

import io.vertx.core.Future;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InvoiceLineServiceTest {
  private AutoCloseable closeable;
  @InjectMocks InvoiceLineService invoiceLineService;
  @Mock
  private RequestContext requestContext;
  @Mock
  private RestClient restClient;

  @BeforeEach
  public void initMocks() {
    closeable = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void closeService() throws Exception {
    closeable.close();
  }

  @Test
  public void shouldReturnSavedRecordToEntity() {
    List<String> ids = List.of("id1", "id2");
    List<InvoiceLine> invoiceLines = List.of(new InvoiceLine());
    InvoiceLineCollection invoiceLineCollection = new InvoiceLineCollection()
      .withInvoiceLines(invoiceLines)
      .withTotalRecords(1);

    ArgumentCaptor<RequestEntry> requestEntryCaptor = ArgumentCaptor.forClass(RequestEntry.class);
    when(restClient.get(requestEntryCaptor.capture(), eq(InvoiceLineCollection.class), eq(requestContext)))
      .thenReturn(succeededFuture(invoiceLineCollection));

    Future<List<InvoiceLine>> future = invoiceLineService.getInvoiceLinesByIdsAndQuery(ids, Object::toString, requestContext);

    assertTrue(future.succeeded());
    verify(restClient, times(1)).get(any(RequestEntry.class), any(), any(RequestContext.class));
    List<RequestEntry> requestEntries = requestEntryCaptor.getAllValues();
    assertEquals(encodeQuery(ids.toString()), requestEntries.getFirst().getQueryParams().get("query"));
    assertEquals(invoiceLines, future.result());
  }
}
