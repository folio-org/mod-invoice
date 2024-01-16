package org.folio.dao;

import static org.folio.domain.relationship.EntityTable.INVOICES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.folio.common.dao.EntityIdStorageDao;
import org.folio.common.dao.EntityIdStorageDaoImpl;
import org.folio.common.dao.PostgresClientFactory;
import org.folio.domain.relationship.RecordToEntity;
import org.folio.rest.impl.AbstractRestTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class EntityIdStorageDaoImplTest extends AbstractRestTest {
  private static final String RECORD_ID = UUID.randomUUID().toString();
  private static final String INVOICE_ID = UUID.randomUUID().toString();
  private static final String DUPLICATE_INVOICE_ID = UUID.randomUUID().toString();

  PostgresClientFactory postgresClientFactory = new PostgresClientFactory(Vertx.vertx());
  private final EntityIdStorageDao entityIdStorageDao = new EntityIdStorageDaoImpl(postgresClientFactory);

  @Test
  public void shouldReturnSavedRecordToInstance(VertxTestContext context) {
    RecordToEntity expectedRecordToInstance =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(INVOICE_ID).build();

    Future<RecordToEntity> future = entityIdStorageDao.saveRecordToEntityRelationship(expectedRecordToInstance, TENANT_ID);
    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      RecordToEntity actualRecordToEntity = ar.result();
      assertEquals(expectedRecordToInstance.getRecordId(), actualRecordToEntity.getRecordId());
      assertEquals(expectedRecordToInstance.getEntityId(), actualRecordToEntity.getEntityId());
      assertEquals(expectedRecordToInstance.getTable(), actualRecordToEntity.getTable());
      context.completeNow();
    });
  }

  @Test
  public void shouldReturnSameInstanceIdWithDuplicateRecordId(VertxTestContext context) {
    RecordToEntity expectedRecordToInstance1 =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(INVOICE_ID).build();
    RecordToEntity expectedRecordToInstance2 =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(DUPLICATE_INVOICE_ID).build();

    Future<RecordToEntity> future = entityIdStorageDao.saveRecordToEntityRelationship(expectedRecordToInstance1, TENANT_ID)
      .compose(ar -> entityIdStorageDao.saveRecordToEntityRelationship(expectedRecordToInstance2, TENANT_ID));

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      RecordToEntity actualRecordToEntity = ar.result();
      assertEquals(expectedRecordToInstance1.getRecordId(), actualRecordToEntity.getRecordId());
      assertEquals(expectedRecordToInstance1.getEntityId(), actualRecordToEntity.getEntityId());
      assertEquals(expectedRecordToInstance1.getTable(), actualRecordToEntity.getTable());
      context.completeNow();
    });
  }
}
