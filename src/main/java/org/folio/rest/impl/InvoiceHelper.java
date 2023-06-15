package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.money.Monetary.getDefaultRounding;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_RESET_INVOICE_FISCAL_YEAR;
import static org.folio.invoices.utils.ErrorCodes.INVALID_INVOICE_TRANSITION_ON_PAID_STATUS;
import static org.folio.invoices.utils.ErrorCodes.ORG_IS_NOT_VENDOR;
import static org.folio.invoices.utils.ErrorCodes.ORG_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherLineAmount;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.ProtectedOperationType.UPDATE;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.services.voucher.VoucherCommandService.VOUCHER_NUMBER_PREFIX_CONFIG_QUERY;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasAssignPermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasManagePermission;
import static org.folio.utils.UserPermissionsUtil.verifyUserHasFiscalYearUpdatePermission;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRateProvider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceRestrictionsUtil;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.models.FundExtNoExpenseClassExtNoPair;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.services.VendorRetrieveService;
import org.folio.services.VoucherLineService;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.finance.FundService;
import org.folio.services.finance.budget.BudgetExpenseClassService;
import org.folio.services.finance.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.fiscalyear.CurrentFiscalYearService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.finance.transaction.PendingPaymentWorkflowService;
import org.folio.services.invoice.InvoiceCancelService;
import org.folio.services.invoice.InvoiceFiscalYearsService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoicePaymentService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.validator.InvoiceValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherService;
import org.folio.spring.SpringContextUtil;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.annotations.VisibleForTesting;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class InvoiceHelper extends AbstractHelper {
  private final ProtectionHelper protectionHelper;
  @Autowired
  private AdjustmentsService adjustmentsService;
  @Autowired
  private InvoiceValidator validator;
  @Autowired
  private InvoiceLineService invoiceLineService;
  @Autowired
  private BudgetExpenseClassService budgetExpenseClassService;
  @Autowired
  private ExpenseClassRetrieveService expenseClassRetrieveService;
  @Autowired
  private InvoiceService invoiceService;
  @Autowired
  private EncumbranceService encumbranceService;
  @Autowired
  private VoucherCommandService voucherCommandService;
  @Autowired
  private CurrentFiscalYearService currentFiscalYearService;
  @Autowired
  private ExchangeRateProviderResolver exchangeRateProviderResolver;
  @Autowired
  private PendingPaymentWorkflowService pendingPaymentWorkflowService;
  @Autowired
  private FundService fundService;
  @Autowired
  private InvoiceWorkflowDataHolderBuilder holderBuilder;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private VendorRetrieveService vendorService;
  @Autowired
  private InvoicePaymentService invoicePaymentService;
  @Autowired
  private InvoiceCancelService invoiceCancelService;
  @Autowired
  private VoucherService voucherService;
  @Autowired
  private VoucherLineService voucherLineService;
  @Autowired
  private InvoiceFiscalYearsService invoiceFiscalYearsService;
  private RequestContext requestContext;

  public InvoiceHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.protectionHelper = new ProtectionHelper(okapiHeaders, ctx);
    this.requestContext = new RequestContext(ctx, okapiHeaders);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  public Future<Invoice> createInvoice(Invoice invoice, RequestContext requestContext) {
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
      return succeededFuture(null);
    }

    String fundId = invoice.getAdjustments().stream()
      .flatMap(adjustment -> adjustment.getFundDistributions().stream())
      .map(FundDistribution::getFundId)
      .findFirst()
      .orElse(null);

    if (StringUtils.isNotEmpty(fundId)) {
      return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, requestContext)
        .map(fiscalYear -> {
          invoice.setFiscalYearId(fiscalYear.getId());
          return null;
        });
    }

    return succeededFuture(null);
  }

  /**
   * @param acqUnitIds acquisitions units assigned to invoice from request
   * @return completable future completed successfully if all checks pass or exceptionally in case of error/restriction caused by
   *         acquisitions units
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
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link InvoiceCollection} on success or an exception if processing fails
   */
  public Future<InvoiceCollection> getInvoices(int limit, int offset, String query) {
      return buildGetInvoicesQuery(query)
        .compose(getInvoicesQuery -> invoiceService.getInvoices(getInvoicesQuery, offset, limit, requestContext))
        .onSuccess(invoiceCollection -> logger.info("Successfully retrieved invoices: {}", invoiceCollection))
        .compose(invoiceCollection -> invoiceService.updateInvoicesTotals(invoiceCollection, requestContext).map(invoiceCollection))
        .onFailure(t -> logger.error("Error getting invoices", t));
  }

  private Future<String> buildGetInvoicesQuery(String query) {
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
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public Future<Void> updateInvoice(Invoice invoice) {
    logger.debug("Updating invoice...");
    return Future.succeededFuture()
      .map(v-> {
        validator.validateIncomingInvoice(invoice);
        return null;
      })
      .compose(v -> getInvoiceRecord(invoice.getId()))
      .compose(invoiceFromStorage -> validateAndHandleInvoiceStatusTransition(invoice, invoiceFromStorage))
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
    return getInvoiceWorkflowDataHolders(invoice, invoiceLines, requestContext)
            .compose(holders -> holderBuilder.withExistingTransactions(holders, requestContext))
            .compose(holders ->  pendingPaymentWorkflowService.handlePendingPaymentsUpdate(holders, requestContext))
            .compose(aVoid -> updateVoucher(invoice, invoiceLines));
  }

  private Future<Void> updateVoucher(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return voucherService.getVoucherByInvoiceId(invoice.getId(), requestContext)
      .compose(voucher -> {
        if (voucher != null) {
          return voucherCommandService.updateVoucherWithExchangeRate(voucher, invoice, requestContext)
            .compose(voucherP ->  getAllFundDistributions(invoiceLines, invoice)
                                            .compose(fundDistributions -> handleVoucherWithLines(fundDistributions, voucherP)));
        }
        return succeededFuture(null);
      });
  }

  private Future<Void> validateAndHandleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    return validateAcqUnitsOnUpdate(invoice, invoiceFromStorage)
      .map(ok -> {
        validator.validateInvoice(invoice, invoiceFromStorage);
        verifyUserHasManagePermission(invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds(), okapiHeaders);
        verifyUserHasFiscalYearUpdatePermission(invoice.getFiscalYearId(), invoiceFromStorage.getFiscalYearId(), okapiHeaders);

        setSystemGeneratedData(invoiceFromStorage, invoice);
        return null;
      })
      .compose(v -> invoiceLineService.getInvoiceLinesWithTotals(invoice, requestContext))
      .compose(invoiceLines -> Future.succeededFuture()
        .map(v -> {
          List<InvoiceLine> updatedInvoiceLines = invoiceLines.stream()
            .map(invoiceLine -> JsonObject.mapFrom(invoiceLine).mapTo(InvoiceLine.class))
            .collect(toList());
          recalculateAdjustmentData(invoice, invoiceFromStorage, updatedInvoiceLines);
          invoiceService.recalculateTotals(invoice, updatedInvoiceLines);
          return updatedInvoiceLines;
        })
        .compose(updatedInvoiceLines -> handleInvoiceStatusTransition(invoice, invoiceFromStorage, updatedInvoiceLines)
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
   *         acquisitions units
   */
  private Future<Void> validateAcqUnitsOnUpdate(Invoice updatedInvoice, Invoice persistedInvoice) {
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

  private void updateInvoiceLinesStatus(Invoice invoice, List<InvoiceLine> invoiceLines) {
    invoiceLines.forEach(invoiceLine -> invoiceLine.withInvoiceLineStatus(InvoiceLine.InvoiceLineStatus.fromValue(invoice.getStatus().value())));
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
    generateAdjustmentsIds(invoice);
  }

  private Future<Void> handleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage, List<InvoiceLine> invoiceLines) {
    if (isTransitionToApproved(invoiceFromStorage, invoice)) {
      return approveInvoice(invoice, invoiceLines);
    } else if (isAfterApprove(invoice, invoiceFromStorage) && isExchangeRateChanged(invoice, invoiceFromStorage)) {
      return handleExchangeRateChange(invoice, invoiceLines);
    } else if (isTransitionToPaid(invoiceFromStorage, invoice)) {
      if (isExchangeRateChanged(invoice, invoiceFromStorage)) {
        return handleExchangeRateChange(invoice, invoiceLines)
          .compose(aVoid1 -> invoicePaymentService.payInvoice(invoice, invoiceLines, requestContext));
      }
      invoice.setExchangeRate(invoiceFromStorage.getExchangeRate());
      return invoicePaymentService.payInvoice(invoice, invoiceLines, requestContext);
    } else if (isTransitionToCancelled(invoiceFromStorage, invoice)) {
      return invoiceCancelService.cancelInvoice(invoiceFromStorage, invoiceLines, requestContext);
    }
    return succeededFuture(null);
  }

  private boolean isAfterApprove(Invoice invoice, Invoice invoiceFromStorage) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.APPROVED;
  }

  private boolean isExchangeRateChanged(Invoice invoice, Invoice invoiceFromStorage) {
    return Objects.nonNull(invoice.getExchangeRate()) && !invoice.getExchangeRate().equals(invoiceFromStorage.getExchangeRate());
  }

  private void verifyTransitionOnPaidStatus(Invoice invoiceFromStorage, Invoice invoice) {
    // Once an invoice is Paid, it should no longer transition to other statuses, except Cancelled.
    if (invoiceFromStorage.getStatus() == Invoice.Status.PAID && invoice.getStatus() != Invoice.Status.CANCELLED &&
        invoice.getStatus() != invoiceFromStorage.getStatus()) {
      List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceId")
        .withValue(invoice.getId()));
      Error error = INVALID_INVOICE_TRANSITION_ON_PAID_STATUS.toError()
        .withParameters(parameters);
      throw new HttpException(422, error);
    }
  }

  private boolean isTransitionToApproved(Invoice invoiceFromStorage, Invoice invoice) {
    verifyTransitionOnPaidStatus(invoiceFromStorage, invoice);
    return invoice.getStatus() == Invoice.Status.APPROVED && !isPostApproval(invoiceFromStorage);
  }

  private boolean isTransitionToCancelled(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() != Invoice.Status.CANCELLED && invoice.getStatus() == Invoice.Status.CANCELLED;
  }

  /**
   * Handles transition of given invoice to {@link Invoice.Status#APPROVED} status.
   * Transition triggers if the current {@link Invoice.Status} is {@link Invoice.Status#REVIEWED} or {@link Invoice.Status#OPEN}
   * and exist at least one {@link InvoiceLine} associated with this invoice
   *
   * @param invoice {@link Invoice}to be approved
   * @return CompletableFuture that indicates when transition is completed
   */
  private Future<Void> approveInvoice(Invoice invoice, List<InvoiceLine> lines) {
    invoice.setApprovalDate(new Date());
    invoice.setApprovedBy(invoice.getMetadata().getUpdatedByUserId());

    return configurationService.getConfigurationsEntries(requestContext, SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_PREFIX_CONFIG_QUERY)
      .compose(v -> vendorService.getVendor(invoice.getVendorId(), requestContext))
      .map(organization -> {
        validateBeforeApproval(organization, invoice, lines);
        return null;
      })
      .compose(v -> getInvoiceWorkflowDataHolders(invoice, lines, requestContext))
      .compose(holders -> encumbranceService.updateInvoiceLinesEncumbranceLinks(holders,
          holders.get(0).getFiscalYear().getId(), requestContext)
        .compose(linesToUpdate -> invoiceLineService.persistInvoiceLines(linesToUpdate, requestContext))
        .map(v -> holders))
      .compose(holders -> budgetExpenseClassService.checkExpenseClasses(holders, requestContext))
      .compose(holders -> pendingPaymentWorkflowService.handlePendingPaymentsCreation(holders, invoice, requestContext))
      .compose(v -> prepareVoucher(invoice))
      .compose(voucher -> updateVoucherWithSystemCurrency(voucher, lines))
      .compose(voucher -> voucherCommandService.updateVoucherWithExchangeRate(voucher, invoice, requestContext))
      .compose(voucher -> getAllFundDistributions(lines, invoice)
                                  .compose(fundDistributions -> handleVoucherWithLines(fundDistributions, voucher))
      );

  }

  private void validateBeforeApproval(Organization organization, Invoice invoice, List<InvoiceLine> lines) {
    if (organization == null) {
      throw new HttpException(404, ORG_NOT_FOUND);
    } else {
      if (!organization.getIsVendor()) {
        throw new HttpException(400, ORG_IS_NOT_VENDOR);
      }
    }
    validator.validateBeforeApproval(invoice, lines);
  }

  private Future<List<InvoiceWorkflowDataHolder>> getInvoiceWorkflowDataHolders(Invoice invoice, List<InvoiceLine> lines, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withFunds(dataHolders, requestContext)
            .compose(holders -> holderBuilder.withLedgers(holders, requestContext))
            .compose(holders -> holderBuilder.withBudgets(holders, requestContext))
            .map(holderBuilder::checkMultipleFiscalYears)
            .compose(holders -> holderBuilder.withFiscalYear(holders, requestContext))
            .compose(holders -> holderBuilder.withEncumbrances(holders, requestContext))
            .compose(holders -> holderBuilder.withExpenseClasses(holders, requestContext))
            .compose(holders -> holderBuilder.withExchangeRate(holders, requestContext));
  }

  private Future<Voucher> updateVoucherWithSystemCurrency(Voucher voucher, List<InvoiceLine> lines) {
    if (!CollectionUtils.isEmpty(lines) && !CollectionUtils.isEmpty(lines.get(0).getFundDistributions())) {
      String fundId = lines.get(0).getFundDistributions().get(0).getFundId();
      return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, new RequestContext(ctx, okapiHeaders))
        .map(fiscalYear -> voucher.withSystemCurrency(fiscalYear.getCurrency()));
    }
    return configurationService.getSystemCurrency(requestContext)
                               .map(systemCurrency -> voucher.withSystemCurrency(systemCurrency));
  }

  private Future<List<FundDistribution>> getAllFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice) {
    return configurationService.getSystemCurrency(new RequestContext(ctx, okapiHeaders))
      .map(systemCurrency -> {
        ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, systemCurrency);
        ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, new RequestContext(ctx, okapiHeaders));
        CurrencyConversion conversion =  exchangeRateProvider.getCurrencyConversion(conversionQuery);
        List<FundDistribution> fundDistributions = getInvoiceLineFundDistributions(invoiceLines, invoice, conversion);
        fundDistributions.addAll(getAdjustmentFundDistributions(invoice, conversion));
        return fundDistributions;
      });
  }

  private List<FundDistribution> getInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice,
      CurrencyConversion conversion) {
    Map<InvoiceLine, List<FundDistribution>> fdsByLine = invoiceLines.stream()
      .collect(toMap(Function.identity(), InvoiceLine::getFundDistributions));

    return fdsByLine.entrySet()
        .stream()
      .map(invoiceLine -> {
        var invoiceLineTotalWithConversion = Money.of(invoiceLine.getKey().getTotal(), invoice.getCurrency()).with(conversion);

        List<FundDistribution> fds = invoiceLine.getValue()
          .stream().sequential()
          .map(fD -> JsonObject.mapFrom(fD)
            .mapTo(FundDistribution.class)
            .withInvoiceLineId(invoiceLine.getKey().getId())
            .withDistributionType(FundDistribution.DistributionType.AMOUNT)
            .withValue(getFundDistributionAmountWithConversion(fD, invoiceLineTotalWithConversion, conversion)))
      .collect(toList());

        return getRedistributedFunds(fds, invoiceLineTotalWithConversion, conversion);

      })
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private List<FundDistribution> getRedistributedFunds(List<FundDistribution> fds, MonetaryAmount expectedTotal, CurrencyConversion conversion) {
    MonetaryAmount actualDistributedAmount = fds.stream()
      .map(FundDistribution::getValue)
      .map(amount -> Money.of(amount, conversion.getCurrency()))
      .reduce(Money::add)
      .orElseGet(() -> Money.of(0d, conversion.getCurrency()));

    MonetaryAmount remainedAmount = expectedTotal.subtract(actualDistributedAmount);

    if (remainedAmount.isNegative()) {
      FundDistribution fdForUpdate = fds.get(0);
      // Subtract from the first fd
      var currentFdAmount = Money.of(fdForUpdate.getValue(), conversion.getCurrency());
      var updatedFdAmount = currentFdAmount.subtract(remainedAmount.abs());
      fdForUpdate.setValue(updatedFdAmount.getNumber().doubleValue());
    } else if (remainedAmount.isPositive()) {
      // add to the last fd
      FundDistribution fdForUpdate = fds.get(fds.size() - 1);
      var currentFdAmount = Money.of(fdForUpdate.getValue(), conversion.getCurrency());
      var updatedFdAmount = currentFdAmount.add(remainedAmount);
      fdForUpdate.setValue(updatedFdAmount.getNumber().doubleValue());
    }
    return fds;
  }

  private double getFundDistributionAmountWithConversion(FundDistribution fundDistribution, MonetaryAmount totalAmount, CurrencyConversion conversion) {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, totalAmount).with(conversion).with(getDefaultRounding());
    return amount.getNumber().doubleValue();
  }

  private List<FundDistribution> getAdjustmentFundDistributions(Invoice invoice, CurrencyConversion conversion) {
    return adjustmentsService.getNotProratedAdjustments(invoice).stream()
      .flatMap(adjustment -> adjustment.getFundDistributions()
        .stream()
        .map(fundDistribution -> JsonObject.mapFrom(fundDistribution).mapTo(FundDistribution.class)
            .withValue(getAdjustmentFundDistributionAmount(fundDistribution, adjustment, invoice).with(conversion).getNumber().doubleValue())
            .withDistributionType(FundDistribution.DistributionType.AMOUNT)
        )
      )
      .collect(toList());
  }

  /**
   * Prepares a new voucher or updates existing one for further processing
   *
   * @param invoice {@link Invoice} to be approved on the basis of which the voucher is prepared
   * @return completable future with {@link Voucher} on success
   */
  private Future<Voucher> prepareVoucher(Invoice invoice) {
    return voucherService.getVoucherByInvoiceId(invoice.getId(), new RequestContext(ctx, okapiHeaders))
      .compose(voucher -> {
        if (nonNull(voucher)) {
          return succeededFuture(voucher);
        }
        return voucherCommandService.buildNewVoucher(invoice, new RequestContext(ctx, okapiHeaders));
      })
      .map(voucher -> {
        invoice.setVoucherNumber(voucher.getVoucherNumber());
        voucher.setAcqUnitIds(invoice.getAcqUnitIds());
        return withRequiredFields(voucher, invoice);
      });
  }

  /**
   * Updates state of {@link Voucher} linked with processed {@link Invoice}
   *
   * @param voucher {@link Voucher} from voucher-storage related to processed invoice
   * @param invoice invoice {@link Invoice} to be approved
   * @return voucher
   */
  private Voucher withRequiredFields(Voucher voucher, Invoice invoice) {
    voucher.setVoucherDate(new Date());
    voucher.setInvoiceCurrency(invoice.getCurrency());
    voucher.setExportToAccounting(invoice.getExportToAccounting());
    voucher.setAccountingCode(invoice.getAccountingCode());
    voucher.setBatchGroupId(invoice.getBatchGroupId());
    voucher.setEnclosureNeeded(invoice.getEnclosureNeeded());
    voucher.setAccountNo(invoice.getAccountNo());
    voucher.setVendorId(invoice.getVendorId());
    voucher.setType(Voucher.Type.VOUCHER);
    voucher.setStatus(Voucher.Status.AWAITING_PAYMENT);

    return voucher;
  }

  /**
   *  Handles creation (or update) of prepared voucher and voucher lines creation
   *
   * @param fundDistributions {@link List<FundDistribution>} associated with processed invoice
   * @param voucher associated with processed invoice
   * @return CompletableFuture that indicates when handling is completed
   */
  private Future<Void> handleVoucherWithLines(List<FundDistribution> fundDistributions, Voucher voucher) {
    return groupFundDistrosByExternalAcctNo(fundDistributions)
      .map(fundDistrosGroupedByExternalAcctNo -> buildVoucherLineRecords(fundDistrosGroupedByExternalAcctNo, voucher))
      .compose(voucherLines -> {
        Double calculatedAmount = HelperUtils.calculateVoucherAmount(voucher, voucherLines);
        voucher.setAmount(calculatedAmount);
        return handleVoucher(voucher)
          .onSuccess(voucherWithId -> populateVoucherId(voucherLines, voucherWithId))
          .compose(v -> createVoucherLinesRecords(voucherLines));
      });
  }

  /**
   * Prepares the data necessary for the generation of voucher lines based on the invoice lines found
   *
   * @param fundDistributions {@link List<InvoiceLine>} associated with processed {@link Invoice}
   * @return {@link InvoiceLine#fundDistributions} grouped by {@link Fund#externalAccountNo}
   */
  private Future<Map<FundExtNoExpenseClassExtNoPair,  List<FundDistribution>>> groupFundDistrosByExternalAcctNo(List<FundDistribution> fundDistributions) {

    Map<String, List<FundDistribution>> fundDistrosGroupedByFundId = groupFundDistrosByFundId(fundDistributions);
    var groupedFundDistrosFuture = fundService.getFunds(fundDistrosGroupedByFundId.keySet(), new RequestContext(ctx, okapiHeaders))
      .map(this::groupFundsByExternalAcctNo);
    var fundsGroupedByExternalAcctNoFuture = groupFundDistrByFundIdByExpenseClassExtNo(fundDistributions);

    return CompositeFuture.join(groupedFundDistrosFuture, fundsGroupedByExternalAcctNoFuture)
      .map(cf -> mapExternalAcctNoToFundDistros(fundsGroupedByExternalAcctNoFuture.result(), groupedFundDistrosFuture.result()));
  }

  private Map<String, List<Fund>> groupFundsByExternalAcctNo(List<Fund> funds) {
    return funds.stream().collect(groupingBy(Fund::getExternalAccountNo));
  }

  private Map<FundExtNoExpenseClassExtNoPair,  List<FundDistribution>> mapExternalAcctNoToFundDistros(
    Map<String, Map<String, List<FundDistribution>>> fundDistrosGroupedByFundIdAndExpenseClassExtNo,
    Map<String, List<Fund>> fundsGroupedByExternalAccountNo) {
    Map<FundExtNoExpenseClassExtNoPair,  List<FundDistribution>> groupedFundDistribution = new HashMap<>();
    for (Map.Entry<String, List<Fund>> fundExternalAccountNoPair : fundsGroupedByExternalAccountNo.entrySet()) {
      String fundExternalAccountNo = fundExternalAccountNoPair.getKey();
      for (Fund fund : fundExternalAccountNoPair.getValue()) {
         Map<String, List<FundDistribution>> fundDistrsExpenseClassExtNo = fundDistrosGroupedByFundIdAndExpenseClassExtNo.get(fund.getId());
         for (Map.Entry<String, List<FundDistribution>> fundDistrs : fundDistrsExpenseClassExtNo.entrySet()) {
           String expenseClassExtAccountNo = fundDistrs.getKey();
           FundExtNoExpenseClassExtNoPair key = new FundExtNoExpenseClassExtNoPair(fundExternalAccountNo, expenseClassExtAccountNo);
           List<FundDistribution> fundDistributions = fundDistrs.getValue();
           updateFundDistributionsWithExpenseClassCode(fund, fundDistributions);
           Optional.ofNullable(groupedFundDistribution.get(key)).ifPresentOrElse(
             value -> value.addAll(fundDistributions), () -> groupedFundDistribution.put(key, fundDistributions));
         }
      }
    }
    return groupedFundDistribution;
  }

  private Map<String, List<FundDistribution>> groupFundDistrosByFundId(List<FundDistribution> fundDistributions) {
    return fundDistributions.stream()
      .collect(groupingBy(FundDistribution::getFundId));
  }

  private Future<Map<String, Map<String, List<FundDistribution>>>> groupFundDistrByFundIdByExpenseClassExtNo(List<FundDistribution> fundDistrs) {
    List<String> expenseClassIds = fundDistrs.stream()
      .map(FundDistribution::getExpenseClassId)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
    return expenseClassRetrieveService.getExpenseClasses(expenseClassIds, new RequestContext(ctx, okapiHeaders))
                               .map(expenseClasses -> expenseClasses.stream().collect(toMap(ExpenseClass::getId, Function.identity())))
                               .map(expenseClassByIds ->
                                 fundDistrs.stream()
                                   .map(fd -> updateWithExpenseClassCode(fd, expenseClassByIds))
                                   .collect(groupingBy(FundDistribution::getFundId,
                                     groupingBy(getExpenseClassExtNo(expenseClassByIds)))
                                 )
                               );
  }

  private FundDistribution updateWithExpenseClassCode(FundDistribution fundDistribution, Map<String, ExpenseClass> expenseClassByIds) {
    if (fundDistribution.getExpenseClassId() != null && !expenseClassByIds.isEmpty()) {
      String expenseClassName = expenseClassByIds.get(fundDistribution.getExpenseClassId()).getCode();
      fundDistribution.setCode(expenseClassName);
    } else {
      fundDistribution.setCode("");
    }
    return fundDistribution;
  }

  private void updateFundDistributionsWithExpenseClassCode(Fund fund, List<FundDistribution> fundDistributions) {
    fundDistributions.forEach(fundDistribution -> {
      String fundCode = isEmpty(fundDistribution.getCode()) ? fund.getCode() : fund.getCode() + "-" + fundDistribution.getCode();
      fundDistribution
        .setCode(fundCode);
    });
  }

  private Function<FundDistribution, String> getExpenseClassExtNo(Map<String, ExpenseClass> expenseClassByIds) {
    return fundDistrsP -> Optional.ofNullable(expenseClassByIds.get(fundDistrsP.getExpenseClassId()))
                                 .map(ExpenseClass::getExternalAccountNumberExt)
                                 .orElse(EMPTY);
  }

  private List<VoucherLine> buildVoucherLineRecords(Map<FundExtNoExpenseClassExtNoPair, List<FundDistribution>> fundDistroGroupedByExternalAcctNo, Voucher voucher) {

    return fundDistroGroupedByExternalAcctNo.entrySet().stream()
      .map(entry -> buildVoucherLineRecord(entry, voucher.getSystemCurrency()))
      .collect(Collectors.toList());
  }

  private VoucherLine buildVoucherLineRecord(Map.Entry<FundExtNoExpenseClassExtNoPair, List<FundDistribution>> fundDistroAcctNoEntry, String systemCurrency) {
    String externalAccountNumber = fundDistroAcctNoEntry.getKey().toString();
    List<FundDistribution> fundDistributions = fundDistroAcctNoEntry.getValue();

    double voucherLineAmount = calculateVoucherLineAmount(fundDistroAcctNoEntry.getValue(), systemCurrency);

    return new VoucherLine()
      .withExternalAccountNumber(externalAccountNumber)
      .withFundDistributions(fundDistributions)
      .withSourceIds(collectInvoiceLineIds(fundDistributions))
      .withAmount(voucherLineAmount);
  }

  private List<String> collectInvoiceLineIds(List<FundDistribution> fundDistributions) {
    return fundDistributions
      .stream()
      .filter(fundDistribution -> StringUtils.isNotEmpty(fundDistribution.getInvoiceLineId()))
      .map(FundDistribution::getInvoiceLineId)
      .distinct()
      .collect(toList());
  }

  /**
   * If {@link Voucher} has an id, then the record exists in the voucher-storage and must be updated,
   * otherwise a new {@link Voucher} record must be created.
   *
   * If {@link Voucher} record exists, it means that there may be voucher lines associated with this {@link Voucher} that should be deleted.
   *
   * @param voucher Voucher for handling
   * @return {@link Voucher} with id
   */
  private Future<Voucher> handleVoucher(Voucher voucher) {
    if (nonNull(voucher.getId())) {
      return voucherService.updateVoucher(voucher.getId(), voucher, new RequestContext(ctx, okapiHeaders))
        .compose(aVoid -> deleteVoucherLinesIfExist(voucher.getId()))
        .map(aVoid -> voucher);
    } else {
      return voucherService.createVoucher(voucher, new RequestContext(ctx, okapiHeaders));
    }
  }

  /**
   * Removes the voucher lines associated with the voucher, if present.
   *
   * @param voucherId Id of {@link Voucher} used to find the voucher lines.
   * @return CompletableFuture that indicates when deletion is completed
   */
  private Future<Void> deleteVoucherLinesIfExist(String voucherId) {
    return getVoucherLineIdsByVoucherId(voucherId)
      .compose(ids -> GenericCompositeFuture.join(ids.stream()
        .map(lineId-> voucherLineService.deleteVoucherLine(lineId, requestContext))
        .collect(toList())))
      .mapEmpty();

  }

  private Future<List<String>> getVoucherLineIdsByVoucherId(String voucherId) {
    String query = "voucherId==" + voucherId;
    return voucherLineService.getVoucherLines(Integer.MAX_VALUE, 0, query, requestContext)
      .map(voucherLineCollection ->  voucherLineCollection.getVoucherLines().
        stream()
        .map(org.folio.rest.acq.model.VoucherLine::getId)
        .collect(toList())
      );
  }

  private void populateVoucherId(List<VoucherLine> voucherLines, Voucher voucher) {
    voucherLines.forEach(voucherLine -> voucherLine.setVoucherId(voucher.getId()));
  }

  private Future<Void> createVoucherLinesRecords(List<VoucherLine> voucherLines) {
    var futures = voucherLines.stream()
      .map(lineId-> voucherLineService.createVoucherLine(lineId, requestContext))
      .collect(toList());
    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  public Future<Void> updateInvoiceRecord(Invoice updatedInvoice) {
    return invoiceService.updateInvoice(updatedInvoice, new RequestContext(ctx, okapiHeaders));
  }

  private boolean isTransitionToPaid(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.PAID;
  }

  private Future<Void> updateEncumbranceLinksWhenFiscalYearIsChanged(Invoice invoice, Invoice invoiceFromStorage,
      List<InvoiceLine> lines) {
    String previousFiscalYearId = invoiceFromStorage.getFiscalYearId();
    String newFiscalYearId = invoice.getFiscalYearId();
    if (Objects.equals(newFiscalYearId, previousFiscalYearId)) {
      return succeededFuture(null);
    }
    if (newFiscalYearId == null) {
      Parameter invoiceIdParam = new Parameter()
        .withKey("invoiceId")
        .withValue(invoice.getId());
      Error error = CANNOT_RESET_INVOICE_FISCAL_YEAR.toError()
        .withParameters(List.of(invoiceIdParam));
      throw new HttpException(422, error);
    }
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withEncumbrances(dataHolders, requestContext)
      .compose(holders -> encumbranceService.updateInvoiceLinesEncumbranceLinks(holders, newFiscalYearId, requestContext))
      .compose(linesToUpdate -> invoiceLineService.persistInvoiceLines(linesToUpdate, requestContext));
  }

  @VisibleForTesting
  boolean isPaymentStatusUpdateRequired(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus, CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus = compositePoLinesWithStatus.get(compositePoLine);
    return (!newPaymentStatus.equals(compositePoLine.getPaymentStatus()) &&
      !compositePoLine.getPaymentStatus().equals(CompositePoLine.PaymentStatus.ONGOING));
  }
}
