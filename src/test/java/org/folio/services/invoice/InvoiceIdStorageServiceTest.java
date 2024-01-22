package org.folio.services.invoice;

import static org.folio.domain.relationship.EntityTable.INVOICES;
import static org.folio.kafka.headers.FolioKafkaHeaders.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import java.util.UUID;
import org.folio.common.dao.EntityIdStorageDaoImpl;
import org.folio.domain.relationship.RecordToEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
public class InvoiceIdStorageServiceTest {

  private static final String RECORD_ID = UUID.randomUUID().toString();
  private static final String INVOICE_ID = UUID.randomUUID().toString();

  private AutoCloseable closeable;
  @Mock private EntityIdStorageDaoImpl entityIdStorageDaoImpl;
  @InjectMocks private InvoiceIdStorageService invoiceIdStorageService;


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
    RecordToEntity expectedRecordToInvoice =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(INVOICE_ID).build();
    when(entityIdStorageDaoImpl.saveRecordToEntityRelationship(any(RecordToEntity.class), any())).thenReturn(Future.succeededFuture(expectedRecordToInvoice));
    Future<RecordToEntity> future = invoiceIdStorageService.store(RECORD_ID, INVOICE_ID, TENANT_ID);

    RecordToEntity actualRecordToInstance = future.result();
    assertEquals(expectedRecordToInvoice.getTable().getTableName(), actualRecordToInstance.getTable().getTableName());
    assertEquals(expectedRecordToInvoice.getTable().getEntityIdFieldName(), actualRecordToInstance.getTable().getEntityIdFieldName());
    assertEquals(expectedRecordToInvoice.getTable().getRecordIdFieldName(), actualRecordToInstance.getTable().getRecordIdFieldName());
    assertEquals(expectedRecordToInvoice.getRecordId(), actualRecordToInstance.getRecordId());
    assertEquals(expectedRecordToInvoice.getEntityId(), actualRecordToInstance.getEntityId());
  }

  @Test
  public void shouldReturnFailedFuture() {
    when(entityIdStorageDaoImpl.saveRecordToEntityRelationship(any(RecordToEntity.class), any())).thenReturn(Future.failedFuture("failed"));
    Future<RecordToEntity> future = invoiceIdStorageService.store(RECORD_ID, INVOICE_ID, TENANT_ID);

    assertEquals("failed", future.cause().getMessage());
  }
}