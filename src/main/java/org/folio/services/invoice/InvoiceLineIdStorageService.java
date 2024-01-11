package org.folio.services.invoice;

import static org.folio.domain.relationship.EntityTable.INVOICE_LINES;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.common.dao.EntityIdStorageDao;
import org.folio.domain.relationship.RecordToEntity;

public class InvoiceLineIdStorageService implements IdStorageService {
  private static final Logger LOGGER = LogManager.getLogger(InvoiceLineIdStorageService.class);

  private final EntityIdStorageDao entityIdStorageDao;

  public InvoiceLineIdStorageService(EntityIdStorageDao entityIdStorageDao) {
    this.entityIdStorageDao = entityIdStorageDao;
  }

  @Override
  public Future<RecordToEntity> store(String recordId, String instanceId, String tenantId) {
    RecordToEntity recordToInvoiceLine = RecordToEntity.builder()
      .table(INVOICE_LINES)
      .recordId(recordId)
      .entityId(instanceId)
      .build();
    LOGGER.info("Saving recordToInvoiceLine relationship: {}", recordToInvoiceLine);
    return entityIdStorageDao.saveRecordToEntityRelationship(recordToInvoiceLine, tenantId);
  }
}