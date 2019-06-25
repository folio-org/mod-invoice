package org.folio.rest.impl;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.invoices.utils.ErrorCodes.FUNDS_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_TOTAL_REQUIRED;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_UPDATE_FAILURE;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.calculateAdjustmentsTotal;
import static org.folio.invoices.utils.HelperUtils.calculateVoucherLineAmount;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.findChangedProtectedFields;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isFieldsVerificationNeeded;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.ORDER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.ArrayList;
import java.util.Collections;
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
import javax.money.convert.MonetaryConversions;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.acq.model.finance.FundCollection;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import one.util.streamex.StreamEx;

public class InvoiceHelper extends AbstractHelper {

  static final int MAX_IDS_FOR_GET_RQ = 15;
  private static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  private static final String DEFAULT_ACCOUNTING_CODE = "tmp_code";
  public static final String NO_INVOICE_LINES_ERROR_MSG = "An invoice cannot be approved if there are no corresponding lines of invoice.";
  public static final String TOTAL = "total";
  public static final String SYSTEM_CONFIG_NAME = "ORG";
  public static final String LOCALE_SETTINGS = "localeSettings";
  public static final String SYSTEM_CURRENCY_PROPERTY_NAME = "currency";
  public static final String FUND_SEARCHING_ENDPOINT = "/finance-storage/funds?query=%s&limit=%s&lang=%s";
  public static final String IMF_EXCHANGE_RATE_PROVIDER = "IMF";
  private final InvoiceLineHelper invoiceLineHelper;
  private final VoucherHelper voucherHelper;
  private final VoucherLineHelper voucherLineHelper;

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";

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
    return handleDeleteRequest(resourceByIdPath(INVOICES, id, lang), httpClient, ctx, okapiHeaders, logger);
  }

  public CompletableFuture<Void> updateInvoice(Invoice invoice) {
    logger.debug("Updating invoice...");

    return getInvoiceRecord(invoice.getId())
      .thenApply(invoiceFromStorage -> {
        validateInvoice(invoice, invoiceFromStorage);
        setSystemGeneratedData(invoiceFromStorage, invoice);
        return invoiceFromStorage;
      })
      .thenCompose(invoiceFromStorage -> handleInvoiceStatusTransition(invoice, invoiceFromStorage))
      .thenCompose(aVoid -> updateInvoiceRecord(invoice));
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
  }

  private CompletionStage<Void> handleInvoiceStatusTransition(Invoice invoice, Invoice invoiceFromStorage) {
    if (isTransitionToApproved(invoiceFromStorage, invoice)) {
      return approveInvoice(invoice);
    } else if (isTransitionToPaid(invoiceFromStorage, invoice)) {
      return payInvoice(invoice);
    }
    return VertxCompletableFuture.completedFuture(null);
  }

  private boolean isTransitionToApproved(Invoice invoiceFromStorage, Invoice invoice) {
    return (invoiceFromStorage.getStatus() == Invoice.Status.REVIEWED || invoiceFromStorage.getStatus() == Invoice.Status.OPEN) && invoice.getStatus() == Invoice.Status.APPROVED;
  }

  /**
   * Handles transition of given invoice to {@link Invoice.Status#APPROVED} status.
   * Transition only triggers if the current {@link Invoice.Status} is {@link Invoice.Status#REVIEWED}
   * and exist at least one {@link InvoiceLine} associated with this invoice
   *
   * @param invoice {@link Invoice}to be approved
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> approveInvoice(Invoice invoice) {
    CompletableFuture<Void> future = new VertxCompletableFuture<>(ctx);
    getInvoiceLinesWithTotals(invoice)
      .thenApply(invoiceLines -> {
        verifyInvoiceLineNotEmpty(invoiceLines);
        return invoiceLines;
      })
      .thenCombine(prepareVoucher(invoice), (invoiceLines, voucher) -> {
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
          return VertxCompletableFuture.completedFuture(voucher);
        }
        return buildNewVoucher(invoice);
      })
      .thenApply(voucher -> withRequiredFields(voucher, invoice));
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

    //TODO Start using real information to create a voucher when it becomes known where to get these values from.
    voucher.setAccountingCode(DEFAULT_ACCOUNTING_CODE);
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

    return voucherHelper.generateVoucherNumber()
      .thenApply(voucher::withVoucherNumber);
  }

  /**
   *  Handles creation (or update) of prepared voucher and voucher lines creation
   *
   * @param invoiceLines {@link List<InvoiceLine>} associated with processed invoice
   * @param voucher associated with processed invoice
   * @return CompletableFuture that indicates when handling is completed
   */
  private CompletableFuture<Void> handleVoucherWithLines(List<InvoiceLine> invoiceLines, Voucher voucher) {
    return getSystemCurrency()
      .thenCompose(systemCurrency -> setExchangeRateFactor(voucher.withSystemCurrency(systemCurrency)))
      .thenCompose(v -> groupFundDistrosByExternalAcctNo(invoiceLines))
      .thenApply(fundDistrosGroupedByExternalAcctNo -> buildVoucherLineRecords(fundDistrosGroupedByExternalAcctNo, invoiceLines, voucher))
      .thenCompose(voucherLines -> {
        Double calculatedAmount = HelperUtils.calculateVoucherAmount(voucher, voucherLines);
        voucher.setAmount(calculatedAmount);
        return handleVoucher(voucher)
          .thenAccept(voucherWithId -> populateVoucherId(voucherLines, voucher))
          .thenCompose(v -> createVoucherLinesRecords(voucherLines));
      });
  }

  /**
   *  Retrieves systemCurrency from mod-configuration
   *  if config is empty than use {@link #DEFAULT_SYSTEM_CURRENCY}
   */
  private CompletableFuture<String> getSystemCurrency() {
    return loadConfiguration(SYSTEM_CONFIG_NAME, LOCALE_SETTINGS)
      .thenApply(configs -> {
        JsonObject config = configs.stream().map(conf -> new JsonObject(conf.getValue())).findFirst().orElseGet(JsonObject::new);
        return config.getString(SYSTEM_CURRENCY_PROPERTY_NAME, DEFAULT_SYSTEM_CURRENCY);
      });
  }

  private CompletableFuture<Void> setExchangeRateFactor(Voucher voucher) {
    return VertxCompletableFuture.supplyBlockingAsync(ctx, () -> MonetaryConversions
        .getExchangeRateProvider(IMF_EXCHANGE_RATE_PROVIDER)
        .getExchangeRate(voucher.getInvoiceCurrency(), voucher.getSystemCurrency()))
      .thenAccept(exchangeRate -> voucher.setExchangeRate(exchangeRate.getFactor().doubleValue()));
  }

  /**
   * Prepares the data necessary for the generation of voucher lines based on the invoice lines found
   *
   * @param invoiceLines {@link List<InvoiceLine>} associated with processed {@link Invoice}
   * @return {@link InvoiceLine#fundDistributions} grouped by {@link Fund#externalAccountNo}
   */
  private CompletableFuture<Map<String, List<FundDistribution>>> groupFundDistrosByExternalAcctNo(
      List<InvoiceLine> invoiceLines) {

    Map<String, List<FundDistribution>> fundDistrosGroupedByFundId = groupFundDistrosByFundId(invoiceLines);

    return fetchFundsByIds(new ArrayList<>(fundDistrosGroupedByFundId.keySet()))
      .thenApply(this::groupFundsByExternalAcctNo)
      .thenApply(fundsGroupedByExternalAcctNo ->
        mapExternalAcctNoToFundDistros(fundDistrosGroupedByFundId, fundsGroupedByExternalAcctNo)
      );
  }

  private Map<String, List<Fund>> groupFundsByExternalAcctNo(List<Fund> funds) {
    return funds.stream().collect(groupingBy(Fund::getExternalAccountNo));
  }

  private Map<String, List<FundDistribution>> mapExternalAcctNoToFundDistros(
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

  private Map<String, List<FundDistribution>> groupFundDistrosByFundId(List<InvoiceLine> invoiceLines) {
    return invoiceLines.stream()
      .flatMap(invoiceLine -> invoiceLine.getFundDistributions().stream()
        .map(fundDistribution -> fundDistribution.withInvoiceLineId(invoiceLine.getId())))
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
    if (fundIds.size() != existingFunds.size()) {
      List<String> idsNotFound = collectFundIdsThatWasNotFound(existingFunds, fundIds);
      if (isNotEmpty(idsNotFound)) {
        throw new HttpException(500, buildFundNotFoundError(idsNotFound));
      }
    }
    return existingFunds;
  }

  private List<String> collectFundIdsThatWasNotFound(List<Fund> existingFunds, List<String> fundIds) {
    return fundIds.stream()
      .filter(id -> existingFunds.stream().
        map(Fund::getId)
        .noneMatch(fundIds::contains))
      .collect(toList());
  }

  private Error buildFundNotFoundError(List<String> idsNotFound) {
    Parameter parameter = new Parameter().withKey("fundIds").withValue(idsNotFound.toString());
    return new Error()
      .withCode(FUNDS_NOT_FOUND.getCode())
      .withMessage(FUNDS_NOT_FOUND.getDescription())
      .withParameters(Collections.singletonList(parameter));
  }

  private CompletableFuture<List<Fund>> getFundsByIds(List<String> ids) {
    String query = encodeQuery(HelperUtils.convertIdsToCqlQuery(ids), logger);
    String endpoint = String.format(FUND_SEARCHING_ENDPOINT, query, ids.size(), lang);
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenApply(this::extractFunds);
  }

  private List<Fund> extractFunds(JsonObject entries) {
    FundCollection fundCollection = entries.mapTo(FundCollection.class);
    return fundCollection.getFunds();
  }

  private List<VoucherLine> buildVoucherLineRecords(Map<String, List<FundDistribution>> fundDistroGroupedByExternalAcctNo, List<InvoiceLine> invoiceLines, Voucher voucher) {
    return fundDistroGroupedByExternalAcctNo.entrySet().stream()
      .map(entry -> buildVoucherLineRecord(entry, invoiceLines , voucher))
      .collect(Collectors.toList());
  }

  private VoucherLine buildVoucherLineRecord(Map.Entry<String, List<FundDistribution>> fundDistroAssociatedWithAcctNo, List<InvoiceLine> invoiceLines, Voucher voucher) {
    String externalAccountNumber = fundDistroAssociatedWithAcctNo.getKey();
    List<FundDistribution> fundDistributions = fundDistroAssociatedWithAcctNo.getValue();

    return new VoucherLine()
      .withExternalAccountNumber(externalAccountNumber)
      .withFundDistributions(fundDistributions)
      .withSourceIds(collectInvoiceLineIds(fundDistributions))
      .withAmount(calculateVoucherLineAmount(fundDistributions, invoiceLines, voucher));
  }

  private List<String> collectInvoiceLineIds(List<FundDistribution> fundDistributions) {
    return fundDistributions
      .stream()
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
      .thenCompose(ids -> VertxCompletableFuture.allOf(ids.stream()
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

  public boolean validateIncomingInvoice(Invoice invoice) {
    if(invoice.getLockTotal() && Objects.isNull(invoice.getTotal())) {
      addProcessingError(INVOICE_TOTAL_REQUIRED.toError());
    }
    return getErrors().isEmpty();
  }

  private void validateInvoice(Invoice invoice, Invoice invoiceFromStorage) {
    if(isFieldsVerificationNeeded(invoiceFromStorage)) {
      Set<String> fields = findChangedProtectedFields(invoice, invoiceFromStorage, InvoiceProtectedFields.getFieldNames());

      // "total" depends on value of "lockTotal": if value is true, total is required; if false, read-only (system calculated)
      if (invoiceFromStorage.getLockTotal() && !Objects.equals(invoice.getTotal(), invoiceFromStorage.getTotal())) {
        fields.add(TOTAL);
      }
      verifyThatProtectedFieldsUnchanged(fields);
    }
  }

  private CompletableFuture<Void> updateInvoiceRecord(Invoice updatedInvoice) {
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
    return VertxCompletableFuture.allOf(ctx, payPoLines(invoice), payVoucher(invoice));
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

    return VertxCompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
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
    return VertxCompletableFuture.allOf(poLines.stream()
      .map(JsonObject::mapFrom)
      .map(poLine -> handlePutRequest(resourceByIdPath(ORDER_LINES, poLine.getString(ID), lang), poLine, httpClient, ctx, okapiHeaders, logger))
      .toArray(CompletableFuture[]::new))
      .exceptionally(fail -> {
        throw new HttpException(500, PO_LINE_UPDATE_FAILURE.toError());
      });
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
      invoice.setTotal(convertToDoubleWithRounding(subTotal.add(adjustmentsTotal)));
    }
    invoice.setAdjustmentsTotal(convertToDoubleWithRounding(adjustmentsTotal));
    invoice.setSubTotal(convertToDoubleWithRounding(subTotal));

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
