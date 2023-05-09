package org.folio.services.finance.transaction;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.finance.OrderTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;
import one.util.streamex.StreamEx;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class EncumbranceService {
  private static final Logger log = LogManager.getLogger();

  private final BaseTransactionService baseTransactionService;
  private final OrderTransactionSummaryService orderTransactionSummaryService;

  public EncumbranceService(BaseTransactionService transactionService,
      OrderTransactionSummaryService orderTransactionSummaryService) {
    this.baseTransactionService = transactionService;
    this.orderTransactionSummaryService = orderTransactionSummaryService;
  }

  public Future<List<Transaction>> getEncumbrancesByPoLineIds(List<String> poLineIds, String fiscalYearId,
      RequestContext requestContext) {
    List<Future<TransactionCollection>> transactionsFutureList = StreamEx
      .ofSubLists(poLineIds, MAX_IDS_FOR_GET_RQ)
      .map(lineIds -> buildEncumbranceChunckQueryByPoLineIds(lineIds, fiscalYearId))
      .map(query -> baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(transactionsFutureList)
      .map(transactionCollections ->
        transactionCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
      )
      .onFailure(t -> log.error(String.format("Error getting encumbrances by po line ids, poLineIds=%s, fiscalYearId=%s",
        poLineIds, fiscalYearId), t));
  }

  public Future<Void> unreleaseEncumbrances(List<Transaction> transactions, RequestContext requestContext) {
    Map<String, List<Transaction>> transactionsByOrderId = transactions.stream()
      .collect(groupingBy(tr -> tr.getEncumbrance().getSourcePurchaseOrderId()));
    var futures =  transactionsByOrderId.entrySet().stream()
      .map(entry -> unreleaseEncumbrancesByOrderId(entry.getKey(), entry.getValue(), requestContext))
      .collect(toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  private String buildEncumbranceChunckQueryByPoLineIds(List<String> poLineIds, String fiscalYearId) {
    String transactionTypeFilter = "transactionType==Encumbrance";
    String fiscalYearFilter = fiscalYearId == null ? "" : String.format(" AND fiscalYearId==%s", fiscalYearId);
    String idFilter = " AND " + convertIdsToCqlQuery(poLineIds, "encumbrance.sourcePoLineId", true);
    return String.format("%s%s%s", transactionTypeFilter, fiscalYearFilter, idFilter);
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

  public Future<List<InvoiceWorkflowDataHolder>> updateEncumbranceLinksForFiscalYear(Invoice invoice,
      List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    // this method is used when an invoice line is created, with the related holders
    if (invoice.getFiscalYearId() == null) {
      return succeededFuture(holders);
    }
    boolean updateNeeded = holders.stream()
      .map(InvoiceWorkflowDataHolder::getEncumbrance)
      .filter(Objects::nonNull)
      .anyMatch(encumbrance -> !invoice.getFiscalYearId().equals(encumbrance.getFiscalYearId()));
    if (!updateNeeded) {
      return succeededFuture(holders);
    }
    return updateInvoiceLinesEncumbranceLinks(holders, invoice.getFiscalYearId(), requestContext)
      .map(linesToUpdate -> holders);
  }

  public Future<List<InvoiceLine>> updateInvoiceLinesEncumbranceLinks(List<InvoiceWorkflowDataHolder> holders,
      String fiscalYearId, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> relevantHolders = holders.stream()
      .filter(holder -> holder.getInvoiceLine() != null && holder.getInvoiceLine().getPoLineId() != null)
      .collect(toList());
    if (relevantHolders.isEmpty()) {
      return succeededFuture(List.of());
    }
    List<String> poLineIds = getPoLineIds(relevantHolders);
    return getEncumbrancesByPoLineIds(poLineIds, fiscalYearId, requestContext)
      .map(encumbrances -> updateFundDistributionsWithEncumbrances(relevantHolders, encumbrances))
      .onFailure(t -> log.error(String.format("Error updating invoice lines encumbrance links, invoiceId=%s, fiscalYearId=%s",
        relevantHolders.get(0).getInvoice().getId(), fiscalYearId), t));
  }

  private List<String> getPoLineIds(List<InvoiceWorkflowDataHolder> holders) {
    return holders.stream()
      .map(holder -> holder.getInvoiceLine().getPoLineId())
      .distinct()
      .collect(toList());
  }

  private List<InvoiceLine> updateFundDistributionsWithEncumbrances(List<InvoiceWorkflowDataHolder> holders,
      List<Transaction> encumbrances) {
    List<InvoiceLine> linesToUpdate = new ArrayList<>();
    for (InvoiceWorkflowDataHolder holder : holders) {
      List<Transaction> matchingEncumbrances = encumbrances.stream()
        .filter(enc -> enc.getEncumbrance().getSourcePoLineId().equals(holder.getInvoiceLine().getPoLineId()) &&
          enc.getFromFundId().equals(holder.getFundId()) &&
          Objects.equals(enc.getExpenseClassId(), holder.getFundDistribution().getExpenseClassId()))
        .collect(toList());
      FundDistribution fundDistribution = holder.getFundDistribution();
      if (matchingEncumbrances.isEmpty()) {
        if (fundDistribution.getEncumbrance() != null) {
          fundDistribution.withEncumbrance(null);
          holder.withEncumbrance(null);
          if (!linesToUpdate.contains(holder.getInvoiceLine())) {
            linesToUpdate.add(holder.getInvoiceLine());
          }
        }
      } else {
        Transaction encumbrance = matchingEncumbrances.get(0);
        if (!encumbrance.getId().equals(fundDistribution.getEncumbrance())) {
          fundDistribution.withEncumbrance(encumbrance.getId());
          holder.withEncumbrance(encumbrance);
          if (!linesToUpdate.contains(holder.getInvoiceLine())) {
            linesToUpdate.add(holder.getInvoiceLine());
          }
        }
      }
    }
    return linesToUpdate;
  }

}
