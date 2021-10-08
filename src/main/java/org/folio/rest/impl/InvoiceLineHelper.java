package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.InvoiceRestrictionsUtil;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.models.InvoiceHolder;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.order.OrderService;
import org.folio.services.order.OrderLineService;
import org.folio.services.validator.InvoiceLineValidator;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_INVOICE_LINE;
import static org.folio.invoices.utils.ErrorCodes.FAILED_TO_UPDATE_INVOICE_AND_OTHER_LINES;
import static org.folio.invoices.utils.ErrorCodes.FAILED_TO_UPDATE_PONUMBERS;
import static org.folio.invoices.utils.ErrorCodes.ORDER_INVOICE_RELATION_CREATE_FAILED;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_INVOICE_LINE_CREATION;
import static org.folio.invoices.utils.HelperUtils.INVOICE_ID;
import static org.folio.invoices.utils.HelperUtils.QUERY_PARAM_START_WITH;
import static org.folio.invoices.utils.HelperUtils.calculateInvoiceLineTotals;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.getHttpClient;
import static org.folio.invoices.utils.HelperUtils.getInvoiceById;
import static org.folio.invoices.utils.HelperUtils.getInvoices;
import static org.folio.invoices.utils.HelperUtils.handleDeleteRequest;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;
import static org.folio.invoices.utils.HelperUtils.handlePutRequest;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.ProtectedOperationType.DELETE;
import static org.folio.invoices.utils.ProtectedOperationType.READ;
import static org.folio.invoices.utils.ProtectedOperationType.UPDATE;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.services.voucher.VoucherRetrieveService.QUERY_BY_INVOICE_ID;

public class InvoiceLineHelper extends AbstractHelper {

  private static final String INVOICE_LINE_NUMBER_ENDPOINT = resourcesPath(INVOICE_LINE_NUMBER) + "?" + INVOICE_ID + "=";
  public static final String GET_INVOICE_LINES_BY_QUERY = resourcesPath(INVOICE_LINES) + SEARCH_PARAMS;
  public static final String HYPHEN_SEPARATOR = "-";

  private final ProtectionHelper protectionHelper;
  private final AdjustmentsService adjustmentsService;
  private final InvoiceLineValidator validator;
  private final RestClient restClient;

  @Autowired
  private OrderService orderService;
  @Autowired
  private InvoiceService invoiceService;
  @Autowired
  private InvoiceLineService invoiceLineService;
  @Autowired
  private OrderLineService orderLineService;

  public InvoiceLineHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this(getHttpClient(okapiHeaders), okapiHeaders, ctx, lang);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  public InvoiceLineHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(httpClient, okapiHeaders, ctx, lang);
    this.protectionHelper = new ProtectionHelper(httpClient, okapiHeaders, ctx, lang);
    this.adjustmentsService = new AdjustmentsService();
    this.validator = new InvoiceLineValidator();
    this.restClient = new RestClient();
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
      .thenCompose(json -> FolioVertxCompletableFuture.supplyBlockingAsync(ctx, () -> json.mapTo(InvoiceLineCollection.class)));
  }

  public CompletableFuture<InvoiceLine> getInvoiceLine(String id) {
    CompletableFuture<InvoiceLine> future = new FolioVertxCompletableFuture<>(ctx);

    try {
      handleGetRequest(resourceByIdPath(INVOICE_LINES, id, lang), httpClient, ctx, okapiHeaders, logger)
        .thenAccept(jsonInvoiceLine -> {
          logger.info("Successfully retrieved invoice line: {}", jsonInvoiceLine.encodePrettily());
          future.complete(jsonInvoiceLine.mapTo(InvoiceLine.class));
        })
        .exceptionally(t -> {
          logger.error("Error getting invoice line by id {}", id);
          future.completeExceptionally(t);
          return null;
        });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  private CompletableFuture<Void> updateOutOfSyncInvoiceLine(InvoiceLine invoiceLine, Invoice invoice) {
    logger.info("Invoice line with id={} is out of date in storage and going to be updated", invoiceLine.getId());
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, ctx, lang);
    return helper.updateInvoiceLineToStorage(invoiceLine)
      .thenCompose(v -> updateInvoice(invoice, buildRequestContext()));
  }

  /**
   * Calculate invoice line total and compare with original value if it has changed
   *
   * @param invoiceLine invoice line to update totals for
   * @param invoice     invoice record
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
    return !(Objects.equals(existingTotal, invoiceLine.getTotal()) && Objects.equals(subTotal, invoiceLine.getSubTotal())
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
    CompletableFuture<InvoiceLine> future = new FolioVertxCompletableFuture<>(ctx);

    // GET invoice-line from storage
    getInvoiceLine(id)
      .thenCompose(invoiceLineFromStorage ->
        getInvoiceAndCheckProtection(invoiceLineFromStorage)
          .thenCompose(invoice -> {
            boolean isTotalOutOfSync = reCalculateInvoiceLineTotals(invoiceLineFromStorage, invoice);
            if (!isTotalOutOfSync) {
              return CompletableFuture.completedFuture(invoiceLineFromStorage);
            }
            return updateOutOfSyncInvoiceLine(invoiceLineFromStorage, invoice)
              .thenApply(v -> invoiceLineFromStorage);
          })
      )
      .thenAccept(future::complete)
      .exceptionally(t -> {
        logger.error("Failed to get an Invoice Line by id={}", id, t.getCause());
        future.completeExceptionally(t);
        return null;
      });
    return future;
  }

  public CompletableFuture<Void> updateInvoiceLineToStorage(InvoiceLine invoiceLine) {
    return handlePutRequest(resourceByIdPath(INVOICE_LINES, invoiceLine.getId(), lang), JsonObject.mapFrom(invoiceLine), httpClient,
        ctx, okapiHeaders, logger);
  }

  public CompletableFuture<Void> updateInvoiceLine(InvoiceLine invoiceLine, RequestContext requestContext) {

    return getInvoiceLine(invoiceLine.getId())
      .thenCompose(invoiceLineFromStorage -> getInvoice(invoiceLineFromStorage).thenCompose(invoice -> {
        // Validate if invoice line update is allowed
        validator.validateProtectedFields(invoice, invoiceLine, invoiceLineFromStorage);
        validator.validateLineAdjustmentsOnUpdate(invoiceLine, invoice);
        invoiceLine.setInvoiceLineNumber(invoiceLineFromStorage.getInvoiceLineNumber());
        unlinkEncumbranceFromChangedFunds(invoiceLine, invoiceLineFromStorage);

        return protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), UPDATE)
          .thenCompose(ok -> applyAdjustmentsAndUpdateLine(invoiceLine, invoiceLineFromStorage, invoice))
          .thenCompose(ok -> updateOrderInvoiceRelationship(invoiceLine, invoiceLineFromStorage, requestContext))
          .thenCompose(ok -> updateInvoicePoNumbers(invoice, invoiceLine, invoiceLineFromStorage, requestContext));
      }));
  }

  private void unlinkEncumbranceFromChangedFunds(InvoiceLine invoiceLine, InvoiceLine invoiceLineFromStorage) {
    invoiceLine.getFundDistributions().stream()
      .filter(distribution -> invoiceLineFromStorage.getFundDistributions().stream()
        .filter(fdStorage -> fdStorage.getEncumbrance() != null)
        .anyMatch(distributionFromStorage ->
          distributionFromStorage.getEncumbrance().equals(distribution.getEncumbrance())
          && !distributionFromStorage.getFundId().equals(distribution.getFundId())))
      .forEach(distribution -> distribution.setEncumbrance(null));
  }

  private CompletableFuture<Void> updateOrderInvoiceRelationship(InvoiceLine invoiceLine, InvoiceLine invoiceLineFromStorage, RequestContext requestContext) {
    if (invoiceLine.getPoLineId() == null) {
      return deleteOrderInvoiceRelationshipIfNeeded(invoiceLine, invoiceLineFromStorage, requestContext);
    }
    if (!StringUtils.equals(invoiceLine.getPoLineId(), invoiceLineFromStorage.getPoLineId())) {
      return orderLineService.getPoLine(invoiceLine.getPoLineId(), requestContext).thenCompose(
        poLine -> orderService.getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceLine.getInvoiceId(), requestContext)
          .thenCompose(relationships -> {
            if (relationships.getTotalRecords() == 0) {

              OrderInvoiceRelationship orderInvoiceRelationship = new OrderInvoiceRelationship();
              orderInvoiceRelationship.withInvoiceId(invoiceLine.getInvoiceId()).withPurchaseOrderId(poLine.getPurchaseOrderId());

              return deleteOrderInvoiceRelationshipIfNeeded(invoiceLine, invoiceLineFromStorage, requestContext)
                .thenCompose(v -> orderService.createOrderInvoiceRelationship(orderInvoiceRelationship, requestContext)
                  .thenCompose(relationship -> CompletableFuture.completedFuture(null))
                );
            }
            return CompletableFuture.completedFuture(null);
          }));
    }

    //  Don't create/update the relationship in case ids match
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> deleteOrderInvoiceRelationshipIfNeeded(InvoiceLine invoiceLine,
      InvoiceLine invoiceLineFromStorage, RequestContext requestContext) {
    // if the stored invoice line does not have a link, there is no relationship to delete
    if (invoiceLineFromStorage.getPoLineId() == null) {
      return CompletableFuture.completedFuture(null);
    }
    return orderService.deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceLine.getInvoiceId(),
      invoiceLineFromStorage.getPoLineId(), requestContext);
  }

  private CompletableFuture<Void> applyAdjustmentsAndUpdateLine(InvoiceLine invoiceLine, InvoiceLine invoiceLineFromStorage,
      Invoice invoice) {
    // Just persist updates if invoice is already finalized
    if (isPostApproval(invoice)) {
      return updateInvoiceLineToStorage(invoiceLine);
    }

    // Re-apply prorated adjustments if available
    return applyProratedAdjustments(invoiceLine, invoice).thenCompose(affectedLines -> {
      // Recalculate totals before update which also indicates if invoice requires update
      calculateInvoiceLineTotals(invoiceLine, invoice);
      // Update invoice line in storage
      return updateInvoiceLineToStorage(invoiceLine).thenCompose(v -> {
        // Trigger invoice update event only if this is required
        if (!affectedLines.isEmpty() || !areTotalsEqual(invoiceLine, invoiceLineFromStorage)) {
          return updateInvoiceAndAffectedLines(invoice, affectedLines);
        } else {
          return CompletableFuture.completedFuture(null);
        }
      });
    });
  }

  /**
   * Deletes Invoice Line and update Invoice if deletion is allowed 1. Get invoice via searching for invoices by invoiceLine.id
   * field 2. Verify if user has permission to delete invoiceLine based on acquisitions units, if not then return 3. If user has
   * permission to delete then delete invoiceLine 4. Update corresponding Invoice
   *
   * @param lineId invoiceLine id to be deleted
   */
  public CompletableFuture<Void> deleteInvoiceLine(String lineId) {
    InvoiceHolder invoiceHolder = new InvoiceHolder();
    return getInvoicesIfExists(lineId)
      .thenApply(invoiceHolder::setInvoice)
      .thenCompose(invHolder -> protectionHelper.isOperationRestricted(invHolder.getInvoice().getAcqUnitIds(), DELETE)
        .thenApply(vVoid -> invHolder.getInvoice()))
      .thenCompose(InvoiceRestrictionsUtil::checkIfInvoiceDeletionPermitted)
      .thenCompose(v -> invoiceLineService.getInvoiceLine(lineId, buildRequestContext())
        .thenApply(invoiceHolder::setInvoiceLine))
      .thenCompose(invoiceHold -> orderService.deleteOrderInvoiceRelationIfLastInvoice(lineId, buildRequestContext())
        .exceptionally(throwable -> {
          logger.error("Can't delete Order Invoice relation for lineId: {}", lineId, throwable);
          List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("lineId")
            .withValue(lineId));
          Error error = CANNOT_DELETE_INVOICE_LINE.toError()
            .withParameters(parameters);
          throw new HttpException(404, error);
        })
        .thenCompose(v -> handleDeleteRequest(resourceByIdPath(INVOICE_LINES, lineId, lang), httpClient, ctx, okapiHeaders, logger))
        .thenCompose(v -> updateInvoiceAndLines(invoiceHold.getInvoice(), buildRequestContext()))
        .thenCompose(invoiceLine -> deleteInvoicePoNumbers(invoiceHold.getInvoice(), invoiceHolder.getInvoiceLine(), buildRequestContext())));
  }

  private CompletableFuture<Invoice> getInvoicesIfExists(String lineId) {
    String query = QUERY_PARAM_START_WITH + lineId;
    return getInvoices(query, httpClient, ctx, okapiHeaders, logger, lang).thenCompose(invoiceCollection -> {
      if (!invoiceCollection.getInvoices()
        .isEmpty()) {
        return CompletableFuture.completedFuture(invoiceCollection.getInvoices()
          .get(0));
      }
      List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceLineId")
        .withValue(lineId));
      Error error = CANNOT_DELETE_INVOICE_LINE.toError()
        .withParameters(parameters);
      throw new HttpException(404, error);
    });
  }

  /**
   * Creates Invoice Line if its content is valid
   *
   * @param invoiceLine {@link InvoiceLine} to be created
   * @return completable future which might hold {@link InvoiceLine} on success, {@code null} if validation fails or an exception if
   *         any issue happens
   */
  public CompletableFuture<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine) {
    return getInvoice(invoiceLine).thenApply(invoice -> {
      validator.validateLineAdjustmentsOnCreate(invoiceLine, invoice);
      return invoice;
    })
      .thenApply(this::checkIfInvoiceLineCreationAllowed)
      .thenCompose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.CREATE)
        .thenApply(v -> invoice))
      .thenCompose(invoice -> createInvoiceLine(invoiceLine, invoice)
        .thenCompose(line -> orderService.createInvoiceOrderRelation(line, buildRequestContext())
          .exceptionally(throwable -> {
            throw new HttpException(500, ORDER_INVOICE_RELATION_CREATE_FAILED.toError());
          })
          .thenCompose(v -> updateInvoicePoNumbers(invoice, line, null, buildRequestContext()))
          .thenApply(v -> line)));
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
   *
   * @param invoiceLine {@link InvoiceLine} to be created
   * @param invoice     associated {@link Invoice} object
   * @return completable future which might hold {@link InvoiceLine} on success or an exception if any issue happens
   */
  private CompletableFuture<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine, Invoice invoice) {
    return generateLineNumber(invoice).thenAccept(invoiceLine::setInvoiceLineNumber)
      // First the prorated adjustments should be applied. In case there is any, it might require to update other lines
      .thenCompose(ok -> applyProratedAdjustments(invoiceLine, invoice).thenCompose(affectedLines -> {
        calculateInvoiceLineTotals(invoiceLine, invoice);
        RequestEntry requestEntry = new RequestEntry(resourcesPath(INVOICE_LINES));
        return restClient.post(requestEntry, invoiceLine, buildRequestContext(), InvoiceLine.class)
          .thenCompose(createdInvoiceLine -> updateInvoiceAndAffectedLines(invoice, affectedLines)
            .thenApply(v -> invoiceLine.withId(createdInvoiceLine.getId())));
      }));
  }

  private CompletableFuture<String> generateLineNumber(Invoice invoice) {
    return handleGetRequest(getInvoiceLineNumberEndpoint(invoice.getId()), httpClient, ctx, okapiHeaders, logger)
      .thenApply(sequenceNumberJson -> sequenceNumberJson.mapTo(SequenceNumber.class)
        .getSequenceNumber());
  }

  private String getInvoiceLineNumberEndpoint(String id) {
    return INVOICE_LINE_NUMBER_ENDPOINT + id;
  }

  /**
   * Applies prorated adjustments to {@code invoiceLine}. In case there is any, other lines might be affected as well
   *
   * @param invoiceLine {@link InvoiceLine} to apply pro-rated adjustments to
   * @param invoice     associated {@link Invoice} record
   * @return list of other lines which are updated after applying prorated adjustment(s)
   */
  private CompletableFuture<List<InvoiceLine>> applyProratedAdjustments(InvoiceLine invoiceLine, Invoice invoice) {

    if (adjustmentsService.getProratedAdjustments(invoice)
      .isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }
    invoiceLine.getAdjustments()
      .forEach(adjustment -> adjustment.setProrate(Adjustment.Prorate.NOT_PRORATED));

    return getRelatedLines(invoiceLine).thenApply(lines -> {
      // Create new list adding current line as well
      List<InvoiceLine> allLines = new ArrayList<>(lines);
      allLines.add(invoiceLine);

      // Re-apply prorated adjustments and return only those related lines which were updated after re-applying prorated
      // adjustment(s)
      return adjustmentsService.applyProratedAdjustments(allLines, invoice)
        .stream()
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

  private CompletableFuture<Void> updateInvoiceAndAffectedLines(Invoice invoice, List<InvoiceLine> lines) {
    return persistInvoiceLines(invoice, lines)
      .thenCompose(v -> updateInvoice(invoice, buildRequestContext()))
      .exceptionally(t -> {
        logger.error("Failed to update the invoice and other lines", t);
        throw new HttpException(500, FAILED_TO_UPDATE_INVOICE_AND_OTHER_LINES.toError());
      });
  }

  private CompletableFuture<Void> persistInvoiceLines(Invoice invoice, List<InvoiceLine> lines) {
    return FolioVertxCompletableFuture.allOf(ctx, lines.stream()
      .map(invoiceLine -> {
        calculateInvoiceLineTotals(invoiceLine, invoice);
        return this.updateInvoiceLineToStorage(invoiceLine);
      })
      .toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<Void> updateInvoice(Invoice invoice, RequestContext requestContext) {
    return invoiceService.recalculateTotals(invoice, requestContext)
      .thenCompose(isOutOfSync -> {
        if (Boolean.TRUE.equals(isOutOfSync)) {
          logger.info("The invoice with id={} is out of sync in storage and requires updates", invoice.getId());
          InvoiceHelper helper = new InvoiceHelper(okapiHeaders, ctx, lang);
          return helper.updateInvoiceRecord(invoice);
        } else {
          logger.info("The invoice with id={} is up to date in storage", invoice.getId());
          return completedFuture(null);
        }
      });
  }

  public CompletableFuture<Void> updateInvoiceAndLines(Invoice invoice, RequestContext requestContext) {

    // If no prorated adjustments, just update invoice details
    if (adjustmentsService.getProratedAdjustments(invoice).isEmpty()) {
      return updateInvoice(invoice, requestContext);
    }

    return getInvoiceLinesByInvoiceId(invoice.getId())
      .thenApply(lines -> adjustmentsService.applyProratedAdjustments(lines, invoice))
      .thenCompose(lines -> persistInvoiceLines(invoice, lines))
      .thenCompose(ok -> updateInvoice(invoice, requestContext));

  }

  /**
   * Updates the invoice's poNumbers field, following an invoice line creation, update or removal.
   * @param invoice - the invoice of the modified invoice line
   * @param invoiceLine - the modified invoice line
   * @param invoiceLineFromStorage - the old version of the invoice line
   * @param requestContext - used to start new requests
   */
  private CompletableFuture<Void> updateInvoicePoNumbers(Invoice invoice, InvoiceLine invoiceLine,
      InvoiceLine invoiceLineFromStorage, RequestContext requestContext) {

    if (!isPoNumbersUpdateNeeded(invoiceLineFromStorage, invoiceLine))
      return CompletableFuture.completedFuture(null);
    String poLineId = (invoiceLineFromStorage == null || invoiceLine.getPoLineId() != null) ? invoiceLine.getPoLineId() :
      invoiceLineFromStorage.getPoLineId();
    return orderLineService.getPoLine(poLineId, requestContext)
      .thenCompose(poLine -> orderService.getOrder(poLine.getPurchaseOrderId(), requestContext))
      .thenCompose(order -> {
        if (invoiceLineFromStorage != null && invoiceLineFromStorage.getPoLineId() != null && invoiceLine.getPoLineId() == null) {
          return removeInvoicePoNumber(order.getPoNumber(), order, invoice, invoiceLine, requestContext);
        }
        return addInvoicePoNumber(order.getPoNumber(), invoice, requestContext);
      })
      .exceptionally(throwable -> {
        logger.error("Failed to update invoice poNumbers", throwable);
        throw new HttpException(500, FAILED_TO_UPDATE_PONUMBERS.toError());
      });
  }


  /**
   * Delete the invoice's poNumbers field, following an invoice line removal.
   * @param invoice - the invoice of the modified invoice line
   * @param invoiceLine - the modified invoice line
   * @param requestContext - used to start new requests
   */
  private CompletableFuture<Void> deleteInvoicePoNumbers(Invoice invoice, InvoiceLine invoiceLine, RequestContext requestContext) {

    if (invoiceLine.getPoLineId() == null)
      return CompletableFuture.completedFuture(null);
    return orderLineService.getPoLine(invoiceLine.getPoLineId(), requestContext)
      .thenCompose(poLine -> orderService.getOrder(poLine.getPurchaseOrderId(), requestContext))
      .thenCompose(order -> removeInvoicePoNumber(order.getPoNumber(), order, invoice, invoiceLine, requestContext))
      .exceptionally(throwable -> {
        logger.error("Failed to update invoice poNumbers", throwable);
        throw new HttpException(500, FAILED_TO_UPDATE_PONUMBERS.toError());
      });
  }

  /**
   * @return false if we can tell right away that an update of the invoice poNumbers is not needed.
   */
  private boolean isPoNumbersUpdateNeeded(InvoiceLine invoiceLineFromStorage, InvoiceLine invoiceLine) {
    return !((invoiceLineFromStorage == null && invoiceLine.getPoLineId() == null) ||
      (invoiceLine.getPoLineId() == null && invoiceLineFromStorage != null && invoiceLineFromStorage.getPoLineId() == null));
  }

  /**
   * Removes orderPoNumber from the invoice's poNumbers field if needed.
   */
  private CompletableFuture<Void> removeInvoicePoNumber(String orderPoNumber, CompositePurchaseOrder order,
      Invoice invoice, InvoiceLine invoiceLine, RequestContext requestContext) {

    List<String> invoicePoNumbers = invoice.getPoNumbers();
    if (!invoicePoNumbers.contains(orderPoNumber))
      return CompletableFuture.completedFuture(null);
    // check the other invoice lines to see if one of them is linking to the same order
    List<String> orderLineIds = order.getCompositePoLines().stream().map(CompositePoLine::getId).collect(toList());
    return getRelatedLines(invoiceLine).thenCompose(lines -> {
      if (lines.stream().anyMatch(line -> orderLineIds.contains(line.getPoLineId()))) {
        return CompletableFuture.completedFuture(null);
      } else {
        List<String> newNumbers = invoicePoNumbers.stream().filter(n -> !n.equals(orderPoNumber)).collect(toList());
        return invoiceService.updateInvoice(invoice.withPoNumbers(newNumbers), requestContext);
      }
    });
  }

  /**
   * Adds orderPoNumber to the invoice's poNumbers field if needed.
   */
  private CompletableFuture<Void> addInvoicePoNumber(String orderPoNumber, Invoice invoice, RequestContext requestContext) {
    List<String> invoicePoNumbers = invoice.getPoNumbers();
    if (invoicePoNumbers.contains(orderPoNumber))
      return CompletableFuture.completedFuture(null);
    return invoiceService.updateInvoice(invoice.withPoNumbers(addPoNumberToList(invoicePoNumbers, orderPoNumber)),
      requestContext);
  }

  private List<String> addPoNumberToList(List<String> numbers, String newNumber) {
    if (newNumber == null)
      return numbers;
    if (numbers == null)
      return List.of(newNumber);
    if (numbers.contains(newNumber))
      return numbers;
    var newNumbers = new ArrayList<>(numbers);
    newNumbers.add(newNumber);
    return newNumbers;
  }
}
