package org.folio.rest.impl;

import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.completedFuture;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.supplyBlockingAsync;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.AcqDesiredPermissions.ASSIGN;
import static org.folio.invoices.utils.AcqDesiredPermissions.MANAGE;
import static org.folio.invoices.utils.ErrorCodes.INVALID_INVOICE_TRANSITION_ON_PAID_STATUS;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
import static org.folio.invoices.utils.HelperUtils.calculateAdjustmentsTotal;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherLineAmount;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.getInvoicesFromStorage;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.ProtectedOperationType.UPDATE;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_PERMISSIONS;
import static org.folio.services.voucher.VoucherCommandService.VOUCHER_NUMBER_PREFIX_CONFIG_QUERY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ExchangeRateProvider;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.AcqDesiredPermissions;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceRestrictionsUtil;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.models.FundExtNoExpenseClassExtNoPair;
import org.folio.rest.acq.model.finance.ExpenseClass;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.expence.ExpenseClassRetrieveService;
import org.folio.services.finance.BudgetExpenseClassService;
import org.folio.services.finance.BudgetValidationService;
import org.folio.services.finance.CurrentFiscalYearService;
import org.folio.services.finance.FundService;
import org.folio.services.transaction.EncumbranceService;
import org.folio.services.transaction.PaymentCreditWorkflowService;
import org.folio.services.transaction.PendingPaymentWorkflowService;
import org.folio.services.validator.InvoiceValidator;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.folio.spring.SpringContextUtil;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class InvoiceHelper extends AbstractHelper {

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + SEARCH_PARAMS;
  private static final String EMPTY_ARRAY = "[]";

  private final InvoiceLineHelper invoiceLineHelper;
  private final VoucherLineHelper voucherLineHelper;
  private final ProtectionHelper protectionHelper;
  private AdjustmentsService adjustmentsService;
  private InvoiceValidator validator;
  @Autowired
  private BudgetExpenseClassService budgetExpenseClassService;
  @Autowired
  private ExpenseClassRetrieveService expenseClassRetrieveService;
  @Autowired
  private RestClient invoiceStorageRestClient;
  @Autowired
  private EncumbranceService encumbranceService;
  @Autowired
  private VoucherCommandService voucherCommandService;
  @Autowired
  private VoucherRetrieveService voucherRetrieveService;
  @Autowired
  private CurrentFiscalYearService currentFiscalYearService;
  @Autowired
  private ExchangeRateProviderResolver exchangeRateProviderResolver;
  @Autowired
  private PendingPaymentWorkflowService pendingPaymentWorkflowService;
  @Autowired
  private PaymentCreditWorkflowService paymentCreditWorkflowService;
  @Autowired
  private BudgetValidationService budgetValidationService;
  @Autowired
  private FundService fundService;

  public InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    this.invoiceLineHelper = new InvoiceLineHelper(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    this.voucherLineHelper = new VoucherLineHelper(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    this.protectionHelper = new ProtectionHelper(httpClient, okapiHeaders, ctx, lang);
    this.adjustmentsService = new AdjustmentsService();
    this.validator = new InvoiceValidator();
  }

  public InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang, ExpenseClassRetrieveService expenseClassRetrieveService,
                        VoucherCommandService voucherCommandService, VoucherRetrieveService voucherRetrieveService,
                            ExchangeRateProviderResolver exchangeRateProviderResolver) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    this.invoiceLineHelper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
    this.voucherLineHelper = new VoucherLineHelper(httpClient, okapiHeaders, ctx, lang);
    this.protectionHelper = new ProtectionHelper(httpClient, okapiHeaders, ctx, lang);
    this.adjustmentsService = new AdjustmentsService();
    this.validator = new InvoiceValidator();
    this.expenseClassRetrieveService = expenseClassRetrieveService;
    this.voucherCommandService = voucherCommandService;
    this.voucherRetrieveService = voucherRetrieveService;
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
  }

  public CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return CompletableFuture.completedFuture(null).thenRun(() -> validator.validateIncomingInvoice(invoice))
      .thenCompose(aVoid -> validateAcqUnitsOnCreate(invoice.getAcqUnitIds()))
      .thenCompose(v -> updateWithSystemGeneratedData(invoice))
      .thenCompose(v -> invoiceStorageRestClient.post(invoice, new RequestContext(ctx, okapiHeaders), Invoice.class))
      .thenApply(Invoice::getId)
      .thenApply(invoice::withId);
  }

  /**
   * @param acqUnitIds acquisitions units assigned to invoice from request
   * @return completable future completed successfully if all checks pass or exceptionally in case of error/restriction caused by
   *         acquisitions units
   */
  private CompletableFuture<Void> validateAcqUnitsOnCreate(List<String> acqUnitIds) {
    if (acqUnitIds.isEmpty()) {
      return completedFuture(null);
    }

    return VertxCompletableFuture.runAsync(ctx, () -> verifyUserHasAssignPermission(acqUnitIds))
      .thenCompose(ok -> protectionHelper.verifyIfUnitsAreActive(acqUnitIds))
      .thenCompose(ok -> protectionHelper.isOperationRestricted(acqUnitIds, ProtectedOperationType.CREATE));
  }

  private void verifyUserHasAssignPermission(List<String> acqUnitIds) {
    if (CollectionUtils.isNotEmpty(acqUnitIds) && isUserDoesNotHaveDesiredPermission(ASSIGN)){
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_ACQ_PERMISSIONS);
    }
  }

   private boolean isUserDoesNotHaveDesiredPermission(AcqDesiredPermissions acqPerm) {
    return !getProvidedPermissions().contains(acqPerm.getPermission());
  }

   private List<String> getProvidedPermissions() {
    return new JsonArray(okapiHeaders.getOrDefault(OKAPI_HEADER_PERMISSIONS, EMPTY_ARRAY)).stream().
      map(Object::toString)
      .collect(Collectors.toList());
  }

  /**
   * Gets invoice by id and calculates totals
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public CompletableFuture<Invoice> getInvoice(String id) {
    CompletableFuture<Invoice> future = new VertxCompletableFuture<>(ctx);
    getInvoiceRecord(id)
      .thenCompose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.READ)
        .thenApply(aVoid -> invoice))
      .thenCompose(invoice -> {
        // If invoice was approved already, the totals are fixed at this point and should not be recalculated
        if (isPostApproval(invoice)) {
          return completedFuture(invoice);
        }

        return recalculateTotals(invoice).thenApply(isOutOfSync -> {
          if (Boolean.TRUE.equals(isOutOfSync)) {
            updateOutOfSyncInvoice(invoice);
          }
          return invoice;
        });
      })
      .thenAccept(future::complete)
      .exceptionally(t -> {
        logger.error("Failed to get an Invoice by id={}", t.getCause(), id);
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  /**
   * Gets invoice by id without calculated totals
   *
   * @param id invoice uuid
   * @return completable future with {@link Invoice} on success or an exception if processing fails
   */
  public CompletableFuture<Invoice> getInvoiceRecord(String id) {
    return invoiceStorageRestClient.getById(id, new RequestContext(ctx, okapiHeaders), Invoice.class);
  }

  /**
   * Gets list of invoice
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link InvoiceCollection} on success or an exception if processing fails
   */
  public CompletableFuture<InvoiceCollection> getInvoices(int limit, int offset, String query) {
    CompletableFuture<InvoiceCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      buildGetInvoicesPath(limit, offset, query)
        .thenCompose(endpoint -> getInvoicesFromStorage(endpoint, httpClient, ctx, okapiHeaders, logger))
        .thenAccept(jsonInvoices -> {
          logger.info("Successfully retrieved invoices: " + jsonInvoices);
          future.complete(jsonInvoices);
        })
        .exceptionally(t -> {
          logger.error("Error getting invoices", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
        future.completeExceptionally(e);
    }
    return future;
  }

  private CompletableFuture<String> buildGetInvoicesPath(int limit, int offset, String query) {
    return protectionHelper.buildAcqUnitsCqlExprToSearchRecords(INVOICES)
      .thenApply(acqUnitsCqlExpr -> {
        String queryParam;
        if (isEmpty(query)) {
          queryParam = getEndpointWithQuery(acqUnitsCqlExpr, logger);
        } else {
          queryParam = getEndpointWithQuery(combineCqlExpressions("and", acqUnitsCqlExpr, query), logger);
        }
        return String.format(GET_INVOICES_BY_QUERY, limit, offset, queryParam, lang);
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
  public CompletableFuture<Void> deleteInvoice(String id) {
    return getInvoiceRecord(id)
    .thenCompose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.DELETE)
      .thenApply(vVoid -> invoice))
    .thenCompose(InvoiceRestrictionsUtil::checkIfInvoiceDeletionPermitted)
    .thenCompose(invoice -> invoiceStorageRestClient.delete(id, new RequestContext(ctx, okapiHeaders)));
  }

  /**
   * Handles update of the invoice. First retrieve the invoice from storage, validate, handle invoice status transition and update
   * to storage.
   *
   * @param invoice updated {@link Invoice} invoice
   * @return completable future holding response indicating success (204 No Content) or error if failed
   */
  public CompletableFuture<Void> updateInvoice(Invoice invoice) {
    logger.debug("Updating invoice...");
    return CompletableFuture.completedFuture(null).thenRun(() -> validator.validateIncomingInvoice(invoice))
      .thenCompose(aVoid -> getInvoiceRecord(invoice.getId()))
      .thenCompose(invoiceFromStorage -> validateAndHandleInvoiceStatusTransition(invoice, invoiceFromStorage))
      .thenCompose(aVoid -> updateInvoiceRecord(invoice));
  }

  private CompletableFuture<Void> handleExchangeRateChange(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return  pendingPaymentWorkflowService.handlePendingPaymentsUpdate(invoice, invoiceLines, new RequestContext(ctx, okapiHeaders))
        .thenCompose(aVoid -> updateVoucher(invoice, invoiceLines));
  }

  private CompletableFuture<Void> updateVoucher(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return voucherRetrieveService.getVoucherByInvoiceId(invoice.getId(), new RequestContext(ctx, okapiHeaders))
      .thenCompose(voucher -> {
        if (voucher != null) {
          return updateVoucherWithExchangeRate(voucher, invoice)
            .thenCompose(voucherP ->  handleVoucherWithLines(getAllFundDistributions(invoiceLines, invoice), voucherP));
        }
        return CompletableFuture.completedFuture(null);
      });
  }

  private CompletableFuture<Void> validateAndHandleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    return validateAcqUnitsOnUpdate(invoice, invoiceFromStorage)
    .thenCompose(ok -> {
      validator.validateInvoiceProtectedFields(invoice, invoiceFromStorage);
      verifyUserHasManagePermission(invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds());
      setSystemGeneratedData(invoiceFromStorage, invoice);
      return getInvoiceLinesWithTotals(invoice)
        .thenCompose(invoiceLines -> {
          List<InvoiceLine> updatedInvoiceLines = invoiceLines.stream()
            .map(invoiceLine -> JsonObject.mapFrom(invoiceLine).mapTo(InvoiceLine.class))
            .collect(toList());
          recalculateDynamicData(invoice, invoiceFromStorage, updatedInvoiceLines);
          return handleInvoiceStatusTransition(invoice, invoiceFromStorage, updatedInvoiceLines)
            .thenAccept(aVoid -> updateInvoiceLinesStatus(invoice, updatedInvoiceLines))
            .thenApply(aVoid -> filterUpdatedLines(invoiceLines, updatedInvoiceLines))
            .thenCompose(lines -> invoiceLineHelper.persistInvoiceLines(lines));
        });
    });
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
  private CompletableFuture<Void> validateAcqUnitsOnUpdate(Invoice updatedInvoice, Invoice persistedInvoice) {
    List<String> updatedAcqUnitIds = updatedInvoice.getAcqUnitIds();
    List<String> currentAcqUnitIds = persistedInvoice.getAcqUnitIds();

    return VertxCompletableFuture.runAsync(ctx, () -> verifyUserHasManagePermission(updatedAcqUnitIds, currentAcqUnitIds))
      // Check that all newly assigned units are active/exist
      .thenCompose(ok -> protectionHelper.verifyIfUnitsAreActive(ListUtils.subtract(updatedAcqUnitIds, currentAcqUnitIds)))
      // The check should be done against currently assigned (persisted in storage) units
      .thenCompose(protectedOperationTypes -> protectionHelper.isOperationRestricted(currentAcqUnitIds, UPDATE));
  }

  /**
   * The method checks if list of acquisition units to which the invoice is assigned is changed, if yes, then check that if the user
   * has desired permission to manage acquisition units assignments
   *
   * @throws HttpException if user does not have manage permission
   * @param newAcqUnitIds     list of acquisition units coming from request
   * @param currentAcqUnitIds list of acquisition units from storage
   */
  private void verifyUserHasManagePermission(List<String> newAcqUnitIds, List<String> currentAcqUnitIds) {
    Set<String> newAcqUnits = new HashSet<>(CollectionUtils.emptyIfNull(newAcqUnitIds));
    Set<String> acqUnitsFromStorage = new HashSet<>(CollectionUtils.emptyIfNull(currentAcqUnitIds));

    if (isManagePermissionRequired(newAcqUnits, acqUnitsFromStorage) && isUserDoesNotHaveDesiredPermission(MANAGE)) {
      throw new HttpException(HttpStatus.HTTP_FORBIDDEN.toInt(), USER_HAS_NO_ACQ_PERMISSIONS);
    }
  }

  private boolean isManagePermissionRequired(Set<String> newAcqUnits, Set<String> acqUnitsFromStorage) {
    return !CollectionUtils.isEqualCollection(newAcqUnits, acqUnitsFromStorage);
  }

  /**
   * Updates invoice in the storage without blocking main flow (i.e. async call)
   * @param invoice invoice which needs updates in storage
   */
  private void updateOutOfSyncInvoice(Invoice invoice) {
    logger.info("Invoice totals are out of sync in the storage");
    VertxCompletableFuture.runAsync(ctx, () -> {
      // Create new instance of the helper to initiate new http client because current one might be closed in the middle of work
      InvoiceHelper helper = new InvoiceHelper(okapiHeaders, ctx, lang);
      helper.updateInvoiceRecord(invoice)
        .handle((ok, fail) -> {
          // the http client  needs to closed regardless of the result
          helper.closeHttpClient();
          return null;
        });
    });
  }

  /**
   * Updates total values of the invoice and invoice lines
   * @param invoice invoice to update totals for
   * @param List<InvoiceLine> invoice lines to update totals for
   * @return {code true} if adjustments total, sub total or grand total value is different to original one
   */
  public boolean recalculateTotals(Invoice invoice, List<InvoiceLine> lines) {
    lines.forEach(line -> HelperUtils.calculateInvoiceLineTotals(line, invoice));
    return recalculateInvoiceTotals(invoice, lines);
  }

  /**
   * Gets invoice lines from the storage and updates total values of the invoice
   * @param invoice invoice to update totals for
   * @return {code true} if adjustments total, sub total or grand total value is different to original one
   */
  public CompletableFuture<Boolean> recalculateTotals(Invoice invoice) {
    return getInvoiceLinesWithTotals(invoice)
      .thenApply(invoiceLines -> recalculateTotals(invoice, invoiceLines));
  }

  private CompletableFuture<Void> updateWithSystemGeneratedData(Invoice invoice) {
    return generateFolioInvoiceNumber()
      .thenApply(invoice::withFolioInvoiceNo)
      .thenAccept(this::calculateTotals)
      .thenAccept(ok -> generateAdjustmentsIds(invoice));
  }

  private void generateAdjustmentsIds(Invoice invoice) {
    invoice.getAdjustments()
      .stream()
      .filter(adj -> adj.getId() == null)
      .forEach(adjustment -> adjustment.setId(randomUUID().toString()));
  }

  /**
   * Updates total values of the invoice
   * @param invoice invoice to update totals for
   * @param lines invoice lines for the invoice
   * @return {code true} if adjustments total, sub total or grand total value is different to original one
   */
  private boolean recalculateInvoiceTotals(Invoice invoice, List<InvoiceLine> lines) {
    // 1. Get original values
    Double total = invoice.getTotal();
    Double subTotal = invoice.getSubTotal();
    Double adjustmentsTotal = invoice.getAdjustmentsTotal();

    // 2. Recalculate totals
    calculateTotals(invoice, lines);

    // 3. Compare if anything has changed
    return !(Objects.equals(total, invoice.getTotal()) && Objects.equals(subTotal, invoice.getSubTotal())
        && Objects.equals(adjustmentsTotal, invoice.getAdjustmentsTotal()));
  }

  private boolean recalculateDynamicData(Invoice updatedInvoice, Invoice invoiceFromStorage, List<InvoiceLine> invoiceLines) {
    // If invoice was approved, the totals are already fixed and should not be recalculated
    if (isPostApproval(invoiceFromStorage)) {
      return false;
    }

    processProratedAdjustments(updatedInvoice, invoiceFromStorage, invoiceLines);
    return recalculateTotals(updatedInvoice, invoiceLines);
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

  private CompletionStage<Void> handleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage, List<InvoiceLine> invoiceLines) {
    if (isTransitionToApproved(invoiceFromStorage, invoice)) {
      return approveInvoice(invoice, invoiceLines);
    } else if (isAfterApprove(invoice, invoiceFromStorage) && isExchangeRateChanged(invoice, invoiceFromStorage)) {
      return handleExchangeRateChange(invoice, invoiceLines);
    } else if (isTransitionToPaid(invoiceFromStorage, invoice)) {
      if (isExchangeRateChanged(invoice, invoiceFromStorage)) {
        return handleExchangeRateChange(invoice, invoiceLines)
          .thenCompose(aVoid1 -> payInvoice(invoice, invoiceLines));
      }
      invoice.setExchangeRate(invoiceFromStorage.getExchangeRate());
      return payInvoice(invoice, invoiceLines);

    }
    return CompletableFuture.completedFuture(null);
  }

  private boolean isAfterApprove(Invoice invoice, Invoice invoiceFromStorage) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.APPROVED;
  }

  private boolean isExchangeRateChanged(Invoice invoice, Invoice invoiceFromStorage) {
    return Objects.nonNull(invoice.getExchangeRate()) && !invoice.getExchangeRate().equals(invoiceFromStorage.getExchangeRate());
  }

  private void verifyTransitionOnPaidStatus(Invoice invoiceFromStorage, Invoice invoice) {
    // Once an invoice is Paid, it should no longer transition to other statuses.
    if (invoiceFromStorage.getStatus() == Invoice.Status.PAID && invoice.getStatus() != invoiceFromStorage.getStatus()) {
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

  /**
   * Handles transition of given invoice to {@link Invoice.Status#APPROVED} status.
   * Transition triggers if the current {@link Invoice.Status} is {@link Invoice.Status#REVIEWED} or {@link Invoice.Status#OPEN}
   * and exist at least one {@link InvoiceLine} associated with this invoice
   *
   * @param invoice {@link Invoice}to be approved
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> approveInvoice(Invoice invoice, List<InvoiceLine> lines) {
    invoice.setApprovalDate(new Date());
    invoice.setApprovedBy(invoice.getMetadata().getUpdatedByUserId());

    return loadTenantConfiguration(SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_PREFIX_CONFIG_QUERY)
      .thenCompose(ok -> updateInvoiceLinesWithEncumbrances(lines, new RequestContext(ctx, okapiHeaders)))
      .thenCompose(v -> invoiceLineHelper.persistInvoiceLines(lines))
      .thenAccept(aVoid -> validator.validateBeforeApproval(invoice, lines))
      .thenCompose(aVoid -> budgetValidationService.checkEnoughMoneyInBudget(lines, invoice, new RequestContext(ctx, okapiHeaders)))
      .thenCompose(aVoid -> budgetExpenseClassService.checkExpenseClasses(lines, invoice, new RequestContext(ctx, okapiHeaders)))
      .thenCompose(v -> pendingPaymentWorkflowService.handlePendingPaymentsCreation(lines, invoice, new RequestContext(ctx ,okapiHeaders)))
      .thenCompose(v -> prepareVoucher(invoice))
      .thenCompose(voucher -> updateVoucherWithSystemCurrency(voucher, lines))
      .thenCompose(voucher -> updateVoucherWithExchangeRate(voucher, invoice))
      .thenCompose(voucher -> handleVoucherWithLines(getAllFundDistributions(lines, invoice), voucher));

  }

  private CompletableFuture<Voucher> updateVoucherWithSystemCurrency(Voucher voucher, List<InvoiceLine> lines) {
    if (!CollectionUtils.isEmpty(lines) && !CollectionUtils.isEmpty(lines.get(0).getFundDistributions())) {
      String fundId = lines.get(0).getFundDistributions().get(0).getFundId();
      return currentFiscalYearService.getCurrentFiscalYearByFund(fundId, new RequestContext(ctx, okapiHeaders))
        .thenApply(fiscalYear -> voucher.withSystemCurrency(fiscalYear.getCurrency()));
    }
    return CompletableFuture.completedFuture(voucher.withSystemCurrency(getSystemCurrency()));
  }

  private List<FundDistribution> getAllFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice) {
    String systemCurrency = getSystemCurrency();
    ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, systemCurrency);
    ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, new RequestContext(ctx, okapiHeaders));
    CurrencyConversion conversion =  exchangeRateProvider.getCurrencyConversion(conversionQuery);
    List<FundDistribution> fundDistributions = getInvoiceLineFundDistributions(invoiceLines, invoice, conversion);
    fundDistributions.addAll(getAdjustmentFundDistributions(invoice, conversion));
    return fundDistributions;
  }

  private List<FundDistribution> getInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice, CurrencyConversion conversion) {
    return invoiceLines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions()
        .stream()
        .map(fundDistribution -> JsonObject.mapFrom(fundDistribution).mapTo(FundDistribution.class).withInvoiceLineId(invoiceLine.getId())
          .withValue(getFundDistributionAmountWithConversion(fundDistribution, Money.of(invoiceLine.getTotal(), invoice.getCurrency()), conversion))
          .withDistributionType(FundDistribution.DistributionType.AMOUNT)
        )
      )
      .collect(toList());
  }

  private double getFundDistributionAmountWithConversion(FundDistribution fundDistribution, MonetaryAmount totalAmount, CurrencyConversion conversion) {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, totalAmount).with(conversion);
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
  private CompletableFuture<Voucher> prepareVoucher(Invoice invoice) {
    return voucherRetrieveService.getVoucherByInvoiceId(invoice.getId(), new RequestContext(ctx, okapiHeaders))
      .thenCompose(voucher -> {
        if (nonNull(voucher)) {
          return completedFuture(voucher);
        }
        return voucherCommandService.buildNewVoucher(invoice, new RequestContext(ctx, okapiHeaders));
      })
      .thenApply(voucher -> {
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
  private CompletableFuture<Void> handleVoucherWithLines(List<FundDistribution> fundDistributions, Voucher voucher) {
    return groupFundDistrosByExternalAcctNo(fundDistributions)
      .thenApply(fundDistrosGroupedByExternalAcctNo -> buildVoucherLineRecords(fundDistrosGroupedByExternalAcctNo, voucher))
      .thenCompose(voucherLines -> {
        Double calculatedAmount = HelperUtils.calculateVoucherAmount(voucher, voucherLines);
        voucher.setAmount(calculatedAmount);
        return handleVoucher(voucher)
          .thenAccept(voucherWithId -> populateVoucherId(voucherLines, voucher))
          .thenCompose(v -> createVoucherLinesRecords(voucherLines));
      });
  }

  private CompletableFuture<Voucher> updateVoucherWithExchangeRate(Voucher voucher, Invoice invoice) {
    return supplyBlockingAsync(ctx, () -> {
      ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, voucher.getSystemCurrency());
      ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, new RequestContext(ctx, okapiHeaders));
      ExchangeRate exchangeRate = exchangeRateProvider.getExchangeRate(conversionQuery);
      invoice.setExchangeRate(exchangeRate.getFactor().doubleValue());
      return voucher.withExchangeRate(exchangeRate.getFactor().doubleValue());
    });
  }

  /**
   * Prepares the data necessary for the generation of voucher lines based on the invoice lines found
   *
   * @param fundDistributions {@link List<InvoiceLine>} associated with processed {@link Invoice}
   * @return {@link InvoiceLine#fundDistributions} grouped by {@link Fund#externalAccountNo}
   */
  private CompletableFuture<Map<FundExtNoExpenseClassExtNoPair,  List<FundDistribution>>> groupFundDistrosByExternalAcctNo(List<FundDistribution> fundDistributions) {

    Map<String, List<FundDistribution>> fundDistrosGroupedByFundId = groupFundDistrosByFundId(fundDistributions);

    return fundService.getFunds(fundDistrosGroupedByFundId.keySet(), new RequestContext(ctx, okapiHeaders))
      .thenApply(this::groupFundsByExternalAcctNo)
      .thenCombine(groupFundDistrByFundIdByExpenseClassExtNo(fundDistributions), (fundsGroupedByExternalAcctNo, groupedFundDistros) ->
        mapExternalAcctNoToFundDistros(groupedFundDistros, fundsGroupedByExternalAcctNo)
      );
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
           fundDistributions.forEach(fundDistribution -> fundDistribution.setCode(fund.getCode()));
           groupedFundDistribution.put(key, fundDistributions);
         }
      }
    }
    return groupedFundDistribution;
  }

  private Map<String, List<FundDistribution>> groupFundDistrosByFundId(List<FundDistribution> fundDistributions) {
    return fundDistributions.stream()
      .collect(groupingBy(FundDistribution::getFundId));
  }

  private CompletableFuture<Map<String, Map<String, List<FundDistribution>>>> groupFundDistrByFundIdByExpenseClassExtNo(List<FundDistribution> fundDistrs) {
    List<String> expenseClassIds = fundDistrs.stream()
                                             .filter(fundDistribution -> nonNull(fundDistribution.getExpenseClassId()))
                                             .map(FundDistribution::getExpenseClassId).collect(toList());
    return expenseClassRetrieveService.getExpenseClasses(expenseClassIds, new RequestContext(ctx, okapiHeaders))
                               .thenApply(expenseClasses -> expenseClasses.stream().collect(toMap(ExpenseClass::getId, Function.identity())))
                               .thenApply(expenseClassByIds ->
                                 fundDistrs.stream().collect(
                                   groupingBy(FundDistribution::getFundId,
                                     groupingBy(getExpenseClassExtNo(expenseClassByIds)))
                                 )
                               );
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
  private CompletableFuture<Voucher> handleVoucher(Voucher voucher) {
    if (nonNull(voucher.getId())) {
      return voucherCommandService.updateVoucher(voucher.getId(), voucher, new RequestContext(ctx, okapiHeaders))
        .thenCompose(aVoid -> deleteVoucherLinesIfExist(voucher.getId()))
        .thenApply(aVoid -> voucher);
    } else {
      return voucherCommandService.createVoucher(voucher, new RequestContext(ctx, okapiHeaders));
    }
  }

  /**
   * Removes the voucher lines associated with the voucher, if present.
   *
   * @param voucherId Id of {@link Voucher} used to find the voucher lines.
   * @return CompletableFuture that indicates when deletion is completed
   */
  private CompletableFuture<Void> deleteVoucherLinesIfExist(String voucherId) {
    return getVoucherLineIdsByVoucherId(voucherId)
      .thenCompose(ids -> allOf(ids.stream()
        .map(voucherLineHelper::deleteVoucherLine)
        .toArray(CompletableFuture[]::new)));
  }

  private CompletableFuture<List<String>> getVoucherLineIdsByVoucherId(String voucherId) {
    String query = "voucherId==" + voucherId;
    return voucherLineHelper.getVoucherLines(Integer.MAX_VALUE, 0, query)
      .thenApply(voucherLineCollection ->  voucherLineCollection.getVoucherLines().
        stream()
        .map(org.folio.rest.acq.model.VoucherLine::getId)
        .collect(toList())
      );
  }

  private void populateVoucherId(List<VoucherLine> voucherLines, Voucher voucher) {
    voucherLines.forEach(voucherLine -> voucherLine.setVoucherId(voucher.getId()));
  }

  private CompletableFuture<Void> createVoucherLinesRecords(List<VoucherLine> voucherLines) {
    return allOf(voucherLines.stream()
      .map(voucherLineHelper::createVoucherLine)
      .toArray(CompletableFuture[]::new));
  }

  public CompletableFuture<Void> updateInvoiceRecord(Invoice updatedInvoice) {
    return invoiceStorageRestClient.put(updatedInvoice.getId(), updatedInvoice, new RequestContext(ctx, okapiHeaders));
  }

  private boolean isTransitionToPaid(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.PAID;
  }

  /**
   * Handles transition of given invoice to PAID status.
   *
   * @param invoice Invoice to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payInvoice(Invoice invoice, List<InvoiceLine> invoiceLines) {
    return paymentCreditWorkflowService.handlePaymentsAndCreditsCreation(invoice, invoiceLines, new RequestContext(ctx, okapiHeaders))
      .thenCompose(vVoid ->
         VertxCompletableFuture.allOf(ctx, payPoLines(invoiceLines),
                               voucherCommandService.payInvoiceVoucher(invoice.getId(), new RequestContext(ctx, okapiHeaders)))
      );
  }

  /**
   * Updates payment status of the associated PO Lines.
   *
   * @param invoiceLines the invoice lines to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payPoLines(List<InvoiceLine> invoiceLines) {
    Map<String, List<InvoiceLine>> poLineIdInvoiceLinesMap = groupInvoiceLinesByPoLineId(invoiceLines);
    return fetchPoLines(poLineIdInvoiceLinesMap)
      .thenApply(this::updatePoLinesPaymentStatus)
      .thenCompose(this::updateCompositePoLines);
  }

  private CompletableFuture<List<InvoiceLine>> getInvoiceLinesWithTotals(Invoice invoice) {
    return invoiceLineHelper.getInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(lines -> {
        lines.forEach(line -> HelperUtils.calculateInvoiceLineTotals(line, invoice));
        return lines;
      });
  }

  private Map<String, List<InvoiceLine>>  groupInvoiceLinesByPoLineId(List<InvoiceLine> invoiceLines) {
    if (CollectionUtils.isEmpty(invoiceLines)) {
      return Collections.emptyMap();
    }
    return invoiceLines
      .stream()
      .filter(invoiceLine -> StringUtils.isNotEmpty(invoiceLine.getPoLineId()))
      .collect(Collectors.groupingBy(InvoiceLine::getPoLineId));
  }

  /**
   * Retrieves PO Lines associated with invoice lines and calculates expected PO Line's payment status
   * @param poLineIdsWithInvoiceLines map where key is PO Line id and value is list of associated invoice lines
   * @return map where key is {@link CompositePoLine} and value is expected PO Line's payment status
   */
  private CompletableFuture<Map<CompositePoLine, CompositePoLine.PaymentStatus>> fetchPoLines(Map<String, List<InvoiceLine>> poLineIdsWithInvoiceLines) {
    List<CompletableFuture<CompositePoLine>> futures = poLineIdsWithInvoiceLines.keySet()
      .stream()
      .map(this::getPoLineById)
      .collect(toList());

    return allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toMap(poLine -> poLine, poLine -> getPoLinePaymentStatus(poLineIdsWithInvoiceLines.get(poLine.getId())))));
  }

  private CompletableFuture<CompositePoLine> getPoLineById(String poLineId) {
    return handleGetRequest(resourceByIdPath(ORDER_LINES, poLineId, lang), httpClient, ctx, okapiHeaders, logger)
      .thenApply(jsonObject -> jsonObject.mapTo(CompositePoLine.class))
      .exceptionally(throwable -> {
        List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("poLineId").withValue(poLineId));
        Error error = PO_LINE_NOT_FOUND.toError().withParameters(parameters);
        throw new HttpException(500, error);
      });
  }

  private List<CompositePoLine> updatePoLinesPaymentStatus(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithNewStatuses) {
    return compositePoLinesWithNewStatuses
      .keySet().stream()
      .filter(compositePoLine -> isPaymentStatusUpdateRequired(compositePoLinesWithNewStatuses, compositePoLine))
      .map(compositePoLine -> updatePaymentStatus(compositePoLinesWithNewStatuses, compositePoLine) )
      .collect(toList());
  }

  private boolean isPaymentStatusUpdateRequired(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus, CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus =  compositePoLinesWithStatus.get(compositePoLine);
    return !newPaymentStatus.equals(compositePoLine.getPaymentStatus());
  }

  private CompositePoLine updatePaymentStatus(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus, CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus =  compositePoLinesWithStatus.get(compositePoLine);
    compositePoLine.setPaymentStatus(newPaymentStatus);
    return compositePoLine;
  }


  private CompositePoLine.PaymentStatus getPoLinePaymentStatus(List<InvoiceLine> invoiceLines) {
    if (isAnyInvoiceLineReleaseEncumbrance(invoiceLines)) {
      return CompositePoLine.PaymentStatus.FULLY_PAID;
    } else {
      return CompositePoLine.PaymentStatus.PARTIALLY_PAID;
    }
  }

  private boolean isAnyInvoiceLineReleaseEncumbrance(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream().anyMatch(InvoiceLine::getReleaseEncumbrance);
  }

  private CompletionStage<Void> updateCompositePoLines(List<CompositePoLine> poLines) {
    return allOf(poLines.stream()
      .map(JsonObject::mapFrom)
      .map(poLine -> handlePutRequest(resourceByIdPath(ORDER_LINES, HelperUtils.getId(poLine), lang), poLine, httpClient, ctx, okapiHeaders, logger))
      .toArray(CompletableFuture[]::new))
      .exceptionally(fail -> {
        throw new HttpException(500, PO_LINE_UPDATE_FAILURE.toError());
      });
  }

  private CompletableFuture<String> generateFolioInvoiceNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(FOLIO_INVOICE_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

  private void calculateTotals(Invoice invoice) {
    calculateTotals(invoice, Collections.emptyList());
  }

  private void calculateTotals(Invoice invoice, List<InvoiceLine> lines) {
    CurrencyUnit currency = Monetary.getCurrency(invoice.getCurrency());

    // 1. Sub-total
    MonetaryAmount subTotal = HelperUtils.summarizeSubTotals(lines, currency, false);

    // 2. Calculate Adjustments Total
    // If there are no invoice lines then adjustmentsTotal = sum of all invoice adjustments
    // If lines are present then adjustmentsTotal = notProratedInvoiceAdjustments + sum of invoiceLines adjustmentsTotal
    MonetaryAmount adjustmentsTotal;
    if (lines.isEmpty()) {
      List<Adjustment> proratedAdjustments = new ArrayList<>(adjustmentsService.getProratedAdjustments(invoice));
      proratedAdjustments.addAll(adjustmentsService.getNotProratedAdjustments(invoice));
      adjustmentsTotal = calculateAdjustmentsTotal(proratedAdjustments, subTotal);
    } else {
      adjustmentsTotal = calculateAdjustmentsTotal(adjustmentsService.getNotProratedAdjustments(invoice), subTotal)
        .add(calculateInvoiceLinesAdjustmentsTotal(lines, currency));
    }

    // 3. Total
    if (Boolean.FALSE.equals(invoice.getLockTotal())) {
      invoice.setTotal(convertToDoubleWithRounding(subTotal.add(adjustmentsTotal)));
    }
    invoice.setAdjustmentsTotal(convertToDoubleWithRounding(adjustmentsTotal));
    invoice.setSubTotal(convertToDoubleWithRounding(subTotal));
  }

  private MonetaryAmount calculateInvoiceLinesAdjustmentsTotal(List<InvoiceLine> lines, CurrencyUnit currency) {
    return lines.stream()
      .map(line -> Money.of(line.getAdjustmentsTotal(), currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }

  private CompletableFuture<Void> updateInvoiceLinesWithEncumbrances(List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    List<String> poLineIds = getPoLineIds(invoiceLines);
    if (!poLineIds.isEmpty()) {
      return encumbranceService.getEncumbrancesByPoLineIds(poLineIds, requestContext)
        .thenApply(encumbrances -> encumbrances.stream()
          .collect(groupingBy(encumbr -> Pair.of(encumbr.getEncumbrance().getSourcePoLineId(), encumbr.getFromFundId()))))
        .thenAccept(encumbrByPoLineAndFundIdMap -> updateFundDistributionsWithEncumbrances(invoiceLines, encumbrByPoLineAndFundIdMap));
    }
    return CompletableFuture.completedFuture(null);
  }

  private List<String> getPoLineIds(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream()
                      .filter(invoiceLine -> isEncumbrancePresent(invoiceLine.getFundDistributions()))
                      .map(InvoiceLine::getPoLineId)
                      .collect(toList());
  }

  private boolean isEncumbrancePresent(List<FundDistribution> fundDistributions) {
    return fundDistributions.stream().anyMatch(fund -> Objects.isNull(fund.getEncumbrance()));
  }

  private void updateFundDistributionsWithEncumbrances(List<InvoiceLine> invoiceLines
                              , Map<Pair<String, String>, List<Transaction>> encumbrByPoLineAndFundIdMap) {
    List<Map<Pair<String, String>, List<FundDistribution>>> fundDistrByPoLineAndFundIdMap = invoiceLines.stream()
      .map(line -> line.getFundDistributions().stream().collect(groupingBy(fund -> Pair.of(line.getPoLineId(), fund.getFundId()))))
      .collect(toList());

    fundDistrByPoLineAndFundIdMap.forEach(fundDistrByPoLineAndFundId ->
      fundDistrByPoLineAndFundId.forEach((key, value) -> {
        List<Transaction> encumbrances = encumbrByPoLineAndFundIdMap.get(key);
        if (!CollectionUtils.isEmpty(encumbrances)) {
          Transaction encumbrance = encumbrances.get(0);
          FundDistribution fundDistribution = value.get(0);
          fundDistribution.withEncumbrance(encumbrance.getId());
        }
      })
   );
  }

}
