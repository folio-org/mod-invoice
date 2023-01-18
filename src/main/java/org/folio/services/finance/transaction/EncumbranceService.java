package org.folio.services.finance.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.List;
import java.util.Map;

import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.finance.OrderTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;
import one.util.streamex.StreamEx;

public class EncumbranceService {

  private final BaseTransactionService baseTransactionService;
  private final OrderTransactionSummaryService orderTransactionSummaryService;

  public EncumbranceService(BaseTransactionService transactionService,
      OrderTransactionSummaryService orderTransactionSummaryService) {
    this.baseTransactionService = transactionService;
    this.orderTransactionSummaryService = orderTransactionSummaryService;
  }

  public Future<List<Transaction>> getEncumbrancesByPoLineIds(List<String> poLineIds, RequestContext requestContext) {
    List<Future<TransactionCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(this::buildEncumbranceChunckQueryByPoLineIds)
      .map(query -> baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
      .map(expenseClassCollections ->
        expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
      );
  }

  public Future<Void> unreleaseEncumbrances(List<Transaction> transactions, RequestContext requestContext) {
    Map<String, List<Transaction>> transactionsByOrderId = transactions.stream()
      .collect(groupingBy(tr -> tr.getEncumbrance().getSourcePurchaseOrderId()));
    var futures =  transactionsByOrderId.entrySet().stream()
      .map(entry -> unreleaseEncumbrancesByOrderId(entry.getKey(), entry.getValue(), requestContext))
      .collect(toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  private String buildEncumbranceChunckQueryByPoLineIds(List<String> poLineIds) {
    return "transactionType==Encumbrance and " +
      convertIdsToCqlQuery(poLineIds, "encumbrance.sourcePoLineId", true);
  }

  private Future<Void> unreleaseEncumbrancesByOrderId(String orderId, List<Transaction> transactions,
      RequestContext requestContext) {
    return orderTransactionSummaryService.updateOrderTransactionSummary(
        buildOrderTransactionsSummary(orderId, transactions), requestContext)
      .compose(v -> baseTransactionService.updateTransactions(transactions, requestContext));
  }

  private OrderTransactionSummary buildOrderTransactionsSummary(String orderId, List<Transaction> transactions) {
    return new OrderTransactionSummary()
      .withId(orderId)
      .withNumTransactions(transactions.size());
  }

}
