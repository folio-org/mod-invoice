package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.ErrorCodes.CANNOT_DELETE_INVOICE_LINE;
import static org.folio.invoices.utils.ErrorCodes.FAILED_TO_UPDATE_INVOICE_AND_OTHER_LINES;
import static org.folio.invoices.utils.ErrorCodes.FAILED_TO_UPDATE_PONUMBERS;
import static org.folio.invoices.utils.ErrorCodes.ORDER_INVOICE_RELATION_CREATE_FAILED;
import static org.folio.invoices.utils.ErrorCodes.PROHIBITED_INVOICE_LINE_CREATION;
import static org.folio.invoices.utils.HelperUtils.QUERY_PARAM_START_WITH;
import static org.folio.invoices.utils.HelperUtils.calculateInvoiceLineTotals;
import static org.folio.invoices.utils.HelperUtils.combineCqlExpressions;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.HelperUtils.isPostApproval;
import static org.folio.invoices.utils.ProtectedOperationType.DELETE;
import static org.folio.invoices.utils.ProtectedOperationType.READ;
import static org.folio.invoices.utils.ProtectedOperationType.UPDATE;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICE_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.InvoiceRestrictionsUtil;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.InvoiceLineCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.finance.budget.BudgetExpenseClassService;
import org.folio.services.finance.transaction.EncumbranceService;
import org.folio.services.invoice.InvoiceLineService;
import org.folio.services.invoice.InvoiceService;
import org.folio.services.order.OrderLineService;
import org.folio.services.order.OrderService;
import org.folio.services.validator.InvoiceLineValidator;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;

public class InvoiceLineHelper extends AbstractHelper {
  public static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  public static final String GET_INVOICE_LINES_BY_QUERY = resourcesPath(INVOICE_LINES) + SEARCH_PARAMS;
  public static final String HYPHEN_SEPARATOR = "-";

  private final ProtectionHelper protectionHelper;
  private final AdjustmentsService adjustmentsService;
  private final InvoiceLineValidator validator;
  @Autowired
  private OrderService orderService;
  @Autowired
  private InvoiceService invoiceService;
  @Autowired
  private InvoiceLineService invoiceLineService;
  @Autowired
  private OrderLineService orderLineService;
  @Autowired
  private InvoiceWorkflowDataHolderBuilder holderBuilder;
  @Autowired
  private BudgetExpenseClassService budgetExpenseClassService;
  @Autowired
  private EncumbranceService encumbranceService;

  public InvoiceLineHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.protectionHelper = new ProtectionHelper(okapiHeaders, ctx);
    this.adjustmentsService = new AdjustmentsService();
    this.validator = new InvoiceLineValidator();
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  public Future<InvoiceLineCollection> getInvoiceLines(int limit, int offset, String query) {
    return protectionHelper.buildAcqUnitsCqlExprToSearchRecords(INVOICE_LINES)
      .compose(acqUnitsCqlExpr -> {
        String queryParam;
        if (isEmpty(query)) {
          queryParam = getEndpointWithQuery(acqUnitsCqlExpr);
        } else {
          queryParam = getEndpointWithQuery(combineCqlExpressions("and", acqUnitsCqlExpr, query));
        }
        String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, limit, offset, queryParam);
        return invoiceLineService.getInvoiceLines(endpoint, buildRequestContext());
      });
  }

  public Future<List<InvoiceLine>> getInvoiceLinesByInvoiceId(String invoiceId) {
    return invoiceLineService.getInvoiceLinesByInvoiceId(invoiceId, buildRequestContext())
      .map(InvoiceLineCollection::getInvoiceLines);
  }

  Future<InvoiceLineCollection> getInvoiceLineCollection(String endpoint, RequestContext requestContext) {
    return invoiceLineService.getInvoiceLines(endpoint, requestContext);
  }

  public Future<InvoiceLine> getInvoiceLine(String id, RequestContext requestContext) {
    return invoiceLineService.getInvoiceLine(id, requestContext);
  }

  private Future<Void> updateOutOfSyncInvoiceLine(InvoiceLine invoiceLine, Invoice invoice,
      boolean[] invoiceSerializationNeeded, RequestContext requestContext) {
    logger.info("Invoice line with id={} is out of date in storage and going to be updated", invoiceLine.getId());
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, ctx);
    return helper.updateInvoiceLineToStorage(invoiceLine, requestContext)
      .compose(v -> updateInvoice(invoice, invoiceSerializationNeeded, requestContext));
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
  public Future<InvoiceLine> getInvoiceLinePersistTotal(String id) {
    boolean[] invoiceSerializationNeeded = { false };
    RequestContext requestContext = buildRequestContext();
    return getInvoiceLine(id, requestContext)
      .compose(invoiceLineFromStorage ->
        getInvoiceAndCheckProtection(invoiceLineFromStorage, requestContext)
          .compose(invoice -> {
            boolean isTotalOutOfSync = reCalculateInvoiceLineTotals(invoiceLineFromStorage, invoice);
            if (!isTotalOutOfSync) {
              return succeededFuture(invoiceLineFromStorage);
            }
            return updateOutOfSyncInvoiceLine(invoiceLineFromStorage, invoice, invoiceSerializationNeeded, requestContext)
              .compose(v -> persistInvoiceIfNeeded(invoice, invoiceSerializationNeeded, requestContext))
              .map(v -> invoiceLineFromStorage);
          })
      )
      .onFailure(t -> logger.error("Failed to get an Invoice Line by id={}", id, t.getCause()));
  }

  public Future<Void> updateInvoiceLineToStorage(InvoiceLine invoiceLine, RequestContext requestContext) {
    return invoiceLineService.updateInvoiceLine(invoiceLine, requestContext);
  }

  public Future<Void> updateInvoiceLine(InvoiceLine invoiceLine, RequestContext requestContext) {
    boolean[] invoiceSerializationNeeded = { false };
    var invoiceLineFuture = getInvoiceLine(invoiceLine.getId(), requestContext);
    var invoiceFuture = invoiceLineFuture.compose(invLine -> invoiceService.getInvoiceById(invoiceLine.getInvoiceId(),
      requestContext));

    return invoiceFuture
      .compose(invoice -> getInvoiceWorkflowDataHolders(invoice, invoiceLine, invoiceSerializationNeeded, requestContext))
      .compose(holders -> budgetExpenseClassService.checkExpenseClasses(holders, requestContext))
      .map(holders -> {
        var invoice = invoiceFuture.result();
        // Validate if invoice line update is allowed
        validator.validateProtectedFields(invoice, invoiceLine, invoiceLineFuture.result());
        validator.validateLineAdjustmentsOnUpdate(invoiceLine, invoice);
        invoiceLine.setInvoiceLineNumber(invoiceLineFuture.result().getInvoiceLineNumber());
        unlinkEncumbranceFromChangedFunds(invoiceLine, invoiceLineFuture.result());
        return null;
      })
      .compose(v -> protectionHelper.isOperationRestricted(invoiceFuture.result().getAcqUnitIds(), UPDATE))
      .compose(ok -> applyAdjustmentsAndUpdateLine(invoiceLine, invoiceLineFuture.result(), invoiceFuture.result(),
        invoiceSerializationNeeded, requestContext))
      .compose(ok -> updateOrderInvoiceRelationship(invoiceLine, invoiceLineFuture.result(), requestContext))
      .compose(ok -> updateInvoicePoNumbers(invoiceFuture.result(), invoiceLine, invoiceLineFuture.result(),
        invoiceSerializationNeeded, requestContext))
      .compose(v -> persistInvoiceIfNeeded(invoiceFuture.result(), invoiceSerializationNeeded, requestContext));
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

  private Future<Void> updateOrderInvoiceRelationship(InvoiceLine invoiceLine, InvoiceLine invoiceLineFromStorage, RequestContext requestContext) {
    if (invoiceLine.getPoLineId() == null) {
      return deleteOrderInvoiceRelationshipIfNeeded(invoiceLine, invoiceLineFromStorage, requestContext);
    }
    if (!StringUtils.equals(invoiceLine.getPoLineId(), invoiceLineFromStorage.getPoLineId())) {
      return orderLineService.getPoLine(invoiceLine.getPoLineId(), requestContext).compose(
        poLine -> orderService.getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceLine.getInvoiceId(), requestContext)
          .compose(relationships -> {
            if (relationships.getTotalRecords() == 0) {

              OrderInvoiceRelationship orderInvoiceRelationship = new OrderInvoiceRelationship();
              orderInvoiceRelationship.withInvoiceId(invoiceLine.getInvoiceId()).withPurchaseOrderId(poLine.getPurchaseOrderId());

              return deleteOrderInvoiceRelationshipIfNeeded(invoiceLine, invoiceLineFromStorage, requestContext)
                .compose(v -> orderService.createOrderInvoiceRelationship(orderInvoiceRelationship, requestContext)
                  .compose(relationship -> succeededFuture(null))
                );
            }
            return succeededFuture(null);
          }));
    }

    //  Don't create/update the relationship in case ids match
    return succeededFuture(null);
  }

  private Future<Void> deleteOrderInvoiceRelationshipIfNeeded(InvoiceLine invoiceLine,
      InvoiceLine invoiceLineFromStorage, RequestContext requestContext) {
    // if the stored invoice line does not have a link, there is no relationship to delete
    if (invoiceLineFromStorage.getPoLineId() == null) {
      return succeededFuture(null);
    }
    return orderService.deleteOrderInvoiceRelationshipByInvoiceIdAndLineId(invoiceLine.getInvoiceId(),
      invoiceLineFromStorage.getPoLineId(), requestContext);
  }

  private Future<Void> applyAdjustmentsAndUpdateLine(InvoiceLine invoiceLine, InvoiceLine invoiceLineFromStorage,
      Invoice invoice, boolean[] invoiceSerializationNeeded, RequestContext requestContext) {
    // Just persist updates if invoice is already finalized
    if (isPostApproval(invoice)) {
      return updateInvoiceLineToStorage(invoiceLine, requestContext);
    }

    // Re-apply prorated adjustments if available
    return applyProratedAdjustments(invoiceLine, invoice, requestContext).compose(affectedLines -> {
      // Recalculate totals before update which also indicates if invoice requires update
      calculateInvoiceLineTotals(invoiceLine, invoice);
      // Update invoice line in storage
      return updateInvoiceLineToStorage(invoiceLine, requestContext).compose(v -> {
        // Trigger invoice update event only if this is required
        if (!affectedLines.isEmpty() || !areTotalsEqual(invoiceLine, invoiceLineFromStorage)) {
          return updateInvoiceAndAffectedLines(invoice, affectedLines, invoiceSerializationNeeded, requestContext);
        } else {
          return succeededFuture(null);
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
  public Future<Void> deleteInvoiceLine(String lineId) {
    boolean[] invoiceSerializationNeeded = { false };
    RequestContext requestContext = buildRequestContext();
    var invoiceFuture = getInvoicesIfExists(lineId, requestContext);
    var invoiceLineFuture = invoiceLineService.getInvoiceLine(lineId, requestContext);

    return CompositeFuture.join(invoiceFuture, invoiceLineFuture)
      .compose(cf -> protectionHelper.isOperationRestricted(invoiceFuture.result().getAcqUnitIds(), DELETE)
      .compose(v -> InvoiceRestrictionsUtil.checkIfInvoiceDeletionPermitted(invoiceFuture.result())))
      .compose(invoiceHold -> orderService.deleteOrderInvoiceRelationIfLastInvoice(lineId, requestContext)
        .compose(v -> invoiceLineService.deleteInvoiceLine(lineId, requestContext))
        .compose(v -> updateInvoiceAndLines(invoiceFuture.result(), invoiceSerializationNeeded, requestContext))
        .compose(v -> deleteInvoicePoNumbers(invoiceFuture.result(), invoiceLineFuture.result(),
          invoiceSerializationNeeded, requestContext))
        .compose(v -> persistInvoiceIfNeeded(invoiceFuture.result(), invoiceSerializationNeeded, requestContext))
      );
  }

  private Future<Invoice> getInvoicesIfExists(String lineId, RequestContext requestContext) {
    String query = QUERY_PARAM_START_WITH + lineId;
    return invoiceService.getInvoices(query, 0, Integer.MAX_VALUE, requestContext)
      .compose(invoiceCollection -> {
      if (!invoiceCollection.getInvoices().isEmpty()) {
        return succeededFuture(invoiceCollection.getInvoices().get(0));
      }
      List<Parameter> parameters = Collections.singletonList(new Parameter().withKey("invoiceLineId").withValue(lineId));
      Error error = CANNOT_DELETE_INVOICE_LINE.toError().withParameters(parameters);
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
  public Future<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine) {
    RequestContext requestContext = new RequestContext(ctx, okapiHeaders);
    boolean[] invoiceSerializationNeeded = { false };
    return invoiceService.getInvoiceById(invoiceLine.getInvoiceId(), requestContext)
      .map(invoice -> {
      validator.validateLineAdjustmentsOnCreate(invoiceLine, invoice);
      return invoice;
    })
      .map(this::checkIfInvoiceLineCreationAllowed)
      .compose(invoice -> getInvoiceWorkflowDataHolders(invoice, invoiceLine, invoiceSerializationNeeded, requestContext)
        .compose(holders -> budgetExpenseClassService.checkExpenseClasses(holders, requestContext))
        .compose(holders -> encumbranceService.updateEncumbranceLinksForFiscalYear(invoice, holders, requestContext))
        .map(holders -> invoice))
      .compose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), ProtectedOperationType.CREATE)
        .map(v -> invoice))
      .compose(invoice -> createInvoiceLine(invoiceLine, invoice, invoiceSerializationNeeded, requestContext)
        .compose(line -> orderService.createInvoiceOrderRelation(line, buildRequestContext())
          .recover(throwable -> {
            throw new HttpException(500, ORDER_INVOICE_RELATION_CREATE_FAILED.toError());
          })
          .compose(v -> updateInvoicePoNumbers(invoice, line, null, invoiceSerializationNeeded,
            requestContext))
          .compose(v -> persistInvoiceIfNeeded(invoice, invoiceSerializationNeeded, requestContext))
          .map(v -> line)));
  }

  private Invoice checkIfInvoiceLineCreationAllowed(Invoice invoice) {
    if (isPostApproval(invoice)) {
      throw new HttpException(500, PROHIBITED_INVOICE_LINE_CREATION);
    }
    return invoice;
  }

  private Future<Invoice> getInvoiceAndCheckProtection(InvoiceLine invoiceLineFromStorage, RequestContext requestContext) {
    return invoiceService.getInvoiceById(invoiceLineFromStorage.getInvoiceId(), requestContext)
      .compose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), READ)
        .map(aVoid -> invoice));
  }

  /**
   * Creates Invoice Line assuming its content is valid
   *
   * @param invoiceLine {@link InvoiceLine} to be created
   * @param invoice     associated {@link Invoice} object
   * @return completable future which might hold {@link InvoiceLine} on success or an exception if any issue happens
   */
  private Future<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine, Invoice invoice,
      boolean[] invoiceSerializationNeeded, RequestContext requestContext) {
    return invoiceLineService.generateLineNumber(invoice.getId(), requestContext)
      .onSuccess(invoiceLine::setInvoiceLineNumber)
      // First the prorated adjustments should be applied. In case there is any, it might require to update other lines
      .compose(ok -> applyProratedAdjustments(invoiceLine, invoice, requestContext))
      .compose(affectedLines -> {
        calculateInvoiceLineTotals(invoiceLine, invoice);
        return invoiceLineService.createInvoiceLine(invoiceLine, requestContext)
          .compose(createdInvoiceLine -> updateInvoiceAndAffectedLines(invoice, affectedLines,
              invoiceSerializationNeeded, requestContext)
            .map(v -> createdInvoiceLine));
      });
  }





  /**
   * Applies prorated adjustments to {@code invoiceLine}. In case there is any, other lines might be affected as well
   *
   * @param invoiceLine {@link InvoiceLine} to apply pro-rated adjustments to
   * @param invoice     associated {@link Invoice} record
   * @return list of other lines which are updated after applying prorated adjustment(s)
   */
  private Future<List<InvoiceLine>> applyProratedAdjustments(InvoiceLine invoiceLine, Invoice invoice,
      RequestContext requestContext) {

    if (adjustmentsService.getProratedAdjustments(invoice)
      .isEmpty()) {
      return succeededFuture(Collections.emptyList());
    }
    invoiceLine.getAdjustments()
      .forEach(adjustment -> adjustment.setProrate(Adjustment.Prorate.NOT_PRORATED));

    return getRelatedLines(invoiceLine, requestContext).map(lines -> {
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
  private Future<List<InvoiceLine>> getRelatedLines(InvoiceLine invoiceLine, RequestContext requestContext) {
    String cql = String.format(QUERY_BY_INVOICE_ID, invoiceLine.getInvoiceId());
    if (invoiceLine.getId() != null) {
      cql = combineCqlExpressions("and", cql, "id<>" + invoiceLine.getId());
    }
    String endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, Integer.MAX_VALUE, 0, getEndpointWithQuery(cql));

    return getInvoiceLineCollection(endpoint, requestContext).map(InvoiceLineCollection::getInvoiceLines);
  }

  private Future<Void> updateInvoiceAndAffectedLines(Invoice invoice, List<InvoiceLine> lines,
      boolean[] invoiceSerializationNeeded, RequestContext requestContext) {
    return persistInvoiceLines(invoice, lines, requestContext)
      .compose(v -> updateInvoice(invoice, invoiceSerializationNeeded, requestContext))
      .recover(t -> {
        logger.error("Failed to update the invoice and other lines", t);
        throw new HttpException(500, FAILED_TO_UPDATE_INVOICE_AND_OTHER_LINES.toError());
      });
  }

  private Future<Void> persistInvoiceLines(Invoice invoice, List<InvoiceLine> lines, RequestContext requestContext) {
    var futures = lines.stream()
      .map(invoiceLine -> {
        calculateInvoiceLineTotals(invoiceLine, invoice);
        return this.updateInvoiceLineToStorage(invoiceLine, requestContext);
      })
      .collect(toList());

    return GenericCompositeFuture.join(futures).mapEmpty();
  }

  private Future<Void> updateInvoice(Invoice invoice, boolean[] invoiceSerializationNeeded,
      RequestContext requestContext) {
    return invoiceService.recalculateTotals(invoice, requestContext)
      .map(isOutOfSync -> {
        if (Boolean.TRUE.equals(isOutOfSync)) {
          logger.info("The invoice with id={} is out of sync in storage and requires updates", invoice.getId());
          invoiceSerializationNeeded[0] = true;
        } else {
          logger.info("The invoice with id={} is up to date in storage", invoice.getId());
        }
        return null;
      });
  }

  public Future<Void> updateInvoiceAndLines(Invoice invoice, boolean[] invoiceSerializationNeeded,
      RequestContext requestContext) {

    // If no prorated adjustments, just update invoice details
    if (adjustmentsService.getProratedAdjustments(invoice).isEmpty()) {
      return updateInvoice(invoice, invoiceSerializationNeeded, requestContext);
    }

    return getInvoiceLinesByInvoiceId(invoice.getId())
      .map(lines -> adjustmentsService.applyProratedAdjustments(lines, invoice))
      .compose(lines -> persistInvoiceLines(invoice, lines, requestContext))
      .compose(ok -> updateInvoice(invoice, invoiceSerializationNeeded, requestContext));

  }

  /**
   * Updates the invoice's poNumbers field, following an invoice line creation, update or removal.
   * @param invoice - the invoice of the modified invoice line
   * @param invoiceLine - the modified invoice line
   * @param invoiceLineFromStorage - the old version of the invoice line
   * @param requestContext - used to start new requests
   */
  private Future<Void> updateInvoicePoNumbers(Invoice invoice, InvoiceLine invoiceLine,
      InvoiceLine invoiceLineFromStorage, boolean[] invoiceSerializationNeeded, RequestContext requestContext) {

    if (!isPoNumbersUpdateNeeded(invoiceLineFromStorage, invoiceLine))
      return succeededFuture(null);
    String poLineId = (invoiceLineFromStorage == null || invoiceLine.getPoLineId() != null) ? invoiceLine.getPoLineId() :
      invoiceLineFromStorage.getPoLineId();
    return orderLineService.getPoLine(poLineId, requestContext)
      .compose(poLine -> orderService.getOrder(poLine.getPurchaseOrderId(), requestContext))
      .compose(order -> {
        if (invoiceLineFromStorage != null && invoiceLineFromStorage.getPoLineId() != null && invoiceLine.getPoLineId() == null) {
          return removeInvoicePoNumber(order.getPoNumber(), order, invoice, invoiceLine, invoiceSerializationNeeded,
            requestContext);
        } else {
          addInvoicePoNumber(order.getPoNumber(), invoice, invoiceSerializationNeeded);
          return succeededFuture();
        }
      })
      .recover(throwable -> {
        logger.error("Failed to update invoice poNumbers", throwable);
        throw new HttpException(500, FAILED_TO_UPDATE_PONUMBERS.toError());
      });
  }


  /**
   * Delete the invoice's poNumbers field, following an invoice line removal.
   * @param invoice - the invoice of the modified invoice line
   * @param invoiceLine - the modified invoice line
   * @param invoiceSerializationNeeded - boolean will be set to true if invoice needs to be saved
   * @param requestContext - used to start new requests
   */
  private Future<Void> deleteInvoicePoNumbers(Invoice invoice, InvoiceLine invoiceLine,
      boolean[] invoiceSerializationNeeded, RequestContext requestContext) {

    if (invoiceLine.getPoLineId() == null)
      return succeededFuture(null);
    return orderLineService.getPoLine(invoiceLine.getPoLineId(), requestContext)
      .compose(poLine -> orderService.getOrder(poLine.getPurchaseOrderId(), requestContext))
      .compose(order -> removeInvoicePoNumber(order.getPoNumber(), order, invoice, invoiceLine,
        invoiceSerializationNeeded, requestContext))
      .recover(throwable -> {
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
  private Future<Void> removeInvoicePoNumber(String orderPoNumber, CompositePurchaseOrder order,
      Invoice invoice, InvoiceLine invoiceLine, boolean[] invoiceSerializationNeeded, RequestContext requestContext) {

    List<String> invoicePoNumbers = invoice.getPoNumbers();
    if (!invoicePoNumbers.contains(orderPoNumber))
      return succeededFuture(null);
    // check the other invoice lines to see if one of them is linking to the same order
    List<String> orderLineIds = order.getCompositePoLines().stream().map(CompositePoLine::getId).collect(toList());
    return getRelatedLines(invoiceLine, requestContext).compose(lines -> {
      if (lines.stream().noneMatch(line -> orderLineIds.contains(line.getPoLineId()))) {
        List<String> newNumbers = invoicePoNumbers.stream().filter(n -> !n.equals(orderPoNumber)).collect(toList());
        invoice.setPoNumbers(newNumbers);
        invoiceSerializationNeeded[0] = true;
      }
      return succeededFuture(null);
    });
  }

  /**
   * Adds orderPoNumber to the invoice's poNumbers field if needed.
   */
  private void addInvoicePoNumber(String orderPoNumber, Invoice invoice, boolean[] invoiceSerializationNeeded) {
    List<String> invoicePoNumbers = invoice.getPoNumbers();
    if (invoicePoNumbers.contains(orderPoNumber))
      return;
    invoice.setPoNumbers(addPoNumberToList(invoicePoNumbers, orderPoNumber));
    invoiceSerializationNeeded[0] = true;
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

  private Future<List<InvoiceWorkflowDataHolder>> getInvoiceWorkflowDataHolders(Invoice invoice, InvoiceLine invoiceLine,
      boolean[] invoiceSerializationNeeded, RequestContext requestContext) {
    List<InvoiceLine> lines = new ArrayList<>();
    lines.add(invoiceLine);

    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withFunds(dataHolders, requestContext)
      .compose(holders -> holderBuilder.withLedgers(holders, requestContext))
      .compose(holders -> holderBuilder.withBudgets(holders, requestContext))
      .map(holderBuilder::checkMultipleFiscalYears)
      .compose(holders -> holderBuilder.withFiscalYear(holders, requestContext))
      .map(holders -> updateInvoiceFiscalYear(holders, invoiceSerializationNeeded))
      .compose(holders -> holderBuilder.withEncumbrances(holders, requestContext))
      .compose(holders -> holderBuilder.withExpenseClasses(holders, requestContext))
      .compose(holders -> holderBuilder.withExchangeRate(holders, requestContext));
  }

  private List<InvoiceWorkflowDataHolder> updateInvoiceFiscalYear(List<InvoiceWorkflowDataHolder> holders,
      boolean[] invoiceSerializationNeeded) {
    if (holders.isEmpty())
      return holders;
    Invoice invoice = holders.get(0).getInvoice();
    if (invoice.getFiscalYearId() != null)
      return holders;
    logger.info("Invoice fiscal year updated based on the invoice line, invoiceLineId={}",
      holders.get(0).getInvoiceLine().getId());
    invoice.setFiscalYearId(holders.get(0).getFiscalYear().getId());
    invoiceSerializationNeeded[0] = true;
    return holders;
  }

  private Future<Void> persistInvoiceIfNeeded(Invoice invoice, boolean[] invoiceSerializationNeeded,
      RequestContext requestContext) {
    if (invoiceSerializationNeeded[0]) {
      return invoiceService.updateInvoice(invoice, requestContext);
    } else {
      return succeededFuture(null);
    }
  }
}
