package org.folio.rest.impl;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_TOTAL_REQUIRED;
import static org.folio.invoices.utils.HelperUtils.calculateAdjustmentsTotal;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherLineAmount;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertToDouble;
import static java.util.stream.Collectors.toMap;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.findChangedProtectedFields;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isFieldsVerificationNeeded;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.PO_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;
import static org.folio.rest.impl.VoucherHelper.DEFAULT_SYSTEM_CURRENCY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.MonetaryConversions;

import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import one.util.streamex.StreamEx;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.javamoney.moneta.Money;

import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.javamoney.moneta.function.MonetaryOperators;


import org.javamoney.moneta.function.MonetaryFunctions;

public class InvoiceHelper extends AbstractHelper {

  static final int MAX_IDS_FOR_GET_RQ = 15;
  private static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  private static final String DEFAULT_ACCOUNTING_CODE = "tmp_code";
  public static final String NO_INVOICE_LINES_ERROR_MSG = "An invoice cannot be approved if there are no corresponding lines of invoice.";
  private final InvoiceLineHelper invoiceLineHelper;
  private final VoucherHelper voucherHelper;
  private final VoucherLineHelper voucherLineHelper;

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";
  private static final String DELETE_INVOICE_BY_ID = resourceByIdPath(INVOICES, "%s") + "?lang=%s";
  private static final String PO_LINE_BY_ID_ENDPOINT = resourceByIdPath(PO_LINES, "%s") + "?lang=%s";


  InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    invoiceLineHelper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
    voucherHelper = new VoucherHelper(httpClient, okapiHeaders, ctx, lang);
    voucherLineHelper = new VoucherLineHelper(httpClient, okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Invoice> createInvoice(Invoice invoice) {
    return generateFolioInvoiceNumber()
      .thenApply(invoice::withFolioInvoiceNo)
      .thenApply(JsonObject::mapFrom)
      .thenCompose(jsonInvoice -> createRecordInStorage(jsonInvoice, resourcesPath(INVOICES)))
      .thenApply(invoice::withId)
      .thenApply(this::withCalculatedTotals);
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
      // To calculate totals, related invoice lines have to be retrieved
      .thenCompose(invoice -> getInvoiceLinesWithTotals(invoice).thenApply(lines -> withCalculatedTotals(invoice, lines)))
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
      String queryParam = getEndpointWithQuery(query, logger);
      String endpoint = String.format(GET_INVOICES_BY_QUERY, limit, offset, queryParam, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenAccept(jsonInvoices -> {
        logger.info("Successfully retrieved invoices: " + jsonInvoices.encodePrettily());
        future.complete(jsonInvoices.mapTo(InvoiceCollection.class));
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

  public CompletableFuture<Void> deleteInvoice(String id) {
    return handleDeleteRequest(String.format(DELETE_INVOICE_BY_ID, id, lang), httpClient, ctx, okapiHeaders, logger);
  }

  public CompletableFuture<Void> updateInvoice(Invoice invoice) {
    logger.debug("Updating invoice...");

    return getInvoiceRecord(invoice.getId())
      .thenApply(invoiceFromStorage -> {
        validateInvoice(invoice, invoiceFromStorage);
        setSystemGeneratedData(invoiceFromStorage, invoice);
        return invoiceFromStorage;
      })
      .thenCompose(invoiceFromStorage -> processInvoice(invoice, invoiceFromStorage))
      .thenCompose(aVoid -> updateInvoiceRecord(invoice));
  }

  private void validateInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    if(isFieldsVerificationNeeded(invoiceFromStorage)) {
      Set<String> fields = findChangedProtectedFields(invoice, invoiceFromStorage, InvoiceProtectedFields.getFieldNames());
      verifyThatProtectedFieldsUnchanged(fields);
    }
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
  }

  private CompletionStage<Void> processInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    if (isTransitionToApproved(invoiceFromStorage, invoice)) {
      return approveInvoice(invoice);
    } else if (isTransitionToPaid(invoiceFromStorage, invoice)) {
      return payInvoice(invoice);
    }
    return VertxCompletableFuture.completedFuture(null);
  }

  private boolean isTransitionToApproved(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() == Invoice.Status.REVIEWED && invoice.getStatus() == Invoice.Status.APPROVED;
  }

  private CompletableFuture<Void> approveInvoice(Invoice invoice) {
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);
    getInvoiceLinesWithTotals(invoice)
      .thenApply(invoiceLines -> {
        verifyInvoiceLineNotEmpty(invoiceLines);
        return invoiceLines;
      })
      .thenCombine(buildOrLinkVoucher(invoice), (invoiceLines, voucher) -> {
        invoice.setVoucherNumber(voucher.getVoucherNumber());
        return handleVoucherWithLines(invoiceLines, voucher)
          .thenAccept(future::complete)
          .exceptionally(t -> {
            future.completeExceptionally(t);
            return null;
          });
      })
    .exceptionally(t -> {
      future.completeExceptionally(t);
      return null;
    });
    return future;
  }

  private void verifyInvoiceLineNotEmpty(List<InvoiceLine> invoiceLines) {
    if (invoiceLines.isEmpty()) {
      throw new HttpException(500, NO_INVOICE_LINES_ERROR_MSG);
    }
  }

  private CompletableFuture<Voucher> buildOrLinkVoucher(Invoice invoice) {
    return findExistingVoucher(invoice.getId())
      .thenCompose(vouchers -> {
        if (isNotEmpty(vouchers)){
          Voucher voucher = vouchers.get(0);
          voucher.setInvoiceCurrency(invoice.getCurrency());
          setVoucherDefaultRequiredFields(voucher);
          return getExchangeRate(voucher)
            .thenAccept(voucher::setExchangeRate)
            .thenCompose(v -> VertxCompletableFuture.completedFuture(voucher));
        }
        return buildNewVoucher(invoice);
      });
  }

  private CompletableFuture<List<Voucher>> findExistingVoucher(String invoiceId) {
    return voucherHelper.getVouchers(1, 0, String.format(QUERY_BY_INVOICE_ID, invoiceId))
      .thenApply(VoucherCollection::getVouchers);
  }

  private void setVoucherDefaultRequiredFields(Voucher voucher) {
    voucher.setAccountingCode(DEFAULT_ACCOUNTING_CODE);
    voucher.setExportToAccounting(false);
    voucher.setType(Voucher.Type.VOUCHER);
    voucher.setStatus(Voucher.Status.AWAITING_PAYMENT);
  }

  private CompletableFuture<Double> getExchangeRate(Voucher voucher) {
    return VertxCompletableFuture.supplyAsync(() -> MonetaryConversions.getExchangeRateProvider()
      .getExchangeRate(voucher.getInvoiceCurrency(), voucher.getSystemCurrency())
      .getFactor().doubleValue());
  }

  private CompletableFuture<Voucher> buildNewVoucher(Invoice invoice) {

    Voucher voucher = new Voucher();
    voucher.setInvoiceId(invoice.getId());
    voucher.setInvoiceCurrency(invoice.getCurrency());
    setVoucherDefaultRequiredFields(voucher);

    return loadConfiguration("ORG", "localeSettings")
      .thenAccept(configs -> {
        JsonObject config = new JsonObject(configs.get(0).getValue());
        voucher.setSystemCurrency(config.getString("currency", DEFAULT_SYSTEM_CURRENCY));
      })
      .thenCompose(v -> getExchangeRate(voucher))
      .thenAccept(voucher::setExchangeRate)
      .thenCompose(v -> voucherHelper.generateVoucherNumber())
      .thenApply(voucher::withVoucherNumber);
  }

  private CompletableFuture<Void> handleVoucherWithLines(List<InvoiceLine> invoiceLines, Voucher voucher) {
    return groupFundDistributionInvoiceLinePairsByExternalAccountNo(invoiceLines)
      .thenCompose(groupedByExternalAccountNoFundDistributions -> {
        List<VoucherLine> voucherLines = buildVoucherLineRecords(groupedByExternalAccountNoFundDistributions, invoiceLines, voucher);
        Double calculatedAmount = HelperUtils.calculateVoucherAmount(voucher, voucherLines);
        voucher.setAmount(calculatedAmount);
        return handleVoucher(voucher)
          .thenCompose(voucherWithId -> handleVoucherLines(voucherLines, voucherWithId));
      });
  }

  private CompletableFuture<Map<String, List<FundDistribution>>> groupFundDistributionInvoiceLinePairsByExternalAccountNo(List<InvoiceLine> invoiceLines) {
    Map<String, List<FundDistribution>> fundDistributionInvoiceLinePairsGroupedByFundId = getGroupFundDistributionInvoiceLinePairsByFundId(invoiceLines);
    return fetchFounds(new ArrayList<>(fundDistributionInvoiceLinePairsGroupedByFundId.keySet()))
      .thenApply(funds -> funds.stream().collect(groupingBy(Fund::getExternalAccountNo)))
      .thenApply(stringListMap -> stringListMap.keySet().stream()
        .collect(toMap(externalAccountNo -> externalAccountNo, externalAccountNo -> stringListMap.get(externalAccountNo).stream()
          .map(Fund::getId)
          .flatMap(fundId -> fundDistributionInvoiceLinePairsGroupedByFundId.get(fundId).stream())
          .collect(toList()))));
  }

  private Map<String, List<FundDistribution>> getGroupFundDistributionInvoiceLinePairsByFundId(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
        .map(fundDistribution -> fundDistribution.withInvoiceLineId(invoiceLine.getId())))
      .collect(groupingBy(FundDistribution::getFundId));
  }

  private CompletableFuture<List<Fund>> fetchFounds(List<String> fundIds) {
    List<CompletableFuture<List<Fund>>> futures = StreamEx
      .ofSubLists(fundIds, MAX_IDS_FOR_GET_RQ)
      // Send get request for each CQL query
      .map(this::getFoundsByIds)
      .collect(toList());
    return collectResultsOnSuccess(futures)
      .thenApply(results -> results
        .stream()
        .flatMap(List::stream)
        .collect(toList())
      )
      .thenApply(existedFunds -> verifyThatAllFundsFound(existedFunds, fundIds));
  }

  private List<Fund> verifyThatAllFundsFound(List<Fund> existedFunds, List<String> fundIds) {
    if (fundIds.size() != existedFunds.size()) {
      List<String> idsNotFound =  fundIds.stream()
        .filter(id -> existedFunds.stream().
          map(Fund::getId)
          .noneMatch(fundIds::contains))
        .collect(toList());
      if (isNotEmpty(idsNotFound)) {
        throw new HttpException(500, buildFundNotFoundError(idsNotFound));
      }
    }
    return existedFunds;
  }

  private Error buildFundNotFoundError(List<String> idsNotFound) {
    Parameter parameter = new Parameter().withKey("fundIds").withValue(idsNotFound.toString());
    return new Error()
      .withCode(FUNDS_NOT_FOUND.getCode())
      .withMessage(FUNDS_NOT_FOUND.getDescription())
      .withParameters(Collections.singletonList(parameter));
  }

  private CompletableFuture<List<Fund>> getFoundsByIds(List<String> ids) {
    String query = encodeQuery(HelperUtils.convertIdsToCqlQuery(ids), logger);
    String endpoint = String.format("/finance-storage/funds?query=%s&limit=%s&lang=%s", query, ids.size(), lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(this::extractFounds);
  }

  private List<VoucherLine> buildVoucherLineRecords(Map<String, List<FundDistribution>> fundDistributionsGroupedByExternalAccountNo, List<InvoiceLine> invoiceLines, Voucher voucher) {
    return fundDistributionsGroupedByExternalAccountNo.entrySet().stream()
      .map(entry -> buildVoucherLineRecord(entry, invoiceLines , voucher))
      .collect(Collectors.toList());
  }

  private VoucherLine buildVoucherLineRecord(Map.Entry<String, List<FundDistribution>> foundDistributionsAssociatedWithAccountNumber, List<InvoiceLine> invoiceLines, Voucher voucher) {
    return new VoucherLine()
      .withVoucherId(voucher.getId())
      .withExternalAccountNumber(foundDistributionsAssociatedWithAccountNumber.getKey())
      .withFundDistributions(foundDistributionsAssociatedWithAccountNumber.getValue())
      .withSourceIds(invoiceLines.stream()
        .map(InvoiceLine::getId)
        .collect(toList()))
      .withAmount(calculateVoucherLineAmount(foundDistributionsAssociatedWithAccountNumber.getValue(), invoiceLines, voucher));
  }


  private CompletableFuture<Voucher> handleVoucher(Voucher voucher) {
    if (Objects.nonNull(voucher.getId())) {
      return voucherHelper.updateVoucher(voucher)
        .thenApply(aVoid -> voucher);
    } else {
      return voucherHelper.createVoucher(voucher);
    }
  }

  private CompletableFuture<Void> handleVoucherLines(List<VoucherLine> voucherLines, Voucher voucher) {
    populateVoucherId(voucherLines, voucher);
    return deleteVoucherLinesIfExist(voucher.getId())
      .thenCompose(v -> createVoucherLinesRecords(voucherLines));
  }

  private void populateVoucherId(List<VoucherLine> voucherLines, Voucher voucher) {
    voucherLines.forEach(voucherLine -> voucherLine.setVoucherId(voucher.getId()));
  }

  private CompletableFuture<Void> deleteVoucherLinesIfExist(String voucherId) {
    return getVoucherLineIdsByVoucherId(voucherId)
      .thenCompose(ids -> VertxCompletableFuture.allOf(ids.stream()
        .map(id -> voucherLineHelper.deleteVoucherLine(id))
        .toArray(CompletableFuture[]::new)));
  }

  private CompletableFuture<List<String>> getVoucherLineIdsByVoucherId(String voucherId) {
    String query = "voucherId==" + voucherId;
    return voucherLineHelper.getVoucherLines(Integer.MAX_VALUE, 0, query)
      .thenApply(voucherLineCollection ->  voucherLineCollection.getVoucherLines().
        stream()
        .map(voucherLine -> voucherLine.getId())
        .collect(toList())
      );
  }

  public boolean validateIncomingInvoice(Invoice invoice) {
    if(invoice.getLockTotal() && Objects.isNull(invoice.getTotal())) {
      addProcessingError(INVOICE_TOTAL_REQUIRED.toError());
    }
    return getErrors().isEmpty();
  }

  private CompletableFuture<Void> updateInvoiceRecord(Invoice updatedInvoice) {
    JsonObject jsonInvoice = JsonObject.mapFrom(updatedInvoice);
    return handlePutRequest(resourceByIdPath(INVOICES, updatedInvoice.getId()), jsonInvoice, httpClient, ctx, okapiHeaders, logger);
  }


  private boolean isTransitionToPaid(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.PAID;
  }

  private CompletableFuture<Void> createVoucherLinesRecords(List<VoucherLine> voucherLines) {
    return allOf(voucherLines.stream()
      .map(this::createVoucherLine)
      .toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<String> createVoucherLine(VoucherLine voucherLine) {
    JsonObject line = JsonObject.mapFrom(voucherLine);
    return createRecordInStorage(line, resourcesPath(VOUCHER_LINES));
  }

  private List<Fund> extractFounds(JsonObject entries) {
    FundCollection fundCollection = entries.mapTo(FundCollection.class);
    return fundCollection.getFunds();
  }

  /**
   * Handles transition of given invoice to PAID status.
   *
   * @param invoice Invoice to paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payInvoice(Invoice invoice) {
    return fetchInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(this::groupInvoiceLinesByPoLineId)
      .thenCompose(this::fetchPoLines)
      .thenApply(this::updatePoLinesPaymentStatus)
      .thenCompose(this::updateCompositePoLines);
  }


  private CompletableFuture<List<InvoiceLine>> fetchInvoiceLinesByInvoiceId(String invoiceId) {
    String query = String.format(QUERY_BY_INVOICE_ID, invoiceId);
    // Assuming that the invoice will never contain more than Integer.MAX_VALUE invoiceLines.
    return invoiceLineHelper.getInvoiceLines(Integer.MAX_VALUE, 0, query)
      .thenApply(InvoiceLineCollection::getInvoiceLines);
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
      .collect(Collectors.groupingBy(InvoiceLine::getPoLineId));
  }

  private CompletableFuture<Map<CompositePoLine, CompositePoLine.PaymentStatus>> fetchPoLines(Map<String, List<InvoiceLine>> poLineIdsWithInvoiceLines) {
    List<CompletableFuture<CompositePoLine>> futures = poLineIdsWithInvoiceLines.keySet()
      .stream()
      .map(this::getPoLineById)
      .collect(toList());

    return VertxCompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toMap(poLine -> poLine, poLine -> getPoLinePaymentStatus(poLineIdsWithInvoiceLines.get(poLine.getId())))));
  }

  private CompletableFuture<CompositePoLine> getPoLineById(String poLineId) {
    return handleGetRequest(String.format(PO_LINE_BY_ID_ENDPOINT, poLineId, lang), httpClient, ctx, okapiHeaders, logger)
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
    return VertxCompletableFuture.allOf(poLines.stream()
      .map(JsonObject::mapFrom)
      .map(poLine -> handlePutRequest(String.format(PO_LINE_BY_ID_ENDPOINT, poLine.getString(ID), lang), poLine, httpClient, ctx, okapiHeaders, logger))
      .toArray(CompletableFuture[]::new));
  }


  private CompletableFuture<String> generateFolioInvoiceNumber() {
    return HelperUtils.handleGetRequest(resourcesPath(FOLIO_INVOICE_NUMBER), httpClient, ctx, okapiHeaders, logger)
      .thenApply(seqNumber -> seqNumber.mapTo(SequenceNumber.class).getSequenceNumber());
  }

  private Invoice withCalculatedTotals(Invoice invoice) {
    return withCalculatedTotals(invoice, Collections.emptyList());
  }

  private Invoice withCalculatedTotals(Invoice invoice, List<InvoiceLine> lines) {
    CurrencyUnit currency = Monetary.getCurrency(invoice.getCurrency());

    // 1. Sub-total
    MonetaryAmount subTotal = calculateSubTotal(lines, currency);

    // 2. Adjustments (sum of not prorated invoice level and all invoice line level adjustments)
    MonetaryAmount adjustmentsTotal = calculateAdjustmentsTotal(getNotProratedAdjustments(invoice), subTotal)
      .add(calculateInvoiceLinesAdjustmentsTotal(lines, currency));

    // 3. Total
    if (!invoice.getLockTotal()) {
      invoice.setTotal(convertToDouble(subTotal.add(adjustmentsTotal)));
    }
    invoice.setAdjustmentsTotal(convertToDouble(adjustmentsTotal));
    invoice.setSubTotal(convertToDouble(subTotal));

    return invoice;
  }

  private List<Adjustment> getNotProratedAdjustments(Invoice invoice) {
    return invoice.getAdjustments()
      .stream()
      .filter(adj -> adj.getProrate() == Adjustment.Prorate.NOT_PRORATED)
      .collect(toList());
  }

  private MonetaryAmount calculateSubTotal(List<InvoiceLine> lines, CurrencyUnit currency) {
    return lines.stream()
      .map(line -> Money.of(line.getSubTotal(), currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }

  private MonetaryAmount calculateInvoiceLinesAdjustmentsTotal(List<InvoiceLine> lines, CurrencyUnit currency) {
    return lines.stream()
      .map(line -> Money.of(line.getAdjustmentsTotal(), currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }
}
