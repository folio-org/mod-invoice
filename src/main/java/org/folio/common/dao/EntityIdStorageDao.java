package org.folio.common.dao;

import io.vertx.core.Future;
import org.folio.domain.relationship.RecordToEntity;

/**
 * Data access object for Entity.
 */
public interface EntityIdStorageDao {
  /**
   * Saves Entity to database.
   *
   * @param recordToEntity  to save.
   * @param tenantId tenant id.
   * @return future with saved {@link RecordToEntity}.
   */
  Future<RecordToEntity> saveRecordToEntityRelationship(RecordToEntity recordToEntity, String tenantId);
}
