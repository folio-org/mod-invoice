package org.folio.services.finance.transaction;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.PENDING_PAYMENT_ERROR;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.RELEASED;
import static org.folio.rest.acq.model.finance.Encumbrance.Status.UNRELEASED;
import static org.folio.services.FundsDistributionService.distributeFunds;

import javax.money.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.Encumbrance;
import org.folio.rest.acq.model.finance.Metadata;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.validator.HolderValidator;

@Log4j2
public class PendingPaymentWorkflowService {

  private final BaseTransactionService baseTransactionService;
  private final EncumbranceService encumbranceService;
  private final HolderValidator holderValidator;

  public PendingPaymentWorkflowService(BaseTransactionService baseTransactionService,
                                       EncumbranceService encumbranceService,
                                       HolderValidator validator) {
    this.baseTransactionService = baseTransactionService;
    this.encumbranceService = encumbranceService;
    this.holderValidator = validator;
  }

  public Future<List<InvoiceWorkflowDataHolder>> handlePendingPaymentsCreation(List<InvoiceWorkflowDataHolder> dataHolders,
      Invoice invoice, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPendingPayments(dataHolders);
    holderValidator.validate(holders);
    List<Transaction> transactionsToCreate = getPendingPaymentsToCreate(holders);
    return getOldEncumbrancesToRelease(holders, invoice, requestContext)
      .compose(transactionsToUpdate -> {
        if (transactionsToCreate == null && transactionsToUpdate == null) {
          return succeededFuture();
        }
        return baseTransactionService.batchAllOrNothing(transactionsToCreate, transactionsToUpdate,
          null, null, requestContext);
      })
      .recover(t -> {
        log.error("handlePendingPaymentsCreation: Failed to create pending payments and release old encumbrances", t);
        if (t instanceof HttpException he) {
          return failedFuture(new HttpException(he.getCode(), he.getErrors()));
        }
        var causeParam = new Parameter().withKey("cause").withValue(t.getMessage());
        Error error = PENDING_PAYMENT_ERROR.toError().withParameters(List.of(causeParam));
        return failedFuture(new HttpException(500, error));
      })
      .map(v -> dataHolders);
  }

  public Future<Void> handlePendingPaymentsUpdate(List<InvoiceWorkflowDataHolder> dataHolders, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> holders = withNewPendingPayments(dataHolders);
    holderValidator.validate(holders);
    return updateTransactions(holders, requestContext);
  }

  public Future<Void> rollbackCreationOfPendingPayments(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    // NOTE: we are using the holders with the assumption that their encumbrances were not updated when encumbrances were released
    List<Transaction> pendingPayments = holders.stream()
      .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .filter(t -> t.getTransactionType() == Transaction.TransactionType.PENDING_PAYMENT)
      .toList();
    return baseTransactionService.batchDelete(pendingPayments, requestContext)
      .onSuccess(v -> log.info("rollbackCreationOfPendingPayments:: deleted {} pending payments", pendingPayments.size()))
      .compose(v -> unreleaseReleasedEncumbrances(holders, requestContext))
      .onSuccess(v -> log.info("rollbackCreationOfPendingPayments:: success"))
      .onFailure(t -> log.error("rollbackCreationOfPendingPayments:: error", t));
  }

  private List<Transaction> getPendingPaymentsToCreate(List<InvoiceWorkflowDataHolder> holders) {
    if (CollectionUtils.isEmpty(holders)) {
      return null;
    }
    return holders.stream()
      .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .toList();
  }

  private Future<List<Transaction>> getOldEncumbrancesToRelease(List<InvoiceWorkflowDataHolder> holders, Invoice invoice,
      RequestContext requestContext) {
    List<String> poLineIds = holders.stream()
      .filter(holder -> holder.getInvoiceLine() != null)
      .map(holder -> holder.getInvoiceLine().getPoLineId())
      .collect(toList());
    String fiscalYearId = invoice.getFiscalYearId();
    return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, fiscalYearId, requestContext)
      .map(poLineTransactions -> {
        List<Transaction> transactionsToRelease = poLineTransactions.stream()
          .filter(tr -> !RELEASED.equals(tr.getEncumbrance().getStatus()) &&
            holders.stream()
              .filter(holder -> Objects.nonNull(holder.getInvoiceLine()))
              .noneMatch(holder -> sameFundAndPoLine(tr, holder)))
          .toList();
        if (transactionsToRelease.isEmpty()) {
          return null;
        }
        // NOTE: we will have to use transactionPatches when it is available (see MODINVOICE-521)
        transactionsToRelease.forEach(tr -> tr.getEncumbrance().setStatus(Encumbrance.Status.RELEASED));
        return transactionsToRelease;
      });
  }

  private boolean sameFundAndPoLine(Transaction transaction, InvoiceWorkflowDataHolder holder) {
    return transaction.getFromFundId().equals(holder.getFundId())
      && transaction.getEncumbrance().getSourcePoLineId().equals(holder.getInvoiceLine().getPoLineId());
  }

  private List<InvoiceWorkflowDataHolder> withNewPendingPayments(List<InvoiceWorkflowDataHolder> dataHolders) {
    List<InvoiceWorkflowDataHolder> invoiceWorkflowDataHolders = dataHolders.stream()
      .map(holder -> holder.withNewTransaction(buildTransaction(holder)))
      .collect(toList());
    return distributeFunds(invoiceWorkflowDataHolders);
  }

  private Future<Void> updateTransactions(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<Transaction> transactionsToUpdate = holders.stream()
      .map(InvoiceWorkflowDataHolder::getNewTransaction)
      .toList();
    return baseTransactionService.batchUpdate(transactionsToUpdate, requestContext);
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
    Metadata metadata = Optional.ofNullable(holder.getExistingTransaction()).map(tr ->
      JsonObject.mapFrom(tr.getMetadata()).mapTo(Metadata.class)).orElse(null);
    return buildBaseTransaction(holder)
      .withId(transactionId)
      .withVersion(version)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT)
      .withAwaitingPayment(awaitingPayment)
      .withAmount(convertToDoubleWithRounding(amount))
      .withSourceInvoiceLineId(holder.getInvoiceLineId())
      .withMetadata(metadata);
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

  private Future<Void> unreleaseReleasedEncumbrances(List<InvoiceWorkflowDataHolder> holders, RequestContext requestContext) {
    List<Transaction> previousUnreleasedEncumbrances = holders.stream()
      .map(InvoiceWorkflowDataHolder::getEncumbrance)
      .filter(t -> Objects.nonNull(t) && t.getEncumbrance().getStatus() == UNRELEASED)
      .toList();
    if (previousUnreleasedEncumbrances.isEmpty()) {
      log.info("unreleaseReleasedEncumbrances:: no encumbrance to unrelease (no encumbrance was unreleased)");
      return succeededFuture();
    }
    List<String> ids = previousUnreleasedEncumbrances.stream().map(Transaction::getId).toList();
    return baseTransactionService.getTransactionsByIds(ids, requestContext)
      .compose(newEncumbrances -> {
        List<Transaction> encumbrancesToUnrelease = newEncumbrances.stream()
          .filter(t -> t.getEncumbrance().getStatus() == RELEASED)
          .toList();
        if (encumbrancesToUnrelease.isEmpty()) {
          log.info("unreleaseReleasedEncumbrances:: no encumbrance to unrelease (unreleased encumbrances have not been released)");
          return succeededFuture();
        }
        return baseTransactionService.batchUnrelease(encumbrancesToUnrelease, requestContext)
          .onSuccess(v -> log.info("unreleaseReleasedEncumbrances:: unreleased {} encumbrances",
            encumbrancesToUnrelease.size()));
      })
      .onFailure(t -> log.error("unreleaseReleasedEncumbrances:: error", t));
  }
}
