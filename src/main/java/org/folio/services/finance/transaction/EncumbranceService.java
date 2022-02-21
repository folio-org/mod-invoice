package org.folio.services.finance.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.completablefuture.FolioVertxCompletableFuture.allOf;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.acq.model.finance.OrderTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;

import one.util.streamex.StreamEx;

public class EncumbranceService {

  private final BaseTransactionService baseTransactionService;
  private final OrderTransactionSummaryService orderTransactionSummaryService;

  public EncumbranceService(BaseTransactionService transactionService,
      OrderTransactionSummaryService orderTransactionSummaryService) {
    this.baseTransactionService = transactionService;
    this.orderTransactionSummaryService = orderTransactionSummaryService;
  }

  public CompletableFuture<List<Transaction>> getEncumbrancesByPoLineIds(List<String> poLineIds, RequestContext requestContext) {
    List<CompletableFuture<TransactionCollection>> expenseClassesFutureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(this::buildEncumbranceChunckQueryByPoLineIds)
      .map(queryAndSize -> baseTransactionService.getTransactions(queryAndSize.getLeft(), 0,
        queryAndSize.getRight(), requestContext))
      .collect(toList());

    return collectResultsOnSuccess(expenseClassesFutureList)
      .thenApply(expenseClassCollections ->
        expenseClassCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
      );
  }

  public CompletableFuture<Void> unreleaseEncumbrances(List<Transaction> transactions, RequestContext requestContext) {
    Map<String, List<Transaction>> transactionsByOrderId = transactions.stream()
      .collect(groupingBy(tr -> tr.getEncumbrance().getSourcePurchaseOrderId()));
    return allOf(requestContext.getContext(), transactionsByOrderId.entrySet().stream()
      .map(entry -> unreleaseEncumbrancesByOrderId(entry.getKey(), entry.getValue(), requestContext))
      .toArray(CompletableFuture[]::new));
  }

  private Pair<String, Integer> buildEncumbranceChunckQueryByPoLineIds(List<String> poLineIds) {
    String query = "transactionType==Encumbrance and " + convertIdsToCqlQuery(poLineIds, "encumbrance.sourcePoLineId", true);
    return Pair.of(query, poLineIds.size());
  }

  private CompletableFuture<Void> unreleaseEncumbrancesByOrderId(String orderId, List<Transaction> transactions,
      RequestContext requestContext) {
    return orderTransactionSummaryService.updateOrderTransactionSummary(
        buildOrderTransactionsSummary(orderId, transactions), requestContext)
      .thenCompose(v -> baseTransactionService.updateTransactions(transactions, requestContext));
  }

  private OrderTransactionSummary buildOrderTransactionsSummary(String orderId, List<Transaction> transactions) {
    return new OrderTransactionSummary()
      .withId(orderId)
      .withNumTransactions(transactions.size());
  }

}
