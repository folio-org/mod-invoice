package org.folio.dao;

import static org.folio.domain.relationship.EntityTable.INVOICES;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.UUID;
import org.folio.common.dao.EntityIdStorageDao;
import org.folio.common.dao.EntityIdStorageDaoImpl;
import org.folio.common.dao.PostgresClientFactory;
import org.folio.domain.relationship.RecordToEntity;
import org.folio.rest.impl.AbstractRestTest;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EntityIdStorageDaoImplTest extends AbstractRestTest {
  private static final String RECORD_ID = UUID.randomUUID().toString();
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final String DUPLICATE_INSTANCE_ID = UUID.randomUUID().toString();

  PostgresClientFactory postgresClientFactory = new PostgresClientFactory(Vertx.vertx());
  private final EntityIdStorageDao entityIdStorageDao = new EntityIdStorageDaoImpl(postgresClientFactory);

  @Test
  public void shouldReturnSavedRecordToInstance(TestContext context) {
    Async async = context.async();
    RecordToEntity expectedRecordToInstance =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(INSTANCE_ID).build();

    Future<RecordToEntity> future = entityIdStorageDao.saveRecordToEntityRelationship(expectedRecordToInstance, TENANT_ID);
    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      RecordToEntity actualRecordToEntity = ar.result();
      context.assertEquals(expectedRecordToInstance.getRecordId(), actualRecordToEntity.getRecordId());
      context.assertEquals(expectedRecordToInstance.getEntityId(), actualRecordToEntity.getEntityId());
      context.assertEquals(expectedRecordToInstance.getTable(), actualRecordToEntity.getTable());
      async.complete();
    });
  }

  @Test
  public void shouldReturnSameInstanceIdWithDuplicateRecordId(TestContext context) {
    Async async = context.async();

    RecordToEntity expectedRecordToInstance1 =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(INSTANCE_ID).build();
    RecordToEntity expectedRecordToInstance2 =
      RecordToEntity.builder().table(INVOICES).recordId(RECORD_ID).entityId(DUPLICATE_INSTANCE_ID).build();

    Future<RecordToEntity> future = entityIdStorageDao.saveRecordToEntityRelationship(expectedRecordToInstance1, TENANT_ID)
      .compose(ar -> entityIdStorageDao.saveRecordToEntityRelationship(expectedRecordToInstance2, TENANT_ID));

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      RecordToEntity actualRecordToEntity = ar.result();
      context.assertEquals(expectedRecordToInstance1.getRecordId(), actualRecordToEntity.getRecordId());
      context.assertEquals(expectedRecordToInstance1.getEntityId(), actualRecordToEntity.getEntityId());
      context.assertEquals(expectedRecordToInstance1.getTable(), actualRecordToEntity.getTable());
      async.complete();
    });
  }
}
