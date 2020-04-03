package org.folio.rest.impl;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.completedFuture;
import static me.escoffier.vertx.completablefuture.VertxCompletableFuture.supplyAsync;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isAlpha;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.AcqDesiredPermissions.ASSIGN;
import static org.folio.invoices.utils.AcqDesiredPermissions.MANAGE;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.ADJUSTMENT_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH;
import static org.folio.invoices.utils.ErrorCodes.AWAITING_PAYMENT_ERROR;
import static org.folio.invoices.utils.ErrorCodes.EXTERNAL_ACCOUNT_NUMBER_IS_MISSING;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.FUND_DISTRIBUTIONS_NOT_PRESENT;
import static org.folio.invoices.utils.ErrorCodes.INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION;
import static org.folio.invoices.utils.ErrorCodes.INVALID_INVOICE_TRANSITION_ON_PAID_STATUS;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_TOTAL_REQUIRED;
import static org.folio.invoices.utils.ErrorCodes.LINE_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ErrorCodes.USER_HAS_NO_ACQ_PERMISSIONS;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NUMBER_PREFIX_NOT_ALPHA;
import static org.folio.invoices.utils.HelperUtils.calculateAdjustmentsTotal;
import static org.folio.invoices.utils.HelperUtils.calculateInvoiceLineTotals;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherLineAmount;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.findChangedProtectedFields;
import static org.folio.invoices.utils.HelperUtils.getAdjustmentFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getFundDistributionAmount;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;
import static org.folio.invoices.utils.HelperUtils.getInvoicesFromStorage;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.ProtectedOperationType.UPDATE;
import static org.folio.invoices.utils.ResourcePathResolver.AWAITING_PAYMENTS;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.FUNDS;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_PERMISSIONS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.HttpStatus;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.AcqDesiredPermissions;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.rest.acq.model.finance.AwaitingPayment;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.finance.InvoiceTransactionSummary;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;

public class InvoiceHelper extends AbstractHelper {

  static final int MAX_IDS_FOR_GET_RQ = 15;
  static final String NO_INVOICE_LINES_ERROR_MSG = "An invoice cannot be approved if there are no corresponding lines of invoice.";
  static final String TOTAL = "total";
  static final String VOUCHER_NUMBER_CONFIG_NAME = "voucherNumber";
  static final String VOUCHER_NUMBER_PREFIX_CONFIG = "voucherNumberPrefix";
  private static final String VOUCHER_NUMBER_CONFIG_QUERY = String.format(CONFIG_QUERY, INVOICE_CONFIG_MODULE_NAME, VOUCHER_NUMBER_CONFIG_NAME);
  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + SEARCH_PARAMS;
  private static final String GET_FUNDS_BY_QUERY = resourcesPath(FUNDS) + "?query=%s&limit=%s&lang=%s";
  private static final String EMPTY_ARRAY = "[]";

  // Using variable to "cache" lines for particular invoice base on assumption that the helper is stateful and new instance is used
  private List<InvoiceLine> storedInvoiceLines;

  private final InvoiceLineHelper invoiceLineHelper;
  private final VoucherHelper voucherHelper;
  private final VoucherLineHelper voucherLineHelper;
  private final ProtectionHelper protectionHelper;
  private final FinanceHelper financeHelper;

  public InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    invoiceLineHelper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
    voucherHelper = new VoucherHelper(httpClient, okapiHeaders, ctx, lang);
    voucherLineHelper = new VoucherLineHelper(httpClient, okapiHeaders, ctx, lang);
    protectionHelper = new ProtectionHelper(httpClient, okapiHeaders, ctx, lang);
    financeHelper = new FinanceHelper(httpClient, okapiHeaders, ctx, lang);

  }

  public CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return validateAcqUnitsOnCreate(invoice.getAcqUnitIds())
      .thenCompose(v -> updateWithSystemGeneratedData(invoice))
      .thenApply(ok -> JsonObject.mapFrom(invoice))
      .thenCompose(jsonInvoice -> createRecordInStorage(jsonInvoice, resourcesPath(INVOICES)))
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
    return getInvoiceById(id, lang, httpClient, ctx, okapiHeaders, logger);
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
   * 3. If user has permission to delete then delete invoiceLine
   * @param id invoiceLine id to be deleted
   */
  public CompletableFuture<Void> deleteInvoice(String id) {
    return getInvoiceRecord(id)
    .thenCompose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.DELETE))
    .thenCompose(v -> handleDeleteRequest(resourceByIdPath(INVOICES, id, lang), httpClient, ctx, okapiHeaders, logger));
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

    return getInvoiceRecord(invoice.getId())
      .thenCompose(invoiceFromStorage -> validateAndHandleInvoiceStatusTransition(invoice, invoiceFromStorage))
      .thenCompose(ok -> updateInvoiceRecord(invoice));
  }

  private CompletableFuture<Void> validateAndHandleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    return validateAcqUnitsOnUpdate(invoice, invoiceFromStorage)
    .thenCompose(ok -> {
      validateInvoice(invoice, invoiceFromStorage);
      verifyUserHasManagePermission(invoice.getAcqUnitIds(), invoiceFromStorage.getAcqUnitIds());
      setSystemGeneratedData(invoiceFromStorage, invoice);

      return recalculateDynamicData(invoice, invoiceFromStorage)
        .thenCompose(okRecalculated -> handleInvoiceStatusTransition(invoice, invoiceFromStorage));
    });
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
   * Gets invoice lines from the storage and updates total values of the invoice
   * @param invoice invoice to update totals for
   * @return {code true} if adjustments total, sub total or grand total value is different to original one
   */
  public CompletableFuture<Boolean> recalculateTotals(Invoice invoice) {
    return getInvoiceLinesWithTotals(invoice).thenApply(lines -> recalculateTotals(invoice, lines));
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
  private boolean recalculateTotals(Invoice invoice, List<InvoiceLine> lines) {
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

  private CompletableFuture<Boolean> recalculateDynamicData(Invoice updatedInvoice, Invoice invoiceFromStorage) {
    // If invoice was approved, the totals are already fixed and should not be recalculated
    if (isPostApproval(invoiceFromStorage)) {
      return completedFuture(false);
    }

    return processProratedAdjustments(updatedInvoice, invoiceFromStorage).thenCompose(ok -> recalculateTotals(updatedInvoice));
  }

  private CompletableFuture<Void> processProratedAdjustments(Invoice updatedInvoice, Invoice invoiceFromStorage) {
    List<Adjustment> currentAdjustments = getProratedAdjustments(updatedInvoice);

    // Skip if prorated adjustments are the same in incoming invoice and from storage
    if (CollectionUtils.isEqualCollection(currentAdjustments, getProratedAdjustments(invoiceFromStorage))) {
      return completedFuture(null);
    }

    return fetchInvoiceLinesByInvoiceId(updatedInvoice.getId()).thenCompose(lines -> {
      if (lines.isEmpty()) {
        return completedFuture(null);
      }

      return supplyAsync(ctx, () -> invoiceLineHelper.processProratedAdjustments(lines, updatedInvoice))
        .thenCompose(invoiceLines -> VertxCompletableFuture.allOf(ctx, invoiceLines.stream()
          .map(invoiceLine -> persistInvoiceLineUpdates(updatedInvoice, invoiceLine))
          .toArray(CompletableFuture[]::new)));
    });
  }

  private CompletableFuture<Void> persistInvoiceLineUpdates(Invoice updatedInvoice, InvoiceLine invoiceLine) {
    calculateInvoiceLineTotals(invoiceLine, updatedInvoice);
    return invoiceLineHelper.updateInvoiceLineToStorage(invoiceLine)
      .thenAccept(ok -> {
        // Replace invoice line in the local "cache" on success
        storedInvoiceLines.removeIf(line -> line.getId().equals(invoiceLine.getId()));
        storedInvoiceLines.add(invoiceLine);
      });
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
    generateAdjustmentsIds(invoice);
  }

  private CompletionStage<Void> handleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    if (isTransitionToApproved(invoiceFromStorage, invoice)) {
      return approveInvoice(invoice);
    } else if (isTransitionToPaid(invoiceFromStorage, invoice)) {
      return payInvoice(invoice);
    }
    return completedFuture(null);
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
  private CompletableFuture<Void> approveInvoice(Invoice invoice) {
    invoice.setApprovalDate(new Date());
    invoice.setApprovedBy(invoice.getMetadata().getUpdatedByUserId());

    return loadTenantConfiguration(SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_CONFIG_QUERY)
      .thenCompose(ok -> getInvoiceLinesWithTotals(invoice))
      .thenApply(lines -> validateBeforeApproval(invoice, lines))
      .thenCompose(invoiceLines -> updateEncumbrances(invoiceLines, invoice.getCurrency()))
      .thenCompose(lines -> prepareVoucher(invoice)
          .thenCompose(voucher -> handleVoucherWithLines(getAllFundDistributions(lines, invoice), voucher))
      );
  }

  private List<InvoiceLine> validateBeforeApproval(Invoice invoice, List<InvoiceLine> lines) {
    verifyInvoiceLineNotEmpty(lines);
    validateInvoiceLineFundDistributions(lines, Monetary.getCurrency(invoice.getCurrency()));
    validateInvoiceAdjustmentsDistributions(getNotProratedAdjustments(invoice) , Monetary.getCurrency(invoice.getCurrency()));
    return lines;
  }

  private void verifyInvoiceLineNotEmpty(List<InvoiceLine> invoiceLines) {
    if (invoiceLines.isEmpty()) {
      throw new HttpException(500, NO_INVOICE_LINES_ERROR_MSG);
    }
  }

  private void validateInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines, CurrencyUnit currencyUnit) {
    for (InvoiceLine line : invoiceLines){
      if (CollectionUtils.isEmpty(line.getFundDistributions())) {
        throw new HttpException(400, FUND_DISTRIBUTIONS_NOT_PRESENT);
      }

      if (isFundDistributionSummaryNotValid(line, currencyUnit)) {
        throw new HttpException(400, LINE_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH);
      }
    }
  }

  private boolean isFundDistributionSummaryNotValid(InvoiceLine line, CurrencyUnit currencyUnit) {
    return ObjectUtils.notEqual(sumMixedDistributions(line, currencyUnit), line.getTotal());
  }

  private Double sumMixedDistributions(InvoiceLine line, CurrencyUnit currencyUnit) {
    return sumMixedDistributions(line.getFundDistributions(), line.getTotal(), currencyUnit);
  }

  private void validateInvoiceAdjustmentsDistributions(List<Adjustment> adjustments, CurrencyUnit currencyUnit) {
    for (Adjustment adjustment : adjustments){
      if (CollectionUtils.isEmpty(adjustment.getFundDistributions())) {
        throw new HttpException(400, ADJUSTMENT_FUND_DISTRIBUTIONS_NOT_PRESENT);
      }

      if (isFundDistributionSummaryNotValid(adjustment, currencyUnit)) {
        throw new HttpException(400, ADJUSTMENT_FUND_DISTRIBUTIONS_SUMMARY_MISMATCH);
      }
    }
  }

  private boolean isFundDistributionSummaryNotValid(Adjustment adjustment, CurrencyUnit currencyUnit) {
    return ObjectUtils.notEqual(sumMixedDistributions(adjustment.getFundDistributions(), adjustment.getValue(), currencyUnit), adjustment.getValue());
  }

  private Double sumMixedDistributions(List<FundDistribution> fundDistributions, double total, CurrencyUnit currencyUnit) {
    return fundDistributions.stream()
      .map(fundDistribution -> getFundDistributionAmount(fundDistribution, total, currencyUnit))
      .reduce(MonetaryAmount::add)
      .orElse(Money.zero(currencyUnit))
      .getNumber().doubleValue();
  }

  private List<FundDistribution> getAllFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice) {
    CurrencyConversion conversion = getExchangeRateProvider().getCurrencyConversion(getSystemCurrency());

    List<FundDistribution> fundDistributions = getInvoiceLineFundDistributions(invoiceLines, invoice, conversion);
    fundDistributions.addAll(getAdjustmentFundDistributions(invoice, conversion));

    return fundDistributions;
  }

  private List<FundDistribution> getInvoiceLineFundDistributions(List<InvoiceLine> invoiceLines, Invoice invoice, CurrencyConversion conversion) {
    return invoiceLines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions()
        .stream()
        .map(fundDistribution -> fundDistribution.withInvoiceLineId(invoiceLine.getId())
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
    return getNotProratedAdjustments(invoice).stream()
      .flatMap(adjustment -> adjustment.getFundDistributions()
        .stream()
        .map(fundDistribution -> fundDistribution
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
    return voucherHelper.getVoucherByInvoiceId(invoice.getId())
      .thenCompose(voucher -> {
        if (Objects.nonNull(voucher)) {
          return completedFuture(voucher);
        }
        return buildNewVoucher(invoice);
      })
      .thenApply(voucher -> {
        invoice.setVoucherNumber(voucher.getVoucherNumber());
        voucher.setAcqUnitIds(invoice.getAcqUnitIds());
        return withRequiredFields(voucher, invoice);
      })
      .thenCompose(voucher -> setExchangeRateFactor(voucher.withSystemCurrency(getSystemCurrency())));
  }

  /**
   * Updates state of {@link Voucher} linked with processed {@link Invoice}
   *
   * @param voucher {@link Voucher} from voucher-storage related to processed invoice
   * @param invoice invoice {@link Invoice} to be approved
   * @return voucher
   */
  private Voucher withRequiredFields(Voucher voucher, Invoice invoice) {

    voucher.setInvoiceCurrency(invoice.getCurrency());
    voucher.setExportToAccounting(invoice.getExportToAccounting());
    voucher.setAccountingCode(invoice.getAccountingCode());

    // TODO Start using real information to create a voucher when it becomes known where to get these values from.
    voucher.setType(Voucher.Type.VOUCHER);
    voucher.setStatus(Voucher.Status.AWAITING_PAYMENT);

    return voucher;
  }

  /**
   * Build new {@link Voucher} based on processed {@link Invoice}
   *
   * @param invoice invoice {@link Invoice} to be approved
   * @return completable future with {@link Voucher}
   */
  private CompletableFuture<Voucher> buildNewVoucher(Invoice invoice) {

    Voucher voucher = new Voucher();
    voucher.setInvoiceId(invoice.getId());

    return getVoucherNumberWithPrefix()
      .thenApply(voucher::withVoucherNumber);
  }

  private CompletableFuture<String> getVoucherNumberWithPrefix() {
    final String prefix = getVoucherNumberPrefix();
    validateVoucherNumberPrefix(prefix);

    return voucherHelper.generateVoucherNumber()
      .thenApply(sequenceNumber -> prefix + sequenceNumber);
  }

  private String getVoucherNumberPrefix() {
    return getLoadedTenantConfiguration()
      .getConfigs().stream()
      .filter(this::isVoucherNumberPrefixConfig)
      .map(config -> {
        if(config.getValue() != null) {
          String prefix = new JsonObject(config.getValue()).getString(VOUCHER_NUMBER_PREFIX_CONFIG);
          return StringUtils.isNotEmpty(prefix) ? prefix : EMPTY;
        } else {
          return EMPTY;
        }
      })
      .findFirst()
      .orElse(EMPTY);
  }

  private boolean isVoucherNumberPrefixConfig(Config config) {
    return INVOICE_CONFIG_MODULE_NAME.equals(config.getModule()) && VOUCHER_NUMBER_CONFIG_NAME.equals(config.getConfigName());
  }

  private void validateVoucherNumberPrefix(String prefix) {
    if (StringUtils.isNotEmpty(prefix) && !isAlpha(prefix)) {
      throw new HttpException(500, VOUCHER_NUMBER_PREFIX_NOT_ALPHA);
    }
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

  private CompletableFuture<List<InvoiceLine>> updateEncumbrances(List<InvoiceLine> invoiceLines, String currency) {

    return VertxCompletableFuture.supplyBlockingAsync(ctx, () -> buildAwaitingPayments(invoiceLines, currency))
      .thenCompose(awaitingPayments -> {
        InvoiceTransactionSummary summary = buildInvoiceTransactionsSummary(invoiceLines, awaitingPayments.size());
        return createInvoiceTransactionSummary(summary)
          .thenCompose(s -> postAwaitingPayments(awaitingPayments));
      }).thenApply(aVoid -> invoiceLines);
  }

  private InvoiceTransactionSummary buildInvoiceTransactionsSummary(List<InvoiceLine> invoiceLines, int numEncumbrances) {
    return new InvoiceTransactionSummary()
      .withId(invoiceLines.get(0).getInvoiceId())
      .withNumEncumbrances(numEncumbrances)
      .withNumPaymentsCredits(calculatePaymentsNumber(invoiceLines));
  }

  private int calculatePaymentsNumber(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream()
      .filter(invoiceLine -> invoiceLine.getTotal() != 0)
      .mapToInt(invoiceLine -> invoiceLine.getFundDistributions().size())
      .sum();
  }

  private CompletableFuture<String> createInvoiceTransactionSummary(InvoiceTransactionSummary summary) {
    return createRecordInStorage(JsonObject.mapFrom(summary), resourcesPath(INVOICE_TRANSACTION_SUMMARIES));
  }

  private CompletableFuture<Void> postAwaitingPayments(List<AwaitingPayment> awaitingPayments) {
    return VertxCompletableFuture.allOf(ctx,awaitingPayments.stream()
      .map(awaitingPayment -> postRecorderWithoutResponseBody(JsonObject.mapFrom(awaitingPayment), resourcesPath(AWAITING_PAYMENTS))
        .exceptionally(t -> {
          logger.error("Failed to update encumbrance with id {}", t, awaitingPayment.getEncumbranceId());
          Parameter parameter = new Parameter().withKey("encumbranceId").withValue(awaitingPayment.getEncumbranceId());
          throw new HttpException(400, AWAITING_PAYMENT_ERROR.toError().withParameters(Collections.singletonList(parameter)));
        }))
      .toArray(CompletableFuture[]::new));
  }

  private List<AwaitingPayment> buildAwaitingPayments(List<InvoiceLine> invoiceLines, String currency) {
    CurrencyConversion conversion = getExchangeRateProvider().getCurrencyConversion(getSystemCurrency());
    return invoiceLines.stream()
      .flatMap(line -> line.getFundDistributions().stream()
        .filter(fundDistribution -> Objects.nonNull(fundDistribution.getEncumbrance()))
        .map(fundDistribution -> buildAwaitingPayment(fundDistribution, line, currency, conversion)))
      .collect(toList());
  }

  private AwaitingPayment buildAwaitingPayment(FundDistribution fundDistribution, InvoiceLine invoiceLine, String currency, CurrencyConversion conversion) {
    MonetaryAmount amount = getFundDistributionAmount(fundDistribution, invoiceLine.getTotal(), currency).with(conversion);
    return new AwaitingPayment()
      .withAmountAwaitingPayment(convertToDoubleWithRounding(amount))
      .withEncumbranceId(fundDistribution.getEncumbrance())
      .withReleaseEncumbrance(invoiceLine.getReleaseEncumbrance())
      .withInvoiceId(invoiceLine.getInvoiceId())
      .withInvoiceLineId(invoiceLine.getId());
  }

  private CompletableFuture<Voucher> setExchangeRateFactor(Voucher voucher) {
    return VertxCompletableFuture.supplyBlockingAsync(ctx, () -> getExchangeRateProvider()
        .getExchangeRate(voucher.getInvoiceCurrency(), voucher.getSystemCurrency()))
      .thenApply(exchangeRate -> voucher.withExchangeRate(exchangeRate.getFactor().doubleValue()));
  }

  /**
   * Prepares the data necessary for the generation of voucher lines based on the invoice lines found
   *
   * @param fundDistributions {@link List<InvoiceLine>} associated with processed {@link Invoice}
   * @return {@link InvoiceLine#fundDistributions} grouped by {@link Fund#externalAccountNo}
   */
  private CompletableFuture<Map<String,  List<FundDistribution>>> groupFundDistrosByExternalAcctNo(List<FundDistribution> fundDistributions) {

    Map<String, List<FundDistribution>> fundDistrosGroupedByFundId = groupFundDistrosByFundId(fundDistributions);

    return fetchFundsByIds(new ArrayList<>(fundDistrosGroupedByFundId.keySet()))
      .thenApply(this::groupFundsByExternalAcctNo)
      .thenApply(fundsGroupedByExternalAcctNo ->
        mapExternalAcctNoToFundDistros(fundDistrosGroupedByFundId, fundsGroupedByExternalAcctNo)
      );
  }

  private Map<String, List<Fund>> groupFundsByExternalAcctNo(List<Fund> funds) {
    return funds.stream().collect(groupingBy(Fund::getExternalAccountNo));
  }

  private Map<String,  List<FundDistribution>> mapExternalAcctNoToFundDistros(
    Map<String, List<FundDistribution>> fundDistrosGroupedByFundId,
    Map<String, List<Fund>> fundsGroupedByExternalAccountNo) {

    return fundsGroupedByExternalAccountNo.keySet()
      .stream()
      .collect(toMap(externalAccountNo -> externalAccountNo,
        externalAccountNo -> fundsGroupedByExternalAccountNo.get(externalAccountNo)
          .stream()
          .map(Fund::getId)
          .flatMap(fundId -> fundDistrosGroupedByFundId.get(fundId)
            .stream())
          .collect(toList())));
  }

  private Map<String,  List<FundDistribution>> groupFundDistrosByFundId(List<FundDistribution> fundDistributions) {
    return fundDistributions.stream()
      .collect(groupingBy(FundDistribution::getFundId));
  }

  private CompletableFuture<List<Fund>> fetchFundsByIds(List<String> fundIds) {
    List<CompletableFuture<List<Fund>>> futures = StreamEx
      .ofSubLists(fundIds, MAX_IDS_FOR_GET_RQ)
      // Send get request for each CQL query
      .map(this::getFundsByIds)
      .collect(toList());

    return collectResultsOnSuccess(futures)
      .thenApply(results -> results
        .stream()
        .flatMap(List::stream)
        .collect(toList())
      )
      .thenApply(existingFunds -> verifyThatAllFundsFound(existingFunds, fundIds));
  }

  private List<Fund> verifyThatAllFundsFound(List<Fund> existingFunds, List<String> fundIds) {
    List<String> fundIdsWithoutExternalAccNo = getFundIdsWithoutExternalAccNo(existingFunds);
    if (isNotEmpty(fundIdsWithoutExternalAccNo)) {
      throw new HttpException(500, buildFundError(fundIdsWithoutExternalAccNo, EXTERNAL_ACCOUNT_NUMBER_IS_MISSING));
    }
    if (fundIds.size() != existingFunds.size()) {
      List<String> idsNotFound = collectFundIdsThatWasNotFound(existingFunds, fundIds);
      if (isNotEmpty(idsNotFound)) {
        throw new HttpException(500, buildFundError(idsNotFound, FUNDS_NOT_FOUND));
      }
    }
    return existingFunds;
  }

  private List<String> getFundIdsWithoutExternalAccNo(List<Fund> existingFunds) {
    return existingFunds.stream()
      .filter(fund -> Objects.isNull(fund.getExternalAccountNo()))
      .map(Fund::getId)
      .collect(toList());
  }

  private List<String> collectFundIdsThatWasNotFound(List<Fund> existingFunds, List<String> fundIds) {
    return fundIds.stream()
      .filter(id -> existingFunds.stream()
        .map(Fund::getId)
        .noneMatch(existingId -> existingId.equals(id)))
      .collect(toList());
  }

  private Error buildFundError(List<String> fundIds, ErrorCodes errorCode) {
    Parameter parameter = new Parameter().withKey("fundIds").withValue(fundIds.toString());
    return errorCode.toError().withParameters(Collections.singletonList(parameter));
  }

  private CompletableFuture<List<Fund>> getFundsByIds(List<String> ids) {
    String query = encodeQuery(HelperUtils.convertIdsToCqlQuery(ids), logger);
    String endpoint = String.format(GET_FUNDS_BY_QUERY, query, ids.size(), lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(fc -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> fc.mapTo(FundCollection.class).getFunds()));
  }

  private List<VoucherLine> buildVoucherLineRecords(Map<String, List<FundDistribution>> fundDistroGroupedByExternalAcctNo, Voucher voucher) {

    return fundDistroGroupedByExternalAcctNo.entrySet().stream()
      .map(entry -> buildVoucherLineRecord(entry, voucher.getSystemCurrency()))
      .collect(Collectors.toList());
  }

  private VoucherLine buildVoucherLineRecord(Map.Entry<String, List<FundDistribution>> fundDistroAcctNoEntry, String systemCurrency) {
    String externalAccountNumber = fundDistroAcctNoEntry.getKey();
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
    if (Objects.nonNull(voucher.getId())) {
      return voucherHelper.updateVoucher(voucher)
        .thenCompose(aVoid -> deleteVoucherLinesIfExist(voucher.getId()))
        .thenApply(aVoid -> voucher);
    } else {
      return voucherHelper.createVoucher(voucher);
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

  public CompletableFuture<Boolean> validateIncomingInvoice(Invoice invoice) {
    if (Boolean.TRUE.equals(invoice.getLockTotal()) && Objects.isNull(invoice.getTotal())) {
      addProcessingError(INVOICE_TOTAL_REQUIRED.toError());
    }
    if (!isPostApproval(invoice) && (invoice.getApprovalDate() != null || invoice.getApprovedBy() != null)) {
      addProcessingError(INCOMPATIBLE_INVOICE_FIELDS_ON_STATUS_TRANSITION.toError());
    }
    return completedFuture(getErrors().isEmpty());
  }

  private void validateInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    if(isPostApproval(invoiceFromStorage)) {
      Set<String> fields = findChangedProtectedFields(invoice, invoiceFromStorage, InvoiceProtectedFields.getFieldNames());

      // "total" depends on value of "lockTotal": if value is true, total is required; if false, read-only (system calculated)
      if (Boolean.TRUE.equals(invoiceFromStorage.getLockTotal())
          && !Objects.equals(invoice.getTotal(), invoiceFromStorage.getTotal())) {
        fields.add(TOTAL);
      }
      verifyThatProtectedFieldsUnchanged(fields);
    }
  }

  public CompletableFuture<Void> updateInvoiceRecord(Invoice updatedInvoice) {
    JsonObject jsonInvoice = JsonObject.mapFrom(updatedInvoice);
    String path = resourceByIdPath(INVOICES, updatedInvoice.getId(), lang);
    return handlePutRequest(path, jsonInvoice, httpClient, ctx, okapiHeaders, logger);
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
  private CompletableFuture<Void> payInvoice(Invoice invoice) {
    return fetchInvoiceLinesByInvoiceId(invoice.getId())
      .thenCompose(invoiceLines -> financeHelper.handlePaymentsAndCredits(invoice, invoiceLines))
      .thenCompose(vVoid -> VertxCompletableFuture.allOf(ctx, payPoLines(invoice), payVoucher(invoice)));
  }

  /**
   * Updates payment status of the associated PO Lines.
   *
   * @param invoice the invoice to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payPoLines(Invoice invoice) {
    return fetchInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(this::groupInvoiceLinesByPoLineId)
      .thenCompose(this::fetchPoLines)
      .thenApply(this::updatePoLinesPaymentStatus)
      .thenCompose(this::updateCompositePoLines);
  }

  /**
   * Updates associated Voucher status to Paid.
   *
   * @param invoice invoice to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payVoucher(Invoice invoice) {

    return voucherHelper.getVoucherByInvoiceId(invoice.getId())
      .thenApply(voucher -> Optional.ofNullable(voucher).orElseThrow(() -> new HttpException(500, VOUCHER_NOT_FOUND.toError())))
      .thenCompose(voucherHelper::updateVoucherStatusToPaid);
  }

  private CompletableFuture<List<InvoiceLine>> fetchInvoiceLinesByInvoiceId(String invoiceId) {
    if (storedInvoiceLines != null) {
      return completedFuture(storedInvoiceLines);
    }

    return invoiceLineHelper.getInvoiceLinesByInvoiceId(invoiceId).thenApply(invoiceLines -> storedInvoiceLines = invoiceLines);
  }

  private CompletableFuture<List<InvoiceLine>> getInvoiceLinesWithTotals(Invoice invoice) {
    return fetchInvoiceLinesByInvoiceId(invoice.getId())
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
    MonetaryAmount subTotal = invoiceLineHelper.summarizeSubTotals(lines, currency, false);

    // 2. Calculate Adjustments Total
    // If there are no invoice lines then adjustmentsTotal = sum of all invoice adjustments
    // If lines are present then adjustmentsTotal = notProratedInvoiceAdjustments + sum of invoiceLines adjustmentsTotal
    MonetaryAmount adjustmentsTotal = null;
    if (lines.isEmpty()) {
      List<Adjustment> proratedAdjustments = new ArrayList<>(getProratedAdjustments(invoice));
      proratedAdjustments.addAll(getNotProratedAdjustments(invoice));
      adjustmentsTotal = calculateAdjustmentsTotal(proratedAdjustments, subTotal);
    } else {
      adjustmentsTotal = calculateAdjustmentsTotal(getNotProratedAdjustments(invoice), subTotal)
        .add(calculateInvoiceLinesAdjustmentsTotal(lines, currency));
    }

    // 3. Total
    if (Boolean.FALSE.equals(invoice.getLockTotal())) {
      invoice.setTotal(convertToDoubleWithRounding(subTotal.add(adjustmentsTotal)));
    }
    invoice.setAdjustmentsTotal(convertToDoubleWithRounding(adjustmentsTotal));
    invoice.setSubTotal(convertToDoubleWithRounding(subTotal));
  }

  private List<Adjustment> getNotProratedAdjustments(Invoice invoice) {
    return HelperUtils.getNotProratedAdjustments(invoice.getAdjustments());
  }

  private List<Adjustment> getProratedAdjustments(Invoice invoice) {
    return HelperUtils.getProratedAdjustments(invoice.getAdjustments());
  }

  private MonetaryAmount calculateInvoiceLinesAdjustmentsTotal(List<InvoiceLine> lines, CurrencyUnit currency) {
    return lines.stream()
      .map(line -> Money.of(line.getAdjustmentsTotal(), currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }
}
