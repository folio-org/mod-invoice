package org.folio.services.finance.transaction;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_BATCH_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_TRANSACTIONS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.finance.Batch;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.acq.model.finance.TransactionPatch;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.Future;
import one.util.streamex.StreamEx;

public class BaseTransactionService {
  private static final Logger logger = LogManager.getLogger();

  private static final String TRANSACTIONS_ENDPOINT = resourcesPath(FINANCE_TRANSACTIONS);

  private final RestClient restClient;

  public BaseTransactionService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<TransactionCollection> getTransactions(String query, int offset, int limit, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(TRANSACTIONS_ENDPOINT)
        .withQuery(query)
        .withOffset(offset)
        .withLimit(limit);
    return restClient.get(requestEntry, TransactionCollection.class, requestContext)
      .onSuccess(v -> logger.info("getTransactions completed successfully"))
      .onFailure(t -> logger.error("getTransactions failed, query={}", query, t));
  }

  public Future<List<Transaction>> getTransactions(List<String> transactionIds, RequestContext requestContext) {
    if (!CollectionUtils.isEmpty(transactionIds)) {
      List<Future<TransactionCollection>> expenseClassesFutureList = StreamEx
        .ofSubLists(transactionIds, MAX_IDS_FOR_GET_RQ)
        .map(ids -> getTransactionsChunk(ids, requestContext))
        .collect(toList());

      return collectResultsOnSuccess(expenseClassesFutureList)
        .map(expenseClassCollections ->
          expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
        );
    }
    return succeededFuture(Collections.emptyList());
  }

  private Future<TransactionCollection> getTransactionsChunk(List<String> transactionIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(transactionIds));
    return this.getTransactions(query, 0, transactionIds.size(), requestContext);
  }

  public Future<Void> batchAllOrNothing(List<Transaction> transactionsToCreate, List<Transaction> transactionsToUpdate,
      List<String> idsOfTransactionsToDelete, List<TransactionPatch> transactionPatches, RequestContext requestContext) {
    Batch batch = new Batch();
    if (transactionsToCreate != null) {
      transactionsToCreate.forEach(tr -> {
        if (tr.getId() == null) {
          tr.setId(UUID.randomUUID().toString());
        }
      });
      batch.setTransactionsToCreate(transactionsToCreate);
    }
    if (transactionsToUpdate != null) {
      batch.setTransactionsToUpdate(transactionsToUpdate);
    }
    if (idsOfTransactionsToDelete != null) {
      batch.setIdsOfTransactionsToDelete(idsOfTransactionsToDelete);
    }
    if (transactionPatches != null) {
      batch.setTransactionPatches(transactionPatches);
    }
    String endpoint = resourcesPath(FINANCE_BATCH_TRANSACTIONS);
    return restClient.postEmptyResponse(endpoint, batch, requestContext)
      .onSuccess(v -> logger.info("batchAllOrNothing completed successfully"))
      .onFailure(t -> logger.error("batchAllOrNothing failed, batch={}", JsonObject.mapFrom(batch), t));
  }

  public Future<Void> batchCreate(List<Transaction> transactions, RequestContext requestContext) {
    return batchAllOrNothing(transactions, null, null, null, requestContext);
  }

  public Future<Void> batchUpdate(List<Transaction> transactions, RequestContext requestContext) {
    return batchAllOrNothing(null, transactions, null, null, requestContext);
  }

  public Future<Void> batchRelease(List<Transaction> transactions, RequestContext requestContext) {
    // NOTE: we will have to use transactionPatches when it is available
    transactions.forEach(tr -> tr.getEncumbrance().setStatus(Encumbrance.Status.RELEASED));
    return batchUpdate(transactions, requestContext);
  }

  public Future<Void> batchUnrelease(List<Transaction> transactions, RequestContext requestContext) {
    // NOTE: we will have to use transactionPatches when it is available
    transactions.forEach(tr -> tr.getEncumbrance().setStatus(Encumbrance.Status.UNRELEASED));
    return batchUpdate(transactions, requestContext);
  }


  public Future<Void> batchCancel(List<Transaction> transactions, RequestContext requestContext) {
    // NOTE: we will have to use transactionPatches when it is available
    transactions.forEach(tr -> tr.setInvoiceCancelled(true));
    return batchUpdate(transactions, requestContext);
  }

}
