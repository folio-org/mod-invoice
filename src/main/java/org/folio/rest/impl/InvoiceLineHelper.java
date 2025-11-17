package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.invoices.utils.AcqDesiredPermissions.BYPASS_ACQ_UNITS;
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
import static org.folio.utils.UserPermissionsUtil.userHasDesiredPermission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.InvoiceRestrictionsUtil;
import org.folio.invoices.utils.ProtectedOperationType;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.acq.model.orders.CompositePurchaseOrder;
import org.folio.rest.acq.model.orders.OrderInvoiceRelationship;
import org.folio.rest.acq.model.orders.PoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Adjustment;
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
    if (userHasDesiredPermission(BYPASS_ACQ_UNITS, okapiHeaders)) {
      String endpoint;
      if (isEmpty(query)) {
        endpoint = resourcesPath(INVOICE_LINES);
      } else {
        endpoint = String.format(GET_INVOICE_LINES_BY_QUERY, limit, offset, getEndpointWithQuery(query));
      }
      return invoiceLineService.getInvoiceLines(endpoint, buildRequestContext());
    }
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

  public Future<Void> getInvoiceLine(ILProcessing ilProcessing, String id, RequestContext requestContext) {
    return invoiceLineService.getInvoiceLine(id, requestContext)
      .map(invoiceLine -> {
        ilProcessing.setInvoiceLineFromStorage(invoiceLine);
        return null;
      });
  }

  private Future<Void> updateOutOfSyncInvoiceLine(ILProcessing ilProcessing, RequestContext requestContext) {
    InvoiceLine invoiceLineFromStorage = ilProcessing.getInvoiceLineFromStorage();
    logger.info("Invoice line with id={} is out of date in storage and going to be updated",
      invoiceLineFromStorage.getId());
    InvoiceLineHelper helper = new InvoiceLineHelper(okapiHeaders, ctx);
    return helper.updateInvoiceLineToStorage(invoiceLineFromStorage, requestContext)
      .compose(v -> updateInvoice(ilProcessing, requestContext));
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
    ILProcessing ilProcessing = new ILProcessing();
    RequestContext requestContext = buildRequestContext();

    return getInvoiceLine(ilProcessing, id, requestContext)
      .compose(v -> getInvoiceAndCheckProtection(ilProcessing, requestContext))
      .compose(v -> {
        boolean isTotalOutOfSync = reCalculateInvoiceLineTotals(ilProcessing.getInvoiceLineFromStorage(),
          ilProcessing.getInvoice());
        if (!isTotalOutOfSync) {
          return succeededFuture(ilProcessing.getInvoiceLineFromStorage());
        }
        return updateOutOfSyncInvoiceLine(ilProcessing, requestContext)
          .compose(v2 -> persistInvoiceIfNeeded(ilProcessing, requestContext))
          .map(v2 -> ilProcessing.getInvoiceLineFromStorage());
      })
      .onFailure(t -> logger.error("Failed to get an Invoice Line by id={}", id, t.getCause()));
  }

  public Future<Void> updateInvoiceLineToStorage(InvoiceLine invoiceLine, RequestContext requestContext) {
    return invoiceLineService.updateInvoiceLine(invoiceLine, requestContext);
  }

  public Future<Void> updateInvoiceLine(InvoiceLine invoiceLine, boolean skipPoNumbers, RequestContext requestContext) {
    ILProcessing ilProcessing = new ILProcessing();
    ilProcessing.setInvoiceLine(invoiceLine);
    ilProcessing.setPoNumber(skipPoNumbers);
    return getInvoiceLine(ilProcessing, invoiceLine.getId(), requestContext)
      .compose(v -> invoiceService.getInvoiceById(ilProcessing.getInvoiceLineFromStorage().getInvoiceId(),
        requestContext))
      .map(invoice -> {
        ilProcessing.setInvoice(invoice);
        return null;
      })
      .compose(invoice -> holderBuilder.buildCompleteHolders(ilProcessing.getInvoice(),
        Collections.singletonList(ilProcessing.getInvoiceLine()), requestContext))
      .compose(holders -> budgetExpenseClassService.checkExpenseClasses(holders, requestContext))
      .map(holders -> updateInvoiceFiscalYear(holders, ilProcessing))
      .map(holders -> {
        var invoice = ilProcessing.getInvoice();
        // Validate if invoice line update is allowed
        validator.validateProtectedFields(invoice, ilProcessing.getInvoiceLine(), ilProcessing.getInvoiceLineFromStorage());
        validator.validateLineAdjustmentsOnUpdate(invoiceLine, invoice);
        invoiceLine.setInvoiceLineNumber(ilProcessing.getInvoiceLineFromStorage().getInvoiceLineNumber());
        unlinkEncumbranceFromChangedFunds(invoiceLine, ilProcessing.getInvoiceLineFromStorage());
        return null;
      })
      .compose(v -> protectionHelper.isOperationRestricted(ilProcessing.getInvoice().getAcqUnitIds(), UPDATE))
      .compose(ok -> applyAdjustmentsAndUpdateLine(ilProcessing, requestContext))
      .compose(ok -> updateOrderInvoiceRelationship(invoiceLine, ilProcessing.getInvoiceLineFromStorage(), requestContext))
      .compose(ok -> updateInvoicePoNumbers(ilProcessing, requestContext))
      .compose(v -> persistInvoiceIfNeeded(ilProcessing, requestContext));
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
      return orderLineService.getPoLineById(invoiceLine.getPoLineId(), requestContext).compose(
        poLine -> orderService.getOrderInvoiceRelationshipByOrderIdAndInvoiceId(poLine.getPurchaseOrderId(), invoiceLine.getInvoiceId(), requestContext)
          .compose(relationships -> {
            if (relationships.getTotalRecords() == 0) {
              OrderInvoiceRelationship orderInvoiceRelationship = new OrderInvoiceRelationship();
              orderInvoiceRelationship.withInvoiceId(invoiceLine.getInvoiceId()).withPurchaseOrderId(poLine.getPurchaseOrderId());
              logger.info("updateOrderInvoiceRelationship:: Recreating order-invoice relationship was updated for invoice id={}, line id={}",
                invoiceLine.getInvoiceId(), invoiceLine.getId());
              return deleteOrderInvoiceRelationshipIfNeeded(invoiceLine, invoiceLineFromStorage, requestContext)
                .compose(v -> orderService.createOrderInvoiceRelationship(orderInvoiceRelationship, requestContext)
                  .compose(relationship -> succeededFuture(null))
                );
            }
            return succeededFuture(null);
          }));
    }
    logger.info("updateOrderInvoiceRelationship:: No order-invoice relationship was updated for invoice id={}, line id={}",
      invoiceLine.getInvoiceId(), invoiceLine.getId());
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

  private Future<Void> applyAdjustmentsAndUpdateLine(ILProcessing ilProcessing, RequestContext requestContext) {
    Invoice invoice = ilProcessing.getInvoice();
    InvoiceLine invoiceLine = ilProcessing.getInvoiceLine();
    InvoiceLine invoiceLineFromStorage = ilProcessing.getInvoiceLineFromStorage();

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
          return updateInvoiceAndAffectedLines(ilProcessing, affectedLines, requestContext);
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
    ILProcessing ilProcessing = new ILProcessing();
    RequestContext requestContext = buildRequestContext();

    return getInvoiceIfExists(ilProcessing, lineId, requestContext)
      .compose(v -> getInvoiceLine(ilProcessing, lineId, requestContext))
      .compose(cf -> protectionHelper.isOperationRestricted(ilProcessing.getInvoice().getAcqUnitIds(), DELETE)
      .compose(v -> InvoiceRestrictionsUtil.checkIfInvoiceDeletionPermitted(ilProcessing.getInvoice())))
      .compose(invoiceHold -> orderService.deleteOrderInvoiceRelationIfLastInvoice(lineId, requestContext))
      .compose(v -> invoiceLineService.deleteInvoiceLine(lineId, requestContext))
      .compose(v -> updateInvoiceAndLines(ilProcessing, requestContext))
      .compose(v -> deleteInvoicePoNumbers(ilProcessing, requestContext))
      .compose(v -> persistInvoiceIfNeeded(ilProcessing, requestContext));
  }

  private Future<Void> getInvoiceIfExists(ILProcessing ilProcessing, String lineId, RequestContext requestContext) {
    String query = QUERY_PARAM_START_WITH + lineId;
    return invoiceService.getInvoices(query, 0, Integer.MAX_VALUE, requestContext)
      .compose(invoiceCollection -> {
        if (!invoiceCollection.getInvoices().isEmpty()) {
          ilProcessing.setInvoice(invoiceCollection.getInvoices().getFirst());
          return succeededFuture(null);
        }
        var param = new Parameter().withKey("invoiceLineId").withValue(lineId);
        logger.error("getInvoiceIfExists:: Cannot delete invoice line: {}", lineId);
        throw new HttpException(404, CANNOT_DELETE_INVOICE_LINE, List.of(param));
      });
  }

  /**
   * Creates Invoice Line if its content is valid
   *
   * @param invoiceLine {@link InvoiceLine} to be created
   * @return completable future which {@link InvoiceLine} on success
   */
  public Future<InvoiceLine> createInvoiceLine(InvoiceLine invoiceLine) {
    RequestContext requestContext = new RequestContext(ctx, okapiHeaders);
    ILProcessing ilProcessing = new ILProcessing();
    ilProcessing.setInvoiceLine(invoiceLine);

    return invoiceService.getInvoiceById(invoiceLine.getInvoiceId(), requestContext)
      .map(invoice -> {
        validator.validateLineAdjustmentsOnCreate(invoiceLine, invoice);
        checkIfInvoiceLineCreationAllowed(invoice);
        ilProcessing.setInvoice(invoice);
        return null;
      })
      .compose(v -> protectionHelper.isOperationRestricted(ilProcessing.getInvoice().getAcqUnitIds(),
        ProtectedOperationType.CREATE))
      .compose(invoice -> holderBuilder.buildCompleteHolders(ilProcessing.getInvoice(),
          Collections.singletonList(ilProcessing.getInvoiceLine()), requestContext)
        .compose(holders -> budgetExpenseClassService.checkExpenseClasses(holders, requestContext))
        .compose(holders -> generateNewInvoiceLineNumber(holders, ilProcessing, requestContext))
        .map(holders -> updateInvoiceFiscalYear(holders, ilProcessing))
        .compose(holders -> encumbranceService.updateEncumbranceLinksForFiscalYear(ilProcessing.getInvoice(), holders,
          requestContext))
        .mapEmpty())
      .compose(v -> createInvoiceLine(ilProcessing, requestContext))
      .compose(v -> orderService.createInvoiceOrderRelation(ilProcessing.getInvoiceLine(), buildRequestContext())
        .recover(throwable -> {
          var param = new Parameter().withKey("invoiceLineId").withValue(invoiceLine.getId());
          var causeParam = new Parameter().withKey("cause").withValue(throwable.getMessage());
          logger.error("Failed to create invoice line '{}' order relation", invoiceLine.getId(), throwable);
          throw new HttpException(500, ORDER_INVOICE_RELATION_CREATE_FAILED, List.of(param, causeParam));
        }))
      .compose(v -> updateInvoicePoNumbers(ilProcessing, requestContext))
      .compose(v -> persistInvoiceIfNeeded(ilProcessing, requestContext))
      .map(v -> ilProcessing.getInvoiceLine());
  }

  private void checkIfInvoiceLineCreationAllowed(Invoice invoice) {
    if (isPostApproval(invoice)) {
      var param1 = new Parameter().withKey("invoiceId").withValue(invoice.getId());
      var param2 = new Parameter().withKey("invoiceStatus").withValue(invoice.getStatus().toString());
      logger.error("checkIfInvoiceLineCreationAllowed:: Prohibited invoice line '{}' creation: invoiceStatus={}", invoice.getId(), invoice.getStatus().toString());
      throw new HttpException(500, PROHIBITED_INVOICE_LINE_CREATION, List.of(param1, param2));
    }
  }

  private Future<Void> getInvoiceAndCheckProtection(ILProcessing ilProcessing, RequestContext requestContext) {
    InvoiceLine invoiceLineFromStorage = ilProcessing.getInvoiceLineFromStorage();
    return invoiceService.getInvoiceById(invoiceLineFromStorage.getInvoiceId(), requestContext)
      .compose(invoice -> protectionHelper.isOperationRestricted(invoice.getAcqUnitIds(), READ)
        .map(aVoid -> {
          ilProcessing.setInvoice(invoice);
          return null;
        }));
  }

  private Future<List<InvoiceWorkflowDataHolder>> generateNewInvoiceLineNumber(List<InvoiceWorkflowDataHolder> holders,
                                                                               ILProcessing ilProcessing, RequestContext requestContext) {
    String invoiceId = ilProcessing.getInvoice().getId();
    return invoiceLineService.generateLineNumber(invoiceId, requestContext)
      .map(number -> {
        ilProcessing.getInvoiceLine().setInvoiceLineNumber(number);
        return null;
      })
      // the invoice was changed when the new line number was generated, we need to get the new version
      .compose(v -> invoiceService.getInvoiceById(invoiceId, requestContext))
      .map(newInvoice -> {
        ilProcessing.setInvoice(newInvoice);
        return holders;
      });
  }

  /**
   * Creates Invoice Line assuming its content is valid
   */
  private Future<Void> createInvoiceLine(ILProcessing ilProcessing, RequestContext requestContext) {
    InvoiceLine invoiceLine = ilProcessing.getInvoiceLine();
    // First the prorated adjustments should be applied. In case there is any, it might require to update other lines
    return applyProratedAdjustments(invoiceLine, ilProcessing.getInvoice(), requestContext)
      .compose(affectedLines -> {
        calculateInvoiceLineTotals(invoiceLine, ilProcessing.getInvoice());
        return invoiceLineService.createInvoiceLine(invoiceLine, requestContext)
          .map(createdInvoiceLine -> {
            ilProcessing.setInvoiceLine(createdInvoiceLine);
            return null;
          })
          .compose(v2 -> updateInvoiceAndAffectedLines(ilProcessing, affectedLines, requestContext));
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

    // Exclude adjustment recalculation if no prorated ones are found or if no pending (id is null) Invoice Line level adjustments are found
    if (adjustmentsService.getProratedAdjustments(invoice).isEmpty()
      && adjustmentsService.getPendingInvoiceLineAdjustments(invoiceLine).isEmpty()) {
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

  private Future<Void> updateInvoiceAndAffectedLines(ILProcessing ilProcessing, List<InvoiceLine> lines,
                                                     RequestContext requestContext) {

    Invoice invoice = ilProcessing.getInvoice();
    return persistInvoiceLines(invoice, lines, requestContext)
      .compose(v -> updateInvoice(ilProcessing, requestContext))
      .recover(t -> {
        var param = new Parameter().withKey("invoiceId").withValue(invoice.getId());
        var causeParam = new Parameter().withKey("cause").withValue(t.getMessage());
        logger.error("Failed to update the invoice '{}' and other lines", invoice.getId(), t);
        throw new HttpException(500, FAILED_TO_UPDATE_INVOICE_AND_OTHER_LINES, List.of(param, causeParam));
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

  private Future<Void> updateInvoice(ILProcessing ilProcessing, RequestContext requestContext) {
    Invoice invoice = ilProcessing.getInvoice();
    return invoiceService.recalculateTotals(invoice, requestContext)
      .map(isOutOfSync -> {
        if (Boolean.TRUE.equals(isOutOfSync)) {
          logger.info("The invoice with id={} is out of sync in storage and requires updates", invoice.getId());
          ilProcessing.setInvoiceSerializationNeeded();
        } else {
          logger.info("The invoice with id={} is up to date in storage", invoice.getId());
        }
        return null;
      });
  }

  private Future<Void> updateInvoiceAndLines(ILProcessing ilProcessing, RequestContext requestContext) {
    Invoice invoice = ilProcessing.getInvoice();
    // If no prorated adjustments, just update invoice details
    if (adjustmentsService.getProratedAdjustments(invoice).isEmpty()) {
      return updateInvoice(ilProcessing, requestContext);
    }

    return getInvoiceLinesByInvoiceId(invoice.getId())
      .map(lines -> adjustmentsService.applyProratedAdjustments(lines, invoice))
      .compose(lines -> persistInvoiceLines(invoice, lines, requestContext))
      .compose(ok -> updateInvoice(ilProcessing, requestContext));
  }

  /**
   * Updates the invoice's poNumbers field, following an invoice line creation, update or removal.
   */
  private Future<Void> updateInvoicePoNumbers(ILProcessing ilProcessing, RequestContext requestContext) {
    InvoiceLine invoiceLine = ilProcessing.getInvoiceLine();
    InvoiceLine invoiceLineFromStorage = ilProcessing.getInvoiceLineFromStorage();
    if (!isPoNumbersUpdateNeeded(invoiceLineFromStorage, invoiceLine) || Boolean.TRUE.equals(ilProcessing.getSkipPoNumbers())) {
      logger.info("updateInvoicePoNumbers:: No invoice PO numbers were updated for id={}, line id={}",
        invoiceLine.getInvoiceId(), invoiceLine.getId());
      return succeededFuture(null);
    }
    String poLineId = (invoiceLineFromStorage == null || invoiceLine.getPoLineId() != null) ? invoiceLine.getPoLineId() :
      invoiceLineFromStorage.getPoLineId();
    return orderLineService.getPoLineById(poLineId, requestContext)
      .compose(poLine -> orderService.getOrder(poLine.getPurchaseOrderId(), requestContext))
      .compose(order -> {
        logger.info("updateInvoicePoNumbers:: Updating invoice PO numbers for id={}, line id={}",
          invoiceLine.getInvoiceId(), invoiceLine.getId());
        if (invoiceLineFromStorage != null && invoiceLineFromStorage.getPoLineId() != null && invoiceLine.getPoLineId() == null) {
          return removeInvoicePoNumber(order.getPoNumber(), order, ilProcessing, requestContext);
        } else {
          addInvoicePoNumber(order.getPoNumber(), ilProcessing);
          return succeededFuture();
        }
      })
      .recover(throwable -> {
        var param = new Parameter().withKey("poLineId").withValue(poLineId);
        var causeParam = new Parameter().withKey("cause").withValue(throwable.getMessage());
        logger.error("Failed to update invoice poNumbers. poLineId={}", poLineId, throwable);
        throw new HttpException(500, FAILED_TO_UPDATE_PONUMBERS, List.of(param, causeParam));
      });
  }

  /**
   * Delete the invoice's poNumbers field, following an invoice line removal.
   *
   * @param requestContext - used to start new requests
   */
  private Future<Void> deleteInvoicePoNumbers(ILProcessing ilProcessing, RequestContext requestContext) {
    InvoiceLine invoiceLine = ilProcessing.getInvoiceLineFromStorage();
    if (invoiceLine.getPoLineId() == null)
      return succeededFuture(null);
    return orderLineService.getPoLineById(invoiceLine.getPoLineId(), requestContext)
      .compose(poLine -> orderService.getOrder(poLine.getPurchaseOrderId(), requestContext))
      .compose(order -> removeInvoicePoNumber(order.getPoNumber(), order, ilProcessing, requestContext))
      .recover(throwable -> {
        var param = new Parameter().withKey("invoiceLine.getPoLineId").withValue(invoiceLine.getPoLineId());
        var causeParam = new Parameter().withKey("cause").withValue(throwable.getMessage());
        logger.error("Failed to update invoice poNumbers for poLineId={} of invoiceLine", invoiceLine.getPoLineId(), throwable);
        throw new HttpException(500, FAILED_TO_UPDATE_PONUMBERS, List.of(param, causeParam));
      });
  }

  /**
   * @return false if we can tell right away that an update of the invoice poNumbers is not needed.
   */
  private boolean isPoNumbersUpdateNeeded(InvoiceLine invoiceLineFromStorage, InvoiceLine invoiceLine) {
    return !((invoiceLineFromStorage == null && invoiceLine.getPoLineId() == null)
      || (invoiceLine.getPoLineId() == null && invoiceLineFromStorage != null && invoiceLineFromStorage.getPoLineId() == null));
  }

  /**
   * Removes orderPoNumber from the invoice's poNumbers field if needed.
   */
  private Future<Void> removeInvoicePoNumber(String orderPoNumber, CompositePurchaseOrder order,
                                             ILProcessing ilProcessing, RequestContext requestContext) {

    Invoice invoice = ilProcessing.getInvoice();
    InvoiceLine invoiceLine = ilProcessing.getInvoiceLineFromStorage();
    List<String> invoicePoNumbers = invoice.getPoNumbers();
    if (!invoicePoNumbers.contains(orderPoNumber)) {
      return succeededFuture(null);
    }
    // check the other invoice lines to see if one of them is linking to the same order
    List<String> orderLineIds = order.getPoLines().stream()
      .map(PoLine::getId)
      .toList();
    return getRelatedLines(invoiceLine, requestContext).compose(lines -> {
      if (lines.stream().noneMatch(line -> orderLineIds.contains(line.getPoLineId()))) {
        List<String> newNumbers = invoicePoNumbers.stream().filter(n -> !n.equals(orderPoNumber)).collect(toList());
        invoice.setPoNumbers(newNumbers);
        ilProcessing.setInvoiceSerializationNeeded();
      }
      return succeededFuture(null);
    });
  }

  /**
   * Adds orderPoNumber to the invoice's poNumbers field if needed.
   */
  private void addInvoicePoNumber(String orderPoNumber, ILProcessing ilProcessing) {
    Invoice invoice = ilProcessing.getInvoice();
    List<String> invoicePoNumbers = invoice.getPoNumbers();
    if (invoicePoNumbers.contains(orderPoNumber))
      return;
    invoice.setPoNumbers(addPoNumberToList(invoicePoNumbers, orderPoNumber));
    ilProcessing.setInvoiceSerializationNeeded();
  }

  private List<String> addPoNumberToList(List<String> numbers, String newNumber) {
    if (newNumber == null) {
      return numbers;
    }
    if (numbers == null) {
      return List.of(newNumber);
    }
    if (numbers.contains(newNumber)) {
      return numbers;
    }
    var newNumbers = new ArrayList<>(numbers);
    newNumbers.add(newNumber);
    return newNumbers;
  }

  private List<InvoiceWorkflowDataHolder> updateInvoiceFiscalYear(List<InvoiceWorkflowDataHolder> holders,
                                                                  ILProcessing ilProcessing) {
    if (holders.isEmpty())
      return holders;
    Invoice invoice = ilProcessing.getInvoice();
    if (invoice.getFiscalYearId() != null)
      return holders;
    logger.info("Invoice fiscal year updated based on the invoice line, invoiceLineId={}",
      holders.getFirst().getInvoiceLine().getId());
    invoice.setFiscalYearId(holders.getFirst().getFiscalYear().getId());
    ilProcessing.setInvoiceSerializationNeeded();
    return holders;
  }

  private Future<Void> persistInvoiceIfNeeded(ILProcessing ilProcessing, RequestContext requestContext) {
    if (ilProcessing.getInvoiceSerializationNeeded()) {
      return invoiceService.updateInvoice(ilProcessing.getInvoice(), requestContext);
    } else {
      return succeededFuture(null);
    }
  }

  private static class ILProcessing {
    private Invoice invoice;
    private InvoiceLine invoiceLine;
    private InvoiceLine invoiceLineFromStorage;
    private boolean invoiceSerializationNeeded = false;
    private boolean skipPoNumbers = false;

    Invoice getInvoice() {
      return invoice;
    }

    InvoiceLine getInvoiceLine() {
      return invoiceLine;
    }

    InvoiceLine getInvoiceLineFromStorage() {
      return invoiceLineFromStorage;
    }

    boolean getInvoiceSerializationNeeded() {
      return invoiceSerializationNeeded;
    }

    Boolean getSkipPoNumbers() {
      return skipPoNumbers;
    }

    void setInvoice(Invoice invoice) {
      this.invoice = invoice;
    }

    void setInvoiceLine(InvoiceLine invoiceLine) {
      this.invoiceLine = invoiceLine;
    }

    void setInvoiceLineFromStorage(InvoiceLine invoiceLineFromStorage) {
      this.invoiceLineFromStorage = invoiceLineFromStorage;
    }

    void setPoNumber(boolean skipPoNumbers) {
      this.skipPoNumbers = skipPoNumbers;
    }

    void setInvoiceSerializationNeeded() {
      this.invoiceSerializationNeeded = true;
    }
  }
}
