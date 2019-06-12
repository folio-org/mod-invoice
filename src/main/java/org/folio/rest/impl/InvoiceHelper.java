package org.folio.rest.impl;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.INVOICE_TOTAL_REQUIRED;
import static org.folio.invoices.utils.HelperUtils.calculateAdjustmentsTotal;
import static org.folio.invoices.utils.HelperUtils.convertToDouble;
import static org.folio.invoices.utils.ErrorCodes.PO_LINE_NOT_FOUND;
import static org.folio.invoices.utils.HelperUtils.findChangedProtectedFields;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isFieldsVerificationNeeded;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceProtectedFields;
import org.folio.rest.acq.model.CompositePoLine;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.javamoney.moneta.Money;

import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.Parameter;
import org.javamoney.moneta.function.MonetaryFunctions;

public class InvoiceHelper extends AbstractHelper {

  public static final String TOTAL = "total";
  private final InvoiceLineHelper invoiceLineHelper;

  private static final String GET_INVOICES_BY_QUERY = resourcesPath(INVOICES) + "?limit=%s&offset=%s%s&lang=%s";
  private static final String DELETE_INVOICE_BY_ID = resourceByIdPath(INVOICES, "%s") + "?lang=%s";
  private static final String PO_LINE_BY_ID_ENDPOINT = "/orders/order-lines/%s?lang=%s";

  InvoiceHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    invoiceLineHelper = new InvoiceLineHelper(httpClient, okapiHeaders, ctx, lang);
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
      .thenCompose(invoiceFromStorage -> {
        if (isTransitionToPaid(invoiceFromStorage, invoice)) {
          return payInvoice(invoice);
        } else {
          return updateInvoiceRecord(invoice);
        }
      });
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
    return handlePutRequest(resourceByIdPath(INVOICES, updatedInvoice.getId()), jsonInvoice, httpClient, ctx, okapiHeaders, logger);
  }

  private void setSystemGeneratedData(Invoice invoiceFromStorage, Invoice invoice) {
    invoice.withFolioInvoiceNo(invoiceFromStorage.getFolioInvoiceNo());
  }

  private boolean isTransitionToPaid(Invoice invoiceFromStorage, Invoice invoice) {
    return invoiceFromStorage.getStatus() == Invoice.Status.APPROVED && invoice.getStatus() == Invoice.Status.PAID;
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
      .thenCompose(this::updateCompositePoLines)
      .thenCompose(aVoid -> updateInvoiceRecord(invoice));
  }


  private CompletableFuture<List<InvoiceLine>> fetchInvoiceLinesByInvoiceId(String invoiceId) {
    String query = "invoiceId==" + invoiceId;
    // Assuming that the invoice will never contain more than Integer.MAX_VALUE invoiceLines.
    return invoiceLineHelper.getInvoiceLines(Integer.MAX_VALUE, 0, query)
      .thenApply(InvoiceLineCollection::getInvoiceLines);
  }

  private CompletableFuture<List<InvoiceLine>> getInvoiceLinesWithTotals(Invoice invoice) {
    return fetchInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(lines -> {
        lines.forEach(line -> invoiceLineHelper.calculateInvoiceLineTotals(line, invoice));
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
