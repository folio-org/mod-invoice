package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.HelperUtils.*;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.acq.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.javamoney.moneta.function.MonetaryOperators;

public class InvoiceLineHelper extends AbstractHelper {

  private static final String INVOICE_ID = "invoiceId";
  private static final String INVOICE_LINE_NUMBER_ENDPOINT = resourcesPath(INVOICE_LINE_NUMBER) + "?" + INVOICE_ID + "=";
  private static final String GET_INVOICE_LINES_BY_QUERY = resourcesPath(INVOICE_LINES) + "?limit=%s&offset=%s%s&lang=%s";
  private static final String INVOICE_LINE_BYID_ENDPOINT = resourceByIdPath(INVOICE_LINES, "%s") + "?lang=%s";

  InvoiceLineHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
  }

  InvoiceLineHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
  }

  public CompletableFuture<InvoiceLineCollection> getInvoiceLines(int limit, int offset, String query) {
    CompletableFuture<InvoiceLineCollection> future = new VertxCompletableFuture<>(ctx);
    try {
      String queryParam = isEmpty(query) ? EMPTY : "&query=" + encodeQuery(query, logger);
      String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, limit, offset, queryParam, lang);
      handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger)
        .thenCompose(jsonInvoiceLines -> VertxCompletableFuture.supplyBlockingAsync(ctx, () -> {
            if (logger.isInfoEnabled()) {
              logger.info("Successfully retrieved invoice lines: {}", jsonInvoiceLines.encodePrettily());
            }
            return jsonInvoiceLines.mapTo(InvoiceLineCollection.class);
          })
        )
        .thenAccept(future::complete)
        .exceptionally(t -> {
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  public CompletableFuture<InvoiceLine> getInvoiceLine(String id) {
    CompletableFuture<InvoiceLine> future = new VertxCompletableFuture<>(ctx);

    try {
      handleGetRequest(String.format(INVOICE_LINE_BYID_ENDPOINT, id, lang), httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonInvoiceLine -> {
          logger.info("Successfully retrieved invoice line: " + jsonInvoiceLine.encodePrettily());
          future.complete(jsonInvoiceLine.mapTo(InvoiceLine.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting invoice line", t);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  public CompletableFuture<Void> updateInvoiceLine(InvoiceLine invoiceLine) {
    return handlePutRequest(resourceByIdPath(INVOICE_LINES, invoiceLine.getId()), JsonObject.mapFrom(invoiceLine),
          httpClient, ctx, okapiHeaders, logger);
  }

  /**
   * Creates Invoice Line if its content is valid
   * @param invoiceLine {@link InvoiceLine} to be created
   * @return completable future which might hold {@link InvoiceLine} on success, {@code null} if validation fails or an exception if any issue happens
   */
  CompletableFuture<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine) {
    return getInvoice(invoiceLine)
      .thenCompose(invoice -> createInvoiceLine(invoiceLine, invoice));
  }

  private CompletableFuture<Invoice> getInvoice(InvoiceLine invoiceLine) {
    return getInvoiceById(invoiceLine.getInvoiceId(), lang, httpClient, ctx, okapiHeaders, logger)
      .thenApply(HelperUtils::convertToInvoice)
      .exceptionally(t -> {
        Throwable cause = t.getCause();
        throw t instanceof CompletionException ? (CompletionException) t : new CompletionException(cause);
      });
  }

  /**
   * Creates Invoice Line assuming its content is valid
   * @param invoiceLine {@link InvoiceLine} to be created
   * @param invoice associated {@link Invoice} object
   * @return completable future which might hold {@link InvoiceLine} on success or an exception if any issue happens
   */
  CompletableFuture<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine, Invoice invoice) {
    invoiceLine.setId(UUID.randomUUID()
      .toString());
    invoiceLine.setInvoiceId(invoice.getId());

    JsonObject line = mapFrom(invoiceLine);

    return generateLineNumber(invoice).thenAccept(lineNumber -> line.put(INVOICE_LINE_NUMBER, lineNumber))
      .thenAccept(t -> calculateInvoiceLineTotals(invoiceLine, invoice))
      .thenCompose(v -> createInvoiceLineSummary(invoiceLine, line));
  }

  public CompletableFuture<InvoiceLine> calculateInvoiceLineTotals(InvoiceLine invoiceLine) {
    return getInvoice(invoiceLine).thenApply(invoice -> calculateInvoiceLineTotals(invoiceLine, invoice));
  }

  private InvoiceLine calculateInvoiceLineTotals(InvoiceLine invoiceLine, Invoice invoice) {
    String currency = invoice.getCurrency();
    CurrencyUnit currencyUnit = Monetary.getCurrency(currency);
    MonetaryAmount subTotal = Money.of(invoiceLine.getSubTotal(), currencyUnit);

    MonetaryAmount adjustmentTotals = calculateAdjustmentsTotal(invoiceLine, subTotal);
    MonetaryAmount total = adjustmentTotals.add(subTotal).with(MonetaryOperators.rounding());
    invoiceLine.setAdjustmentsTotal(adjustmentTotals.getNumber()
      .doubleValue());
    invoiceLine.setTotal(total.getNumber()
      .doubleValue());

    return invoiceLine;
  }


  private MonetaryAmount calculateAdjustmentsTotal(InvoiceLine invoiceLine, MonetaryAmount subTotal) {
    return invoiceLine.getAdjustments()
      .stream()
      .filter(adj -> adj.getRelationToTotal().equals(Adjustment.RelationToTotal.IN_ADDITION_TO))
      .map(adj -> calculateAdjustment(adj, subTotal))
      .collect(MonetaryFunctions.summarizingMonetary(subTotal.getCurrency()))
      .getSum()
      .with(MonetaryOperators.rounding());
  }


  private MonetaryAmount calculateAdjustment(Adjustment adjustment, MonetaryAmount subTotal) {
    if (adjustment.getType()
      .equals(Adjustment.Type.PERCENTAGE)) {
      return subTotal.with(MonetaryOperators.percent(adjustment.getValue()));
    }
    return Money.of(adjustment.getValue(), subTotal.getCurrency());
  }

  private CompletableFuture<String> generateLineNumber(Invoice invoice) {
    return handleGetRequest(getInvoiceLineNumberEndpoint(invoice.getId()), httpClient, ctx, okapiHeaders, logger)
      .thenApply(sequenceNumberJson -> {
        SequenceNumber sequenceNumber = sequenceNumberJson.mapTo(SequenceNumber.class);
        return buildInvoiceLineNumber(invoice.getFolioInvoiceNo(), sequenceNumber.getSequenceNumber());
      });
  }

  private CompletionStage<InvoiceLine> createInvoiceLineSummary(InvoiceLine invoiceLine, JsonObject line) {
    return createRecordInStorage(line, resourcesPath(INVOICE_LINES))
      // After generating line number, set id and line number of the created Invoice Line
      .thenApply(id -> invoiceLine.withId(id)
        .withInvoiceLineNumber(line.getString(INVOICE_LINE_NUMBER)));
  }

  private String buildInvoiceLineNumber(String folioInvoiceNumber, String sequence) {
    return folioInvoiceNumber + "-" + sequence;
  }

  private String getInvoiceLineNumberEndpoint(String id) {
    return INVOICE_LINE_NUMBER_ENDPOINT + id;
  }

  public CompletableFuture<Void> deleteInvoiceLine(String id) {
    return handleDeleteRequest(String.format(INVOICE_LINE_BYID_ENDPOINT, id, lang), httpClient, ctx, okapiHeaders, logger);
  }
}
