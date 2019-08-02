package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_INVOICE_LINE_CREATION;
import static org.folio.invoices.utils.HelperUtils.*;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.invoices.utils.InvoiceLineProtectedFields;

import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

public class InvoiceLineHelper extends AbstractHelper {

  private static final String INVOICE_ID = "invoiceId";
  private static final String INVOICE_LINE_NUMBER_ENDPOINT = resourcesPath(INVOICE_LINE_NUMBER) + "?" + INVOICE_ID + "=";
  private static final String GET_INVOICE_LINES_BY_QUERY = resourcesPath(INVOICE_LINES) + "?limit=%s&offset=%s%s&lang=%s";

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
      // 1. GET invoice-line from storage
      handleGetRequest(resourceByIdPath(INVOICE_LINES, id, lang), httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonInvoiceLine -> {
          logger.info("Successfully retrieved invoice line: " + jsonInvoiceLine.encodePrettily());

          // 2. Copy invoice-line from storage for future comparison
          InvoiceLine InvoiceLineFromStorage = jsonInvoiceLine.mapTo(InvoiceLine.class);
          InvoiceLine invoiceLineToRecalculateTotal = jsonInvoiceLine.mapTo(InvoiceLine.class);

          // 3. Calculate invoice-line totals, if different from storage and write it back to storage
          calculateInvoiceLineTotals(invoiceLineToRecalculateTotal).thenAccept(invoiceLineWithTotalRecalculated -> {
            Double recalculatedTotal = invoiceLineWithTotalRecalculated.getTotal();
            Double existingTotal = InvoiceLineFromStorage.getTotal();
            logger.info("updatedTotal-> " + recalculatedTotal + " existingTotal-> " + existingTotal);
            int retVal = Double.compare(recalculatedTotal, existingTotal);
            if (retVal != 0) {
              writeTotalToStorageIfDifferent(invoiceLineWithTotalRecalculated).thenAccept(updatedInvLine -> {
                future.complete(invoiceLineWithTotalRecalculated);
              });
            }
          });

          future.complete(InvoiceLineFromStorage);

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

  CompletableFuture<InvoiceLine> writeTotalToStorageIfDifferent(InvoiceLine invoiceLine) {
    JsonObject line = mapFrom(invoiceLine);
    return createRecordInStorage(line, resourcesPath(INVOICE_LINES)).thenApply(invLine -> line.mapTo(InvoiceLine.class));
  }
  
  public CompletableFuture<Void> updateInvoiceLine(InvoiceLine invoiceLine) {

    return getInvoiceLine(invoiceLine.getId())
      .thenCompose(existedInvoiceLine -> new InvoiceHelper(okapiHeaders, ctx, lang).getInvoiceRecord(existedInvoiceLine.getInvoiceId())
        .thenAccept(existedInvoice -> {
          validateInvoiceLine(existedInvoice, invoiceLine, existedInvoiceLine);
          invoiceLine.setInvoiceLineNumber(existedInvoiceLine.getInvoiceLineNumber());
          calculateInvoiceLineTotals(invoiceLine);
        })
      )
      .thenCompose(ok -> handlePutRequest(resourceByIdPath(INVOICE_LINES, invoiceLine.getId(), lang),
          JsonObject.mapFrom(invoiceLine), httpClient, ctx, okapiHeaders, logger));
  }

  private void validateInvoiceLine(Invoice existedInvoice, InvoiceLine invoiceLine, InvoiceLine existedInvoiceLine) {
    if(isFieldsVerificationNeeded(existedInvoice)) {
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
    return getInvoice(invoiceLine)
      .thenApply(this::checkIfInvoiceLineCreationAllowed)
      .thenCompose(invoice -> createInvoiceLine(invoiceLine, invoice));
  }

  private Invoice checkIfInvoiceLineCreationAllowed(Invoice invoice) {
    if (invoice.getStatus() != Invoice.Status.OPEN && invoice.getStatus() != Invoice.Status.REVIEWED) {
      throw new HttpException(500, PROHIBITED_INVOICE_LINE_CREATION);
    }
    return invoice;
  }

  private CompletableFuture<Invoice> getInvoice(InvoiceLine invoiceLine) {
    return getInvoiceById(invoiceLine.getInvoiceId(), lang, httpClient, ctx, okapiHeaders, logger);
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
      .thenAccept(t -> HelperUtils.calculateInvoiceLineTotals(invoiceLine, invoice))
      .thenCompose(v -> createInvoiceLineSummary(invoiceLine, line));
  }

  public CompletableFuture<InvoiceLine> calculateInvoiceLineTotals(InvoiceLine invoiceLine) {
    return getInvoice(invoiceLine).thenApply(invoice -> HelperUtils.calculateInvoiceLineTotals(invoiceLine, invoice));
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
    return handleDeleteRequest(resourceByIdPath(INVOICE_LINES, id, lang), httpClient, ctx, okapiHeaders, logger);
  }
}
