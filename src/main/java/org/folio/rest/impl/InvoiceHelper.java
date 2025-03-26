package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.AcqDesiredPermissions.BYPASS_ACQ_UNITS;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_RESET_INVOICE_FISCAL_YEAR;
import static org.folio.invoices.utils.ErrorCodes.INVALID_INVOICE_TRANSITION_ON_PAID_STATUS;
import static org.folio.invoices.utils.ErrorCodes.MULTIPLE_ADJUSTMENTS_FISCAL_YEARS;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.HelperUtils.isTransitionToApproved;
import static org.folio.invoices.utils.HelperUtils.isTransitionToCancelled;
import static org.folio.invoices.utils.HelperUtils.isTransitionToPaid;
import static org.folio.invoices.utils.ProtectedOperationType.UPDATE;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.utils.UserPermissionsUtil.userHasDesiredPermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasAssignPermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasFiscalYearUpdatePermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasInvoiceApprovePermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasInvoiceCancelPermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasInvoicePayPermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasManagePermission;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceRestrictionsUtil;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.finance.FiscalYear;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.invoice.InvoiceApprovalService;
import org.folio.services.invoice.InvoiceCancelService;
import org.folio.services.invoice.InvoiceFiscalYearsService;
import org.folio.services.invoice.InvoiceFundDistributionService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.invoice.PoLinePaymentStatusUpdateService;
import org.folio.services.validator.InvoiceValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherCreationService;
import org.folio.services.voucher.VoucherService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class InvoiceHelper extends AbstractHelper {
  private final ProtectionHelper protectionHelper;
  @Autowired
  private AdjustmentsService adjustmentsService;
  @Autowired
  private InvoiceValidator validator;
  @Autowired
  private InvoiceLineService invoiceLineService;
  @Autowired
  private InvoiceService invoiceService;
  @Autowired
  private EncumbranceService encumbranceService;
  @Autowired
  private VoucherCommandService voucherCommandService;
  @Autowired
  private CurrentFiscalYearService currentFiscalYearService;
  @Autowired
  private PendingPaymentWorkflowService pendingPaymentWorkflowService;
  @Autowired
  private InvoiceWorkflowDataHolderBuilder holderBuilder;
  @Autowired
  private InvoiceCancelService invoiceCancelService;
  @Autowired
  private VoucherService voucherService;
  @Autowired
  private InvoiceFiscalYearsService invoiceFiscalYearsService;
  @Autowired
  private InvoiceApprovalService invoiceApprovalService;
  @Autowired
  private InvoiceFundDistributionService invoiceFundDistributionService;
  @Autowired
  private VoucherCreationService voucherCreationService;
  @Autowired
  private PaymentCreditWorkflowService paymentCreditWorkflowService;
  @Autowired
  private PoLinePaymentStatusUpdateService poLinePaymentStatusUpdateService;
  private RequestContext requestContext;

  public InvoiceHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.protectionHelper = new ProtectionHelper(okapiHeaders, ctx);
    this.requestContext = new RequestContext(ctx, okapiHeaders);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  public Future<Invoice> createInvoice(Invoice invoice, RequestContext requestContext) {
    //nextInvoiceLineNumber should be ignored if it exists in request payload and should be generated by DB hooks only [MODINVOICE-493].
    invoice.setNextInvoiceLineNumber(null);

    return Future.succeededFuture()
      .map(v -> {
        validator.validateIncomingInvoice(invoice);
        return null;
      })
      .compose(v -> validateAcqUnitsOnCreate(invoice.getAcqUnitIds()))
      .compose(v -> updateWithSystemGeneratedData(invoice))
      .compose(v -> validateFiscalYearId(invoice, requestContext))
      .compose(v -> invoiceService.createInvoice(invoice, requestContext))
      .map(Invoice::getId)
      .map(invoice::withId);
  }

  private Future<Void> validateFiscalYearId(Invoice invoice, RequestContext requestContext) {
    if (StringUtils.isNotEmpty(invoice.getFiscalYearId())) {
      return succeededFuture();
    }

    Set<String> fundIds = invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream())
      .map(FundDistribution::getFundId)
      .collect(Collectors.toSet());

    if (CollectionUtils.isNotEmpty(fundIds)) {
      return HelperUtils.executeWithSemaphores(fundIds,
          fundId -> currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContext),
          requestContext)
        .map(fiscalYears -> {
          Set<FiscalYear> uniqueFiscalYears = new HashSet<>(fiscalYears);
          if (uniqueFiscalYears.size() > 1) {
            var parameters = uniqueFiscalYears.stream()
              .map(fiscalYear -> new Parameter().withKey("fiscalYearCode").withValue(fiscalYear.getCode()))
              .toList();
            logger.error("validateFiscalYearId:: More than one fiscal years found: invoice '{}'", invoice.getId());
            throw new HttpException(422, MULTIPLE_ADJUSTMENTS_FISCAL_YEARS, parameters);
          }
          invoice.setFiscalYearId(uniqueFiscalYears.stream().findFirst().get().getId());
          return null;
        });
    }

    return succeededFuture();
  }

  /**
   * @param acqUnitIds acquisitions units assigned to invoice from request
   * @return completable future completed successfully if all checks pass or exceptionally in case of error/restriction caused by
   * acquisitions units
   */
  private Future<Void> validateAcqUnitsOnCreate(List<String> acqUnitIds) {
    if (acqUnitIds.isEmpty()) {
      return succeededFuture(null);
    }
    return Future.succeededFuture()
      .map(v -> {
        verifyUserHasAssignPermission(acqUnitIds, okapiHeaders);
        return null;
      })
      .compose(v -> protectionHelper.verifyIfUnitsAreActive(acqUnitIds))
      .compose(ok -> protectionHelper.isOperationRestricted(acqUnitIds, ProtectedOperationType.CREATE));
  }


  /**
   * Gets invoice by id and calculates totals
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public Future<Invoice> getInvoice(String id) {
    var invoiceFuture = getInvoiceRecord(id);
    return invoiceFuture
      .compose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.READ))
      .compose(v -> invoiceService.recalculateTotals(invoiceFuture.result(), requestContext))
      .map(v -> invoiceFuture.result())
      .onFailure(t -> logger.error("Failed to get an Invoice by id={}", id, t.getCause()));
  }

  /**
   * Gets invoice by id without calculated totals
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public Future<Invoice> getInvoiceRecord(String id) {
    return invoiceService.getInvoiceById(id, new RequestContext(ctx, okapiHeaders));
  }

  /**
   * Gets list of invoice
   *
   * @param limit  Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query  A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link InvoiceCollection} on success or an exception if processing fails
   */
  public Future<InvoiceCollection> getInvoices(int limit, int offset, String query) {
    return buildGetInvoicesQuery(query)
      .compose(getInvoicesQuery -> invoiceService.getInvoices(getInvoicesQuery, offset, limit, requestContext))
      .compose(invoiceCollection -> invoiceService.updateInvoicesTotals(invoiceCollection, requestContext).map(invoiceCollection))
      .onFailure(t -> logger.error("Error getting invoices", t));
  }

  private Future<String> buildGetInvoicesQuery(String query) {
    if (userHasDesiredPermission(BYPASS_ACQ_UNITS, okapiHeaders)) {
      return succeededFuture(query);
    }
    return protectionHelper.buildAcqUnitsCqlExprToSearchRecords(INVOICES)
      .map(acqUnitsCqlExpr -> {
        if (isEmpty(query)) {
          return acqUnitsCqlExpr;
        } else {
          return combineCqlExpressions("and", acqUnitsCqlExpr, query);
        }
      });
  }

  /**
   * Delete Invoice
   * 1. Get invoice by id
   * 2. Verify if user has permission to delete Invoice based on acquisitions units
   * 3. Check if invoice status is not approved or paid
   * 4. If user has permission to delete and invoice is not approved or paid then delete invoiceLine
   *
   * @param id invoiceLine id to be deleted
   */
  public Future<Void> deleteInvoice(String id, RequestContext requestContext) {
    return getInvoiceRecord(id)
      .compose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.DELETE)
        .map(vVoid -> invoice))
      .compose(InvoiceRestrictionsUtil::checkIfInvoiceDeletionPermitted)
      .compose(invoice -> invoiceService.deleteInvoice(id, requestContext));
  }

  /**
   * Handles update of the invoice. First retrieve the invoice from storage, validate, handle invoice status transition and update
   * to storage.
   *
   * @param invoice updated {@link Invoice} invoice
   * @param poLinePaymentStatus - paymentStatus to use to update po lines when approving or cancelling invoices (optional)
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public Future<Void> updateInvoice(Invoice invoice, String poLinePaymentStatus) {
    logger.debug("Updating invoice with id {}", invoice.getId());
    return Future.succeededFuture()
      .map(v -> {
        validator.validateIncomingInvoice(invoice);
        return null;
      })
      .compose(v -> getInvoiceRecord(invoice.getId()))
      .compose(invoiceFromStorage -> validateAndHandleInvoiceStatusTransition(invoice, invoiceFromStorage,
        poLinePaymentStatus))
      .compose(v -> validateFiscalYearId(invoice, requestContext))
      .compose(v -> updateInvoiceRecord(invoice));
  }

  public Future<FiscalYearCollection> getFiscalYearsByInvoiceId(String invoiceId) {
    return getInvoiceRecord(invoiceId)
      .compose(invoice ->
        protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.READ)
          .compose(v -> invoiceLineService.getInvoiceLinesByInvoiceId(invoiceId, requestContext))
          .map(InvoiceLineCollection::getInvoiceLines)
          .compose(lines -> invoiceFiscalYearsService.getFiscalYearsByInvoiceAndLines(invoice, lines, requestContext)))
      .onFailure(t -> logger.error("Error getting fiscal years for invoice {}", invoiceId, t));
  }


  private Future<Void> handleExchangeRateChange(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return holderBuilder.buildCompleteHolders(invoice, invoiceLines, requestContext)
      .compose(holders -> holderBuilder.withExistingTransactions(holders, requestContext))
      .compose(holders -> pendingPaymentWorkflowService.handlePendingPaymentsUpdate(holders, requestContext))
      .compose(aVoid -> updateVoucher(invoice, invoiceLines));
  }

  private Future<Void> updateVoucher(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return voucherService.getVoucherByInvoiceId(invoice.getId(), requestContext)
      .compose(voucher -> {
        if (voucher != null) {
          return voucherCommandService.updateVoucherWithExchangeRate(voucher, invoice, requestContext)
            .compose(voucherP -> invoiceFundDistributionService.getAllFundDistributions(invoiceLines, invoice, requestContext)
              .compose(fundDistributions -> voucherCreationService.handleVoucherWithLines(fundDistributions,
                voucherP, requestContext)));
        }
        return succeededFuture(null);
      });
  }

  private Future<Void> validateAndHandleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage,
      String poLinePaymentStatus) {
    return validateAcqUnitsOnUpdate(invoice, invoiceFromStorage)
      .map(ok -> {
        validator.validateInvoice(invoice, invoiceFromStorage);
        verifyUserHasManagePermission(invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders);
        verifyUserHasFiscalYearUpdatePermission(invoice.getFiscalYearId(), invoiceFromStorage.getFiscalYearId(), okapiHeaders);
        verifyUserHasInvoiceApprovePermission(invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders);
        verifyUserHasInvoicePayPermission(invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders);
        verifyUserHasInvoiceCancelPermission(invoice.getStatus(), invoiceFromStorage.getStatus(), okapiHeaders);

        setSystemGeneratedData(invoiceFromStorage, invoice);
        return null;
      })
      .compose(v -> invoiceLineService.getInvoiceLinesWithTotals(invoice, requestContext))
      .compose(invoiceLines -> validator.validatePoLinePaymentStatusParameter(invoice, invoiceLines,
          invoiceFromStorage, poLinePaymentStatus, requestContext)
        .map(v -> {
          List<InvoiceLine> updatedInvoiceLines = invoiceLines.stream()
            .map(invoiceLine -> JsonObject.mapFrom(invoiceLine).mapTo(InvoiceLine.class))
            .collect(toList());
          recalculateAdjustmentData(invoice, invoiceFromStorage, updatedInvoiceLines);
          invoiceService.recalculateTotals(invoice, updatedInvoiceLines);
          return updatedInvoiceLines;
        })
        .compose(updatedInvoiceLines -> handleInvoiceStatusTransition(invoice, invoiceFromStorage,
            updatedInvoiceLines, poLinePaymentStatus)
          .map(aVoid -> {
            updateInvoiceLinesStatus(invoice, updatedInvoiceLines);
            return null;
          })
          .map(aVoid -> filterUpdatedLines(invoiceLines, updatedInvoiceLines))
          .compose(lines -> invoiceLineService.persistInvoiceLines(lines, requestContext)))
        .compose(lines -> updateEncumbranceLinksWhenFiscalYearIsChanged(invoice, invoiceFromStorage, invoiceLines)));
  }

  private List<InvoiceLine> filterUpdatedLines(List<InvoiceLine> invoiceLines, List<InvoiceLine> updatedInvoiceLines) {
    Map<String, InvoiceLine> idLineMap = invoiceLines.stream().collect(toMap(InvoiceLine::getId, Function.identity()));
    return updatedInvoiceLines.stream().filter(invoiceLine -> !invoiceLine.equals(idLineMap.get(invoiceLine.getId()))).collect(toList());
  }

  /**
   * @param updatedInvoice   invoice from request
   * @param persistedInvoice invoice from storage
   * @return completable future completed successfully if all checks pass or exceptionally in case of error/restriction caused by
   * acquisitions units
   */
  private Future<Void> validateAcqUnitsOnUpdate(Invoice updatedInvoice, Invoice persistedInvoice) {
    if (userHasDesiredPermission(BYPASS_ACQ_UNITS, okapiHeaders)) {
      return Future.succeededFuture();
    }
    List<String> updatedAcqUnitIds = updatedInvoice.getAcqUnitIds();
    List<String> currentAcqUnitIds = persistedInvoice.getAcqUnitIds();
    verifyUserHasManagePermission(updatedAcqUnitIds, currentAcqUnitIds, okapiHeaders);
    // Check that all newly assigned units are active/exist
    return protectionHelper.verifyIfUnitsAreActive(ListUtils.subtract(updatedAcqUnitIds, currentAcqUnitIds))
      // The check should be done against currently assigned (persisted in storage) units
      .compose(protectedOperationTypes -> protectionHelper.isOperationRestricted(currentAcqUnitIds, UPDATE));
  }

  private Future<Void> updateWithSystemGeneratedData(Invoice invoice) {
    return invoiceService.generateFolioInvoiceNumber(buildRequestContext())
      .map(invoice::withFolioInvoiceNo)
      .map(invoiceWithNo -> {
        invoiceService.calculateTotals(invoiceWithNo);
        generateAdjustmentsIds(invoice);
        return null;
      });
  }

  private void generateAdjustmentsIds(Invoice invoice) {
    invoice.getAdjustments()
      .stream()
      .filter(adj -> adj.getId() == null)
      .forEach(adjustment -> adjustment.setId(randomUUID().toString()));
  }

  private void recalculateAdjustmentData(Invoice updatedInvoice, Invoice invoiceFromStorage, List<InvoiceLine> invoiceLines) {
    // If invoice was approved, the totals are already fixed and should not be recalculated
    if (!isPostApproval(invoiceFromStorage)) {
      processProratedAdjustments(updatedInvoice, invoiceFromStorage, invoiceLines);
    }
  }

  private void processProratedAdjustments(Invoice updatedInvoice, Invoice invoiceFromStorage, List<InvoiceLine> lines) {
    List<Adjustment> currentAdjustments = adjustmentsService.getProratedAdjustments(updatedInvoice);

    // Skip if prorated adjustments are the same in incoming invoice and from storage
    if (CollectionUtils.isEqualCollection(currentAdjustments, adjustmentsService.getProratedAdjustments(invoiceFromStorage))) {
      return;
    }
    adjustmentsService.processProratedAdjustments(lines, updatedInvoice);
  }

  private Future<Void> updateInvoiceRecord(Invoice updatedInvoice) {
    return invoiceService.updateInvoice(updatedInvoice, new RequestContext(ctx, okapiHeaders));
  }

  private void updateInvoiceLinesStatus(Invoice invoice, List<InvoiceLine> invoiceLines) {
    invoiceLines.forEach(invoiceLine -> invoiceLine.withInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.fromValue(invoice.getStatus().value())));
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
    generateAdjustmentsIds(invoice);
  }

  private Future<Void> handleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage,
      List<InvoiceLine> invoiceLines, String poLinePaymentStatus) {
    verifyTransitionOnPaidStatus(invoiceFromStorage, invoice);
    if (isTransitionToApproved(invoiceFromStorage, invoice)) {
      return invoiceApprovalService.approveInvoice(invoice, invoiceLines, poLinePaymentStatus, requestContext);
    } else if (isAfterApprove(invoice, invoiceFromStorage) && isExchangeRateChanged(invoice, invoiceFromStorage)) {
      return handleExchangeRateChange(invoice, invoiceLines);
    } else if (isTransitionToPaid(invoiceFromStorage, invoice)) {
      if (isExchangeRateChanged(invoice, invoiceFromStorage)) {
        return handleExchangeRateChange(invoice, invoiceLines)
          .compose(v -> payInvoice(invoice, invoiceLines, poLinePaymentStatus, requestContext));
      }
      invoice.setExchangeRate(invoiceFromStorage.getExchangeRate());
      return payInvoice(invoice, invoiceLines, poLinePaymentStatus, requestContext);
    } else if (isTransitionToCancelled(invoiceFromStorage, invoice)) {
      return invoiceCancelService.cancelInvoice(invoiceFromStorage, invoiceLines, poLinePaymentStatus, requestContext);
    }
    return succeededFuture(null);
  }

  private void verifyTransitionOnPaidStatus(Invoice invoiceFromStorage, Invoice invoice) {
    // Once an invoice is Paid, it should no longer transition to other statuses, except Cancelled.
    if (invoiceFromStorage.getStatus() == Invoice.Status.PAID && invoice.getStatus() != Invoice.Status.CANCELLED &&
        invoice.getStatus() != invoiceFromStorage.getStatus()) {
      var parameter = new Parameter().withKey("invoiceId").withValue(invoice.getId());
      logger.error("verifyTransitionOnPaidStatus:: Invalid invoice '{}' transition on paid status", invoice.getId());
      throw new HttpException(422, INVALID_INVOICE_TRANSITION_ON_PAID_STATUS, List.of(parameter));
    }
  }

  private boolean isAfterApprove(Invoice invoice, Invoice invoiceFromStorage) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.APPROVED;
  }

  private boolean isExchangeRateChanged(Invoice invoice, Invoice invoiceFromStorage) {
    return Objects.nonNull(invoice.getExchangeRate()) && !invoice.getExchangeRate().equals(invoiceFromStorage.getExchangeRate());
  }

  /**
   * Handles transition of given invoice to PAID status.
   */
  private Future<Void> payInvoice(Invoice invoice, List<InvoiceLine> invoiceLines, String poLinePaymentStatus,
    RequestContext requestContext) {
    //  Set payment date, when the invoice is being paid.
    invoice.setPaymentDate(invoice.getMetadata().getUpdatedDate());
    return holderBuilder.buildCompleteHolders(invoice, invoiceLines, requestContext)
      .compose(holders -> paymentCreditWorkflowService.handlePaymentsAndCreditsCreation(holders, requestContext))
      .compose(v -> Future.join(
        poLinePaymentStatusUpdateService.updatePoLinePaymentStatusToPayInvoice(invoiceLines, poLinePaymentStatus, requestContext),
        voucherService.payInvoiceVoucher(invoice.getId(), requestContext)))
      .mapEmpty();
  }

  private Future<Void> updateEncumbranceLinksWhenFiscalYearIsChanged(Invoice invoice, Invoice invoiceFromStorage,
                                                                     List<InvoiceLine> lines) {
    String previousFiscalYearId = invoiceFromStorage.getFiscalYearId();
    String newFiscalYearId = invoice.getFiscalYearId();
    if (Objects.equals(newFiscalYearId, previousFiscalYearId)) {
      return succeededFuture(null);
    }
    if (newFiscalYearId == null) {
      var invoiceIdParam = new Parameter().withKey("invoiceId").withValue(invoice.getId());
      logger.error("validateFiscalYearId:: newFiscalYearId is null. Cannot reset invoice '{}' fiscal year", invoice.getId());
      throw new HttpException(422, CANNOT_RESET_INVOICE_FISCAL_YEAR, List.of(invoiceIdParam));
    }
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withEncumbrances(dataHolders, requestContext)
      .compose(holders -> encumbranceService.updateInvoiceLinesEncumbranceLinks(holders, newFiscalYearId, requestContext))
      .compose(linesToUpdate -> invoiceLineService.persistInvoiceLines(linesToUpdate, requestContext));
  }

}
