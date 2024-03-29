package org.folio.services.invoice;

import io.vertx.core.Future;
import org.folio.domain.relationship.RecordToEntity;

public interface IdStorageService {

  /**
   * Stores relationship between record and entity.
   *
   * @param recordId Record id.
   * @param entityId Entity id.
   * @return future with {@link RecordToEntity}.
   */
  Future<RecordToEntity> store(String recordId, String entityId, String tenantId);
}
