package org.folio.services.invoice;

import static org.folio.domain.relationship.EntityTable.INVOICES;
import static org.folio.rest.impl.ApiTestBase.TENANT_ID;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.UUID;
import org.folio.common.dao.EntityIdStorageDaoImpl;
import org.folio.domain.relationship.RecordToEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
class InvoiceLineIdStorageServiceTest {
  private static final String RECORD_ID = UUID.randomUUID().toString();
  private static final String INSTANCE_ID = UUID.randomUUID().toString();

  @Mock
  private EntityIdStorageDaoImpl entityIdStorageDaoImpl;
  @InjectMocks
  private InvoiceLineIdStorageService invoiceLineIdStorageService;

  @Test
  public void shouldReturnSavedRecordToEntity() {
    RecordToEntity expectedRecordToInstance =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(INSTANCE_ID).build();
    when(entityIdStorageDaoImpl.saveRecordToEntityRelationship(any(RecordToEntity.class), any())).thenReturn(Future.succeededFuture(expectedRecordToInstance));
    Future<RecordToEntity> future = invoiceLineIdStorageService.store(RECORD_ID, INSTANCE_ID, TENANT_ID);

    RecordToEntity actualRecordToInstance = future.result();
    assertEquals(expectedRecordToInstance.getTable().getTableName(), actualRecordToInstance.getTable().getTableName());
    assertEquals(expectedRecordToInstance.getTable().getEntityIdFieldName(), actualRecordToInstance.getTable().getEntityIdFieldName());
    assertEquals(expectedRecordToInstance.getTable().getRecordIdFieldName(), actualRecordToInstance.getTable().getRecordIdFieldName());
    assertEquals(expectedRecordToInstance.getRecordId(), actualRecordToInstance.getRecordId());
    assertEquals(expectedRecordToInstance.getEntityId(), actualRecordToInstance.getEntityId());
  }

  @Test
  public void shouldReturnRFailedFuture() {
    when(entityIdStorageDaoImpl.saveRecordToEntityRelationship(any(RecordToEntity.class), any())).thenReturn(Future.failedFuture("failed"));
    Future<RecordToEntity> future = invoiceLineIdStorageService.store(RECORD_ID, INSTANCE_ID, TENANT_ID);

    assertEquals("failed", future.cause().getMessage());
  }
}