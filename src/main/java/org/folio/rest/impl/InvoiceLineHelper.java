package org.folio.rest.impl;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_INVOICE_LINE_CREATION;
import static org.folio.invoices.utils.HelperUtils.INVOICE;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.calculateInvoiceLineTotals;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.HelperUtils.findChangedProtectedFields;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;
import static org.folio.invoices.utils.HelperUtils.getProratedAdjustments;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.ProtectedOperationType.READ;
import static org.folio.invoices.utils.ProtectedOperationType.DELETE;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import org.folio.invoices.events.handlers.MessageAddress;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.InvoiceLineProtectedFields;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class InvoiceLineHelper extends AbstractHelper {

  private static final String INVOICE_LINE_NUMBER_ENDPOINT = resourcesPath(INVOICE_LINE_NUMBER) + "?" + INVOICE_ID + "=";
  public static final String GET_INVOICE_LINES_BY_QUERY = resourcesPath(INVOICE_LINES) + SEARCH_PARAMS;

  private final ProtectionHelper protectionHelper;

  InvoiceLineHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  InvoiceLineHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
    protectionHelper = new ProtectionHelper(httpClient, okapiHeaders, ctx, lang);
  }

  public CompletableFuture<InvoiceLineCollection> getInvoiceLines(int limit, int offset, String query) {
    return protectionHelper.buildAcqUnitsCqlExprToSearchRecords(INVOICE_LINES)
      .thenCompose(acqUnitsCqlExpr -> {
        String queryParam;
        if (isEmpty(query)) {
          queryParam = getEndpointWithQuery(acqUnitsCqlExpr, logger);
        } else {
          queryParam = getEndpointWithQuery(combineCqlExpressions("and", acqUnitsCqlExpr, query), logger);
        }
        String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, limit, offset, queryParam, lang);
        return getInvoiceLineCollection(endpoint);
      });
  }

  public CompletableFuture<List<InvoiceLine>> getInvoiceLinesByInvoiceId(String invoiceId) {
    String query = getEndpointWithQuery(String.format(QUERY_BY_INVOICE_ID, invoiceId), logger);
    // Assuming that the invoice will never contain more than Integer.MAX_VALUE invoiceLines.
    String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, Integer.MAX_VALUE, 0, query, lang);
    return getInvoiceLineCollection(endpoint).thenApply(InvoiceLineCollection::getInvoiceLines);
  }

  CompletableFuture<InvoiceLineCollection> getInvoiceLineCollection(String endpoint) {
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
      .thenCompose(json -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> json.mapTo(InvoiceLineCollection.class)));
  }

  public CompletableFuture<InvoiceLine> getInvoiceLine(String id) {
    CompletableFuture<InvoiceLine> future = new VertxCompletableFuture<>(ctx);

    try {
      handleGetRequest(resourceByIdPath(INVOICE_LINES, id, lang), httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonInvoiceLine -> {
          logger.info("Successfully retrieved invoice line: " + jsonInvoiceLine.encodePrettily());
          future.complete(jsonInvoiceLine.mapTo(InvoiceLine.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting invoice line by id ", id);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  private void updateOutOfSyncInvoiceLine(InvoiceLine invoiceLine, Invoice invoice) {
    VertxCompletableFuture.runAsync(ctx, () -> {
      logger.info("Invoice line with id={} is out of date in storage and going to be updated", invoiceLine.getId());
      InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, ctx, lang);
      helper.updateInvoiceLineToStorage(invoiceLine)
        .handle((ok, fail) -> {
          if (fail == null) {
            updateInvoiceAsync(invoice);
          }
          helper.closeHttpClient();
          return null;
        });
    });
  }

  /**
   * Calculate invoice line total and compare with original value if it has changed
   *
   * @param invoiceLine invoice line to update totals for
   * @param invoice invoice record
   * @return {code true} if any total value is different to original one
   */
  private boolean reCalculateInvoiceLineTotals(InvoiceLine invoiceLine, Invoice invoice) {
    if (isPostApproval(invoice)) {
      return false;
    }

    // 1. Get original values
    Double existingTotal = invoiceLine.getTotal();
    Double subTotal = invoiceLine.getSubTotal();
    Double adjustmentsTotal = invoiceLine.getAdjustmentsTotal();

    // 2. Recalculate totals
    calculateInvoiceLineTotals(invoiceLine, invoice);

    // 3. Compare if anything has changed
    return !(Objects.equals(existingTotal, invoiceLine.getTotal())
        && Objects.equals(subTotal, invoiceLine.getSubTotal())
        && Objects.equals(adjustmentsTotal, invoiceLine.getAdjustmentsTotal()));
  }

  /**
   * Compares totals of 2 invoice lines
   *
   * @param invoiceLine1 first invoice line
   * @param invoiceLine2 second invoice line
   * @return {code true} if any total value is different to original one
   */
  private boolean areTotalsEqual(InvoiceLine invoiceLine1, InvoiceLine invoiceLine2) {
    return Objects.equals(invoiceLine1.getTotal(), invoiceLine2.getTotal())
      && Objects.equals(invoiceLine1.getSubTotal(), invoiceLine2.getSubTotal())
      && Objects.equals(invoiceLine1.getAdjustmentsTotal(), invoiceLine2.getAdjustmentsTotal());
  }

  /**
   * Gets invoice line by id and calculate total
   *
   * @param id invoice line uuid
   * @return completable future with {@link InvoiceLine} on success or an exception if processing fails
   */
  public CompletableFuture<InvoiceLine> getInvoiceLinePersistTotal(String id) {
    CompletableFuture<InvoiceLine> future = new VertxCompletableFuture<>(ctx);

    // GET invoice-line from storage
    getInvoiceLine(id)
      .thenCompose(invoiceLineFromStorage -> getInvoiceAndCheckProtection(invoiceLineFromStorage).thenApply(invoice -> {
        boolean isTotalOutOfSync = reCalculateInvoiceLineTotals(invoiceLineFromStorage, invoice);
        if (isTotalOutOfSync) {
          updateOutOfSyncInvoiceLine(invoiceLineFromStorage, invoice);
        }
        return invoiceLineFromStorage;
      }))
      .thenAccept(future::complete)
      .exceptionally(t -> {
        logger.error("Failed to get an Invoice Line by id={}", t.getCause(), id);
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public CompletableFuture<Void> updateInvoiceLineToStorage(InvoiceLine invoiceLine) {
    return handlePutRequest(resourceByIdPath(INVOICE_LINES, invoiceLine.getId(), lang), JsonObject.mapFrom(invoiceLine), httpClient,
        ctx, okapiHeaders, logger);
  }

  public CompletableFuture<Void> updateInvoiceLine(InvoiceLine invoiceLine) {

    return getInvoiceLine(invoiceLine.getId())
      .thenCompose(invoiceLineFromStorage -> getInvoice(invoiceLineFromStorage).thenCompose(invoice -> {
        // Validate if invoice line update is allowed
        validateInvoiceLine(invoice, invoiceLine, invoiceLineFromStorage);
        invoiceLine.setInvoiceLineNumber(invoiceLineFromStorage.getInvoiceLineNumber());

        // Just persist updates if invoice is already finalized
        if (isPostApproval(invoice)) {
          return updateInvoiceLineToStorage(invoiceLine);
        }

        // Re-apply prorated adjustments if available
        return applyProratedAdjustments(invoiceLine, invoice).thenCompose(lines -> {
          // Recalculate totals before update which also indicates if invoice requires update
          calculateInvoiceLineTotals(invoiceLine, invoice);
          // Update invoice line in storage
          return updateInvoiceLineToStorage(invoiceLine).thenRun(() -> {
            // Trigger invoice update event only if this is required
            if (!lines.isEmpty() || !areTotalsEqual(invoiceLine, invoiceLineFromStorage)) {
              updateInvoiceAndAffectedLinesAsync(invoice, lines);
            }
          });
        });
      }));
  }

  /**
   * Deletes Invoice Line and update Invoice if deletion is allowed
   * 1. Get invoiceLine by id
   * 2. Get invoice by id
   * 3. Verify if user has permission to delete invoiceLine based on acquisitions units, if not then return
   * 4. If user has permission to delete then delete invoiceLine
   * 5. Update corresponding Invoice
   * @param id invoiceLine id to be deleted
   */
  public CompletableFuture<Void> deleteInvoiceLine(String id) {
    return getInvoiceLine(id)
      .thenCompose(invoiceLine -> getInvoiceById(invoiceLine.getInvoiceId(), lang, httpClient, ctx, okapiHeaders, logger))
      .thenCompose(invoice -> {
        return protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), DELETE)
          .thenApply(vvoid -> invoice);
      })
      .thenCompose(invoice -> handleDeleteRequest(resourceByIdPath(INVOICE_LINES, id, lang), httpClient, ctx, okapiHeaders, logger)
        .thenRun(() -> updateInvoiceAndLinesAsync(invoice)));
  }

  private void validateInvoiceLine(Invoice existedInvoice, InvoiceLine invoiceLine, InvoiceLine existedInvoiceLine) {
    if(isPostApproval(existedInvoice)) {
      Set<String> fields = findChangedProtectedFields(invoiceLine, existedInvoiceLine, InvoiceLineProtectedFields.getFieldNames());
      verifyThatProtectedFieldsUnchanged(fields);
    }
  }

  /**
   * Creates Invoice Line if its content is valid
   * @param invoiceLine {@link InvoiceLine} to be created
   * @return completable future which might hold {@link InvoiceLine} on success, {@code null} if validation fails or an exception if any issue happens
   */
  CompletableFuture<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine) {
    return getInvoice(invoiceLine).thenApply(this::checkIfInvoiceLineCreationAllowed)
      .thenCompose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.CREATE)
        .thenApply(v -> invoice))
      .thenCompose(invoice -> createInvoiceLine(invoiceLine, invoice));
  }

  private Invoice checkIfInvoiceLineCreationAllowed(Invoice invoice) {
    if (isPostApproval(invoice)) {
      throw new HttpException(500, PROHIBITED_INVOICE_LINE_CREATION);
    }
    return invoice;
  }

  private CompletableFuture<Invoice> getInvoice(InvoiceLine invoiceLine) {
    return getInvoiceById(invoiceLine.getInvoiceId(), lang, httpClient, ctx, okapiHeaders, logger);
  }

  private CompletableFuture<Invoice> getInvoiceAndCheckProtection(InvoiceLine invoiceLineFromStorage) {
    return getInvoice(invoiceLineFromStorage)
      .thenCompose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), READ)
        .thenApply(aVoid -> invoice));
  }

  /**
   * Creates Invoice Line assuming its content is valid
   * @param invoiceLine {@link InvoiceLine} to be created
   * @param invoice associated {@link Invoice} object
   * @return completable future which might hold {@link InvoiceLine} on success or an exception if any issue happens
   */
  private CompletableFuture<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine, Invoice invoice) {
    return generateLineNumber(invoice).thenAccept(invoiceLine::setInvoiceLineNumber)
      // First the prorated adjustments should be applied. In case there is any, it might require to update other lines
      .thenCompose(ok -> applyProratedAdjustments(invoiceLine, invoice).thenCompose(affectedLines -> {
        calculateInvoiceLineTotals(invoiceLine, invoice);
        return createRecordInStorage(JsonObject.mapFrom(invoiceLine), resourcesPath(INVOICE_LINES)).thenApply(id -> {
          updateInvoiceAndAffectedLinesAsync(invoice, affectedLines);
          return invoiceLine.withId(id);
        });
      }));
  }

  private CompletableFuture<String> generateLineNumber(Invoice invoice) {
    return handleGetRequest(getInvoiceLineNumberEndpoint(invoice.getId()), httpClient, ctx, okapiHeaders, logger)
      .thenApply(sequenceNumberJson -> {
        SequenceNumber sequenceNumber = sequenceNumberJson.mapTo(SequenceNumber.class);
        return buildInvoiceLineNumber(invoice.getFolioInvoiceNo(), sequenceNumber.getSequenceNumber());
      });
  }

  private String buildInvoiceLineNumber(String folioInvoiceNumber, String sequence) {
    return folioInvoiceNumber + "-" + sequence;
  }

  private String getInvoiceLineNumberEndpoint(String id) {
    return INVOICE_LINE_NUMBER_ENDPOINT + id;
  }

  public MonetaryAmount summarizeSubTotals(List<InvoiceLine> lines, CurrencyUnit currency, boolean byAbsoluteValue) {
    return lines.stream()
      .map(InvoiceLine::getSubTotal)
      .map(subTotal -> Money.of(byAbsoluteValue ? Math.abs(subTotal) : subTotal, currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }

  public List<InvoiceLine> processProratedAdjustments(List<InvoiceLine> lines, Invoice invoice) {
    List<Adjustment> proratedAdjustments = getProratedAdjustments(invoice.getAdjustments());

    // Remove previously applied prorated adjustments if they are no longer available at invoice level
    List<InvoiceLine> updatedLines = filterDeletedAdjustments(proratedAdjustments, lines);

    // Apply prorated adjustments to each invoice line
    updatedLines.addAll(applyProratedAdjustments(proratedAdjustments, lines, invoice));

    // Return only unique invoice lines
    return updatedLines.stream()
      .distinct()
      .collect(toList());
  }

  /**
   * Removes adjustments at invoice line level based on invoice's prorated adjustments which are no longer available
   * @param proratedAdjustments list of prorated adjustments available at invoice level
   * @param invoiceLines list of invoice lines associated with current invoice
   */
  private List<InvoiceLine> filterDeletedAdjustments(List<Adjustment> proratedAdjustments, List<InvoiceLine> invoiceLines) {
    List<String> adjIds = proratedAdjustments.stream()
      .map(Adjustment::getId)
      .collect(toList());

    return invoiceLines.stream()
      .filter(line -> line.getAdjustments()
        .removeIf(adj -> (adj.getProrate() != Adjustment.Prorate.NOT_PRORATED) && !adjIds.contains(adj.getAdjustmentId())))
      .collect(toList());
  }

  private List<InvoiceLine> applyProratedAdjustments(List<Adjustment> proratedAdjustments, List<InvoiceLine> lines, Invoice invoice) {
    CurrencyUnit currencyUnit = Monetary.getCurrency(invoice.getCurrency());

    List<InvoiceLine> updatedLines = new ArrayList<>();
    for (Adjustment adjustment : proratedAdjustments) {
      switch (adjustment.getProrate()) {
      case BY_LINE:
        updatedLines.addAll(applyProratedAdjustmentByLines(adjustment, lines, currencyUnit));
        break;
      case BY_AMOUNT:
        updatedLines.addAll(applyProratedAdjustmentByAmount(adjustment, lines, currencyUnit));
        break;
      case BY_QUANTITY:
        updatedLines.addAll(applyProratedAdjustmentByQuantity(adjustment, lines, currencyUnit));
        break;
      default:
        logger.warn("Unexpected {} adjustment's prorate type for invoice with id={}", adjustment.getProrate(), invoice.getId());
      }
    }

    // Return only unique invoice lines
    return updatedLines.stream()
      .distinct()
      .collect(toList());
  }

  /**
   * Each invoiceLine gets adjustment value divided by quantity of lines
   */
  private List<InvoiceLine> applyProratedAdjustmentByLines(Adjustment adjustment, List<InvoiceLine> lines,
      CurrencyUnit currencyUnit) {

    List<InvoiceLine> updatedLines = new ArrayList<>();
    for (InvoiceLine line : lines) {
      Adjustment proratedAdjustment = prepareAdjustmentForLine(adjustment);

      if (adjustment.getType() == Adjustment.Type.AMOUNT) {
        proratedAdjustment.setValue(convertToDoubleWithRounding(Money.of(adjustment.getValue(), currencyUnit)
          .divide(lines.size())));
      } else {
        proratedAdjustment.setValue(BigDecimal.valueOf(adjustment.getValue())
          .divide(BigDecimal.valueOf(lines.size()), 15, RoundingMode.HALF_EVEN)
          .doubleValue());
      }

      if (addAdjustmentToLine(line, proratedAdjustment)) {
        updatedLines.add(line);
      }
    }

    return updatedLines;
  }

  /**
   * Each invoiceLine gets a portion of the amount proportionate to the invoiceLine's contribution to the invoice subTotal.
   * Prorated percentage adjustments of this type aren't split but rather each invoiceLine gets an adjustment of that percentage
   */
  private List<InvoiceLine> applyProratedAdjustmentByAmount(Adjustment adjustment, List<InvoiceLine> lines,
      CurrencyUnit currencyUnit) {

    MonetaryAmount grandSubTotal = summarizeSubTotals(lines, currencyUnit, true);
    if (adjustment.getType() == Adjustment.Type.AMOUNT && grandSubTotal.isZero()) {
      // If summarized subTotal (by abs) is zero, each line has zero amount (e.g. gift) so adjustment should be prorated "By line"
      return applyProratedAdjustmentByLines(adjustment, lines, currencyUnit);
    }

    List<InvoiceLine> updatedLines = new ArrayList<>();
    for (InvoiceLine line : lines) {
      Adjustment proratedAdjustment = prepareAdjustmentForLine(adjustment);

      if (adjustment.getType() == Adjustment.Type.AMOUNT) {
        proratedAdjustment.setValue(convertToDoubleWithRounding(Money.of(adjustment.getValue(), currencyUnit)
          // The adjustment amount should be calculated by absolute value of subTotal
          .multiply(Math.abs(line.getSubTotal()))
          .divide(grandSubTotal.getNumber())));
      }

      if (addAdjustmentToLine(line, proratedAdjustment)) {
        updatedLines.add(line);
      }
    }

    return updatedLines;
  }

  /**
   * Each invoiceLine gets an portion of the amount proportionate to the invoiceLine's quantity.
   */
  private List<InvoiceLine> applyProratedAdjustmentByQuantity(Adjustment adjustment, List<InvoiceLine> lines,
      CurrencyUnit currencyUnit) {

    if (adjustment.getType() == Adjustment.Type.PERCENTAGE) {
      /*
       * "By quantity" prorated percentage adjustments don't make sense and the client should not use that combination. We will also
       * need to validate this on the backend and return an error response in the case where someone makes a call directly to the
       * API with the combo.
       */
      return Collections.emptyList();
    }

    List<InvoiceLine> updatedLines = new ArrayList<>();
    for (InvoiceLine line : lines) {
      Adjustment proratedAdjustment = prepareAdjustmentForLine(adjustment)
        .withValue(convertToDoubleWithRounding(Money.of(adjustment.getValue(), currencyUnit)
          .multiply(line.getQuantity())
          .divide(getTotalQuantities(lines))));

      if (addAdjustmentToLine(line, proratedAdjustment)) {
        updatedLines.add(line);
      }
    }

    return updatedLines;
  }

  private Adjustment prepareAdjustmentForLine(Adjustment adjustment) {
    return JsonObject.mapFrom(adjustment)
      .mapTo(adjustment.getClass())
      .withId(null)
      .withAdjustmentId(adjustment.getId());
  }

  private Integer getTotalQuantities(List<InvoiceLine> lines) {
    return lines.stream().map(InvoiceLine::getQuantity).reduce(0, Integer::sum);
  }

  private boolean addAdjustmentToLine(InvoiceLine line, Adjustment proratedAdjustment) {
    List<Adjustment> lineAdjustments = line.getAdjustments();
    if (!lineAdjustments.contains(proratedAdjustment)) {
      // Just in case there was adjustment with this uuid but now updated, remove it
      lineAdjustments.removeIf(adj -> proratedAdjustment.getAdjustmentId().equals(adj.getAdjustmentId()));
      lineAdjustments.add(proratedAdjustment);
      return true;
    }
    return false;
  }

  /**
   * Applies prorated adjustments to {@code invoiceLine}. In case there is any, other lines might be affected as well
   *
   * @param invoiceLine {@link InvoiceLine} to apply pro-rated adjustments to
   * @param invoice associated {@link Invoice} record
   * @return list of other lines which are updated after applying prorated adjustment(s)
   */
  private CompletableFuture<List<InvoiceLine>> applyProratedAdjustments(InvoiceLine invoiceLine, Invoice invoice) {
    List<Adjustment> proratedAdjustments = getProratedAdjustments(invoice.getAdjustments());

    if (proratedAdjustments.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return getRelatedLines(invoiceLine).thenApply(lines -> {
      // Create new list adding current line as well
      List<InvoiceLine> allLines = new ArrayList<>(lines);
      allLines.add(invoiceLine);

      // Re-apply prorated adjustments and return only those related lines which were updated after re-applying prorated adjustment(s)
      return applyProratedAdjustments(proratedAdjustments, allLines, invoice).stream()
        .filter(line -> !line.equals(invoiceLine))
        .collect(toList());
    });
  }

  /**
   * Gets all other invoice lines associated with the same invoice. Passed {@code invoiceLine} is not added to the result.
   *
   * @param invoiceLine {@link InvoiceLine} record
   * @return list of all other invoice lines associated with the same invoice
   */
  private CompletableFuture<List<InvoiceLine>> getRelatedLines(InvoiceLine invoiceLine) {
    String cql = String.format(QUERY_BY_INVOICE_ID, invoiceLine.getInvoiceId());
    if (invoiceLine.getId() != null) {
      cql = combineCqlExpressions("and", cql, "id<>" + invoiceLine.getId());
    }
    String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, Integer.MAX_VALUE, 0, getEndpointWithQuery(cql, logger), lang);

    return getInvoiceLineCollection(endpoint).thenApply(InvoiceLineCollection::getInvoiceLines);
  }

  private void updateInvoiceAndAffectedLinesAsync(Invoice invoice, List<InvoiceLine> lines) {
    VertxCompletableFuture.runAsync(ctx, () -> {
      InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, ctx, lang);
      helper.persistInvoiceLines(invoice, lines).handle((ok, fail) -> {
        if (fail == null) {
          updateInvoiceAsync(invoice);
        }
        helper.closeHttpClient();
        return null;
      });
    });
  }

  private CompletableFuture<Void> persistInvoiceLines(Invoice invoice, List<InvoiceLine> lines) {
    return VertxCompletableFuture.allOf(ctx, lines.stream()
      .map(invoiceLine -> {
        calculateInvoiceLineTotals(invoiceLine, invoice);
        return this.updateInvoiceLineToStorage(invoiceLine);
      })
      .toArray(CompletableFuture[]::new));
  }

  private void updateInvoiceAsync(Invoice invoice) {
    VertxCompletableFuture.runAsync(ctx,
      () -> sendEvent(MessageAddress.INVOICE_TOTALS, new JsonObject().put(INVOICE, JsonObject.mapFrom(invoice))));
  }

  private void updateInvoiceAndLinesAsync(Invoice invoice) {
    VertxCompletableFuture.runAsync(ctx, () -> {
      InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, ctx, lang);
      helper.updateInvoiceAndLines(invoice)
        .handle((ok, fail) -> {
          helper.closeHttpClient();
          return null;
        });
    });
  }

  private CompletableFuture<Void> updateInvoiceAndLines(Invoice invoice) {

    List<Adjustment> proratedAdjustments = getProratedAdjustments(invoice.getAdjustments());

    // If no prorated adjustments, just update invoice details
    if (proratedAdjustments.isEmpty()) {
      updateInvoiceAsync(invoice);
      return CompletableFuture.completedFuture(null);
    }

    return getInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(lines -> applyProratedAdjustments(proratedAdjustments, lines, invoice))
      .thenCompose(lines -> persistInvoiceLines(invoice, lines))
      .thenAccept(ok -> updateInvoiceAsync(invoice));

  }
}
