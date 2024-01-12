package org.folio.common.dao;

import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.domain.relationship.EntityTable;
import org.folio.domain.relationship.RecordToEntity;
import org.springframework.stereotype.Repository;

@Repository
public class EntityIdStorageDaoImpl implements EntityIdStorageDao {
  private static final Logger LOGGER = LogManager.getLogger(EntityIdStorageDaoImpl.class);

  private static final String INSERT_FUNCTION = "WITH input_rows({recordIdFieldName}, {entityIdFieldName}) AS (\n" +
    "   VALUES ($1,$2)\n" +
    ")\n" +
    ", ins AS (\n" +
    "   INSERT INTO {schemaName}.{tableName}({recordIdFieldName}, {entityIdFieldName})\n" +
    "   SELECT * FROM input_rows\n" +
    "   ON CONFLICT ({recordIdFieldName}) DO NOTHING\n" +
    "   RETURNING {recordIdFieldName}, {entityIdFieldName}\n" +
    "   )\n" +
    "SELECT {recordIdFieldName}, {entityIdFieldName}\n" +
    "FROM   ins\n" +
    "UNION  ALL\n" +
    "SELECT c.{recordIdFieldName}, c.{entityIdFieldName} \n" +
    "FROM   input_rows\n" +
    "JOIN   {schemaName}.{tableName} c USING ({recordIdFieldName});";

  private final PostgresClientFactory postgresClientFactory;

  public EntityIdStorageDaoImpl(final PostgresClientFactory postgresClientFactory) {
    this.postgresClientFactory = postgresClientFactory;
  }

  @Override
  public Future<RecordToEntity> saveRecordToEntityRelationship(RecordToEntity recordToEntity, String tenantId) {
    EntityTable entityTable = recordToEntity.getTable();
    UUID recordId = UUID.fromString(recordToEntity.getRecordId());
    UUID entityId = UUID.fromString(recordToEntity.getEntityId());
    String tableName = entityTable.getTableName();

    LOGGER.info("Trying to save entity to {} with recordId = {} and entityId = {}", tableName, recordId, entityId);
    String sql = prepareQuery(entityTable, tenantId);
    Tuple tuple = Tuple.of(recordId, entityId);

    return postgresClientFactory.createInstance(tenantId).execute(sql, tuple)
      .map(rows -> mapRowToRecordToEntity(rows, entityTable));
  }

  /**
   * Convert database query result {@link RowSet} to {@link RecordToEntity}.
   * There is no case when DB returns empty RowSet, so hasNext check is not needed yet.
   *
   * @param rows query result RowSet.
   * @return RecordToInstance
   */
  private RecordToEntity mapRowToRecordToEntity(RowSet<Row> rows, EntityTable entityTable) {
    Row row = rows.iterator().next();
    return RecordToEntity.builder()
      .table(entityTable)
      .recordId(row.getValue(entityTable.getRecordIdFieldName()).toString())
      .entityId(row.getValue(entityTable.getEntityIdFieldName()).toString())
      .build();
  }

  /**
   * Prepares SQL query for Insert.
   *
   * @param entityTable the entity table.
   * @return sql query to use.
   */
  private String prepareQuery(EntityTable entityTable, String tenantId) {
    String schemaName = convertToPsqlStandard(tenantId);
    return INSERT_FUNCTION.replace("{recordIdFieldName}", entityTable.getRecordIdFieldName())
      .replace("{entityIdFieldName}", entityTable.getEntityIdFieldName())
      .replace("{tableName}", entityTable.getTableName())
      .replace("{schemaName}", schemaName);
  }
}
