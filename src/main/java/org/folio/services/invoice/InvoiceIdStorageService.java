package org.folio.services.invoice;

import static org.folio.domain.relationship.EntityTable.INVOICES;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.common.dao.EntityIdStorageDao;
import org.folio.domain.relationship.RecordToEntity;

public class InvoiceIdStorageService implements IdStorageService {
  private static final Logger LOGGER = LogManager.getLogger(InvoiceIdStorageService.class);

  private final EntityIdStorageDao entityIdStorageDao;

  public InvoiceIdStorageService(EntityIdStorageDao entityIdStorageDao) {
    this.entityIdStorageDao = entityIdStorageDao;
  }

  @Override
  public Future<RecordToEntity> store(String recordId, String instanceId, String tenantId) {
    RecordToEntity recordToInvoiceLine = RecordToEntity.builder()
      .table(INVOICES)
      .recordId(recordId)
      .entityId(instanceId)
      .build();
    LOGGER.info("Saving recordToInvoiceLine relationship: {}", recordToInvoiceLine);
    return entityIdStorageDao.saveRecordToEntityRelationship(recordToInvoiceLine, tenantId);
  }
}