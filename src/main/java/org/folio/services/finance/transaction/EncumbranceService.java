package org.folio.services.finance.transaction;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;
import one.util.streamex.StreamEx;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

public class EncumbranceService {

  private static final Logger logger = LogManager.getLogger();

  private final BaseTransactionService baseTransactionService;

  public EncumbranceService(BaseTransactionService transactionService) {
    this.baseTransactionService = transactionService;
  }

  public Future<List<Transaction>> getEncumbrancesByPoLineIds(List<String> poLineIds, String fiscalYearId,
      RequestContext requestContext) {
    List<String> distinctPoLineIds = poLineIds.stream().distinct().toList();
    List<Future<TransactionCollection>> transactionsFutureList = StreamEx
      .ofSubLists(distinctPoLineIds, MAX_IDS_FOR_GET_RQ)
      .map(lineIds -> buildEncumbranceChunkQueryByPoLineIds(lineIds, fiscalYearId))
      .map(query -> baseTransactionService.getTransactions(query, 0, Integer.MAX_VALUE, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(transactionsFutureList)
      .map(transactionCollections ->
        transactionCollections.stream().flatMap(col -> col.getTransactions().stream()).collect(toList())
      )
      .onFailure(t -> logger.error("Error getting encumbrances by po line ids, poLineIds={}, fiscalYearId={}",
        distinctPoLineIds, fiscalYearId, t));
  }

  private String buildEncumbranceChunkQueryByPoLineIds(List<String> poLineIds, String fiscalYearId) {
    String transactionTypeFilter = "transactionType==Encumbrance";
    String fiscalYearFilter = fiscalYearId == null ? "" : String.format(" AND fiscalYearId==%s", fiscalYearId);
    String idFilter = " AND " + convertIdsToCqlQuery(poLineIds, "encumbrance.sourcePoLineId", true);
    return String.format("%s%s%s", transactionTypeFilter, fiscalYearFilter, idFilter);
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
      .onFailure(t -> logger.error("Error updating invoice lines encumbrance links, invoiceId={}, fiscalYearId={}",
        relevantHolders.getFirst().getInvoice().getId(), fiscalYearId, t));
  }

  private List<String> getPoLineIds(List<InvoiceWorkflowDataHolder> holders) {
    return holders.stream()
      .map(holder -> holder.getInvoiceLine().getPoLineId())
      .distinct()
      .collect(toList());
  }

  private List<InvoiceLine> updateFundDistributionsWithEncumbrances(List<InvoiceWorkflowDataHolder> holders,
                                                                    List<Transaction> encumbrances) {
    var linesToUpdate = new ArrayList<InvoiceLine>();
    for (InvoiceWorkflowDataHolder holder : holders) {
      var matchingEncumbrances = encumbrances.stream()
        .filter(enc -> shouldChangeFundDistributions(holder, enc))
        .collect(Collectors.toCollection(ArrayList::new));
      var fundDistribution = holder.getFundDistribution();
      if (matchingEncumbrances.isEmpty()) {
        if (fundDistribution.getEncumbrance() != null) {
          fundDistribution.withEncumbrance(null);
          holder.withEncumbrance(null);
          if (!linesToUpdate.contains(holder.getInvoiceLine())) {
            linesToUpdate.add(holder.getInvoiceLine());
          }
        }
      } else {
        var encumbrance = matchingEncumbrances.getFirst();
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

  private boolean shouldChangeFundDistributions(InvoiceWorkflowDataHolder holder, Transaction enc) {
    var isMatchPoLineId = enc.getEncumbrance().getSourcePoLineId().equals(holder.getInvoiceLine().getPoLineId());
    var isMatchFundId = enc.getFromFundId().equals(holder.getFundId());
    logger.info("shouldChangeFundDistributions:: Matching poLineId={}, fundId={}", isMatchPoLineId, isMatchFundId);
    return isMatchPoLineId && isMatchFundId;
  }
}
