package org.folio.services.finance.transaction;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.PENDING_PAYMENT_ERROR;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.services.FundsDistributionService.distributeFunds;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.money.MonetaryAmount;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.validator.HolderValidator;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertxconcurrent.Semaphore;

public class PendingPaymentWorkflowService {

  private static final Logger logger = LogManager.getLogger(PendingPaymentWorkflowService.class);

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final InvoiceTransactionSummaryService invoiceTransactionSummaryService;
  private final HolderValidator holderValidator;


  public PendingPaymentWorkflowService(BaseTransactionService baseTransactionService,
                                       EncumbranceService encumbranceService,
                                       InvoiceTransactionSummaryService invoiceTransactionSummaryService,
                                       HolderValidator validator) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.invoiceTransactionSummaryService = invoiceTransactionSummaryService;
    this.holderValidator = validator;

  }

  public Future<Void> handlePendingPaymentsCreation(List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPendingPayments(dataHolders);
    holderValidator.validate(holders);
    InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(holders);
    return invoiceTransactionSummaryService.createInvoiceTransactionSummary(summary, requestContext)
      .compose(s -> createPendingPayments(holders, requestContext))
      .compose(s -> cleanupOldEncumbrances(holders, requestContext));
  }

  public Future<Void> handlePendingPaymentsUpdate(List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPendingPayments(dataHolders);
    holderValidator.validate(holders);
    InvoiceTransactionSummary invoiceTransactionSummary = buildInvoiceTransactionsSummary(holders);
    return invoiceTransactionSummaryService.updateInvoiceTransactionSummary(invoiceTransactionSummary, requestContext)
      .compose(aVoid -> updateTransactions(holders, requestContext));
  }

  private InvoiceTransactionSummary buildInvoiceTransactionsSummary(List<InvoiceWorkflowDataHolder> holders) {
    return holders.stream().findFirst().map(InvoiceWorkflowDataHolder::getInvoice).map(Invoice::getId)
            .map(invoiceId -> new InvoiceTransactionSummary()
                    .withId(invoiceId)
                    .withNumPendingPayments(holders.size())
                    .withNumPaymentsCredits(holders.size()))
            .orElse(null);
  }

  private Future<Void> createPendingPayments(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(holders)) {
      return Future.succeededFuture();
    }

    return requestContext.getContext()
      .<List<Future<Void>>>executeBlocking(promise -> {
        Semaphore semaphore = new Semaphore(1, Vertx.currentContext().owner());
        List<Future<Void>> futures = new ArrayList<>();

        for (InvoiceWorkflowDataHolder holder : holders) {
          Transaction pendingPayment = holder.getNewTransaction();
          semaphore.acquire(() -> {
            Future<Void> future = baseTransactionService.createTransaction(pendingPayment, requestContext)
              .recover(t -> {
                logger.error("Failed to create pending payment with id {}", pendingPayment.getId(), t);
                throw new HttpException(500, PENDING_PAYMENT_ERROR.toError());
              })
              .onComplete(asyncResult -> semaphore.release())
              .mapEmpty();

            futures.add(future);
            // complete executeBlocking promise when all operations started
            if (futures.size() == holders.size()) {
              promise.complete(futures);
            }
          });
        }
      })
      .compose(GenericCompositeFuture::join)
      .mapEmpty();
  }

  private Future<Void> cleanupOldEncumbrances(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<String> poLineIds = holders.stream().filter(holder -> holder.getInvoiceLine() != null).map(holder -> holder.getInvoiceLine().getPoLineId()).collect(toList());
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, requestContext)
      .compose(transactions -> cleanupOldEncumbrances(transactions, holders, requestContext));
  }

  private Future<Void> cleanupOldEncumbrances(List<Transaction> poLineTransactions, List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<Future<Void>> futures = new ArrayList<>();

    for (Transaction transaction : poLineTransactions) {
      boolean encumbranceIsNoLongerRelevant = holders.stream().noneMatch(holder -> sameFundAndPoLine(transaction, holder));
      if (encumbranceIsNoLongerRelevant) {
        futures.add(baseTransactionService.releaseEncumbrance(transaction, requestContext));
      }
    }

    return collectResultsOnSuccess(futures)
      .onSuccess(result -> logger.debug("Number of encumbrances released due to invoice lines fund distributions being different from PO lines fund distributions: {}", result.size()))
      .onFailure(result -> logger.error("Failed cleaning up unlinked encumbrances", result.getCause()))
      .mapEmpty();
  }

  private boolean sameFundAndPoLine(Transaction transaction, InvoiceWorkflowDataHolder holder) {
    return transaction.getFromFundId().equals(holder.getFundId()) && transaction.getEncumbrance().getSourcePoLineId().equals(holder.getInvoiceLine().getPoLineId());
  }

  private List<InvoiceWorkflowDataHolder> withNewPendingPayments(List<InvoiceWorkflowDataHolder> dataHolders) {
    List<InvoiceWorkflowDataHolder> invoiceWorkflowDataHolders = dataHolders.stream()
      .map(holder -> holder.withNewTransaction(buildTransaction(holder)))
      .collect(toList());
    return distributeFunds(invoiceWorkflowDataHolders);
  }

  private Future<Void> updateTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    var futures = holders.stream()
      .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .map(transaction -> baseTransactionService.updateTransaction(transaction, requestContext))
      .collect(Collectors.toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  private Transaction buildTransaction(InvoiceWorkflowDataHolder holder)  {
    MonetaryAmount amount = getFundDistributionAmount(holder.getFundDistribution(), holder.getTotal(), holder.getInvoiceCurrency()).with(holder.getConversion());
    AwaitingPayment awaitingPayment = null;
    FundDistribution fundDistribution = holder.getFundDistribution();

    if (fundDistribution.getEncumbrance() != null) {
      boolean releaseEncumbrance = Optional.ofNullable(holder.getInvoiceLine()).map(InvoiceLine::getReleaseEncumbrance).orElse(false);
      awaitingPayment = new AwaitingPayment()
              .withEncumbranceId(fundDistribution.getEncumbrance())
              .withReleaseEncumbrance(releaseEncumbrance);
    }
    String transactionId = Optional.ofNullable(holder.getExistingTransaction()).map(Transaction::getId).orElse(null);
    Integer version = Optional.ofNullable(holder.getExistingTransaction()).map(Transaction::getVersion).orElse(null);
    return buildBaseTransaction(holder)
            .withId(transactionId)
            .withVersion(version)
            .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
            .withAwaitingPayment(awaitingPayment)
            .withAmount(convertToDoubleWithRounding(amount))
            .withSourceInvoiceLineId(holder.getInvoiceLineId());

  }

  protected Transaction buildBaseTransaction(InvoiceWorkflowDataHolder holder) {

    return new Transaction()
            .withSource(Transaction.Source.INVOICE)
            .withCurrency(holder.getFyCurrency())
            .withFiscalYearId(holder.getFiscalYear().getId())
            .withSourceInvoiceId(holder.getInvoice().getId())
            .withFromFundId(holder.getFundId())
            .withExpenseClassId(holder.getExpenseClassId());
  }

}
