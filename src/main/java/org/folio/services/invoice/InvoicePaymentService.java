package org.folio.services.invoice;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.rest.acq.model.orders.CompositePoLine.PaymentStatus.ONGOING;
import static org.folio.rest.acq.model.orders.CompositePoLine.PaymentStatus.PAYMENT_NOT_REQUIRED;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.order.OrderLineService;
import org.folio.services.voucher.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.annotations.VisibleForTesting;

import io.vertx.core.Future;

public class InvoicePaymentService {

  @Autowired
  private InvoiceWorkflowDataHolderBuilder holderBuilder;
  @Autowired
  private PaymentCreditWorkflowService paymentCreditWorkflowService;
  @Autowired
  private VoucherService voucherService;
  @Autowired
  private OrderLineService orderLineService;

  protected static final Set<CompositePoLine.PaymentStatus> PO_LINE_PAYMENT_IGNORED_STATUSES =
    EnumSet.of(ONGOING, PAYMENT_NOT_REQUIRED);

  /**
   * Handles transition of given invoice to PAID status.
   *
   * @param invoice Invoice to be paid
   * @return CompletableFuture that indicates when transition is completed
   */

  public Future<Void> payInvoice(Invoice invoice, List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    //  Set payment date, when the invoice is being paid.
    invoice.setPaymentDate(invoice.getMetadata().getUpdatedDate());
    return holderBuilder.buildCompleteHolders(invoice, invoiceLines, requestContext)
      .compose(holders -> paymentCreditWorkflowService.handlePaymentsAndCreditsCreation(holders, requestContext))
      .compose(v -> Future.join(payPoLines(invoiceLines, requestContext), voucherService.payInvoiceVoucher(invoice.getId(), requestContext)))
      .mapEmpty();
  }

  /**
   * Updates payment status of the associated PO Lines.
   *
   * @param invoiceLines the invoice lines to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private Future<Void> payPoLines(List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    Map<String, List<InvoiceLine>> poLineIdInvoiceLinesMap = groupInvoiceLinesByPoLineId(invoiceLines);
    return fetchPoLines(poLineIdInvoiceLinesMap, requestContext)
      .map(this::updatePoLinesPaymentStatus)
      .compose(poLines -> orderLineService.updateCompositePoLines(poLines, requestContext));
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
  private Future<Map<CompositePoLine, CompositePoLine.PaymentStatus>> fetchPoLines(Map<String, List<InvoiceLine>> poLineIdsWithInvoiceLines,
                                                                                              RequestContext requestContext) {
    List<Future<CompositePoLine>> futures = poLineIdsWithInvoiceLines.keySet()
      .stream()
      .map(poLine -> orderLineService.getPoLine(poLine, requestContext))
      .collect(toList());

    return collectResultsOnSuccess(futures)
      .map(compositePoLines ->  compositePoLines.stream()
        .collect(Collectors.toMap(poLine -> poLine, poLine -> getPoLinePaymentStatus(poLineIdsWithInvoiceLines.get(poLine.getId())))));
  }

  private List<CompositePoLine> updatePoLinesPaymentStatus(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithNewStatuses) {
    return compositePoLinesWithNewStatuses
      .keySet().stream()
      .filter(compositePoLine -> isPaymentStatusUpdateRequired(compositePoLinesWithNewStatuses, compositePoLine))
      .map(compositePoLine -> updatePaymentStatus(compositePoLinesWithNewStatuses, compositePoLine))
      .collect(toList());
  }

  private CompositePoLine updatePaymentStatus(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus, CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus = compositePoLinesWithStatus.get(compositePoLine);
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

  @VisibleForTesting
  boolean isPaymentStatusUpdateRequired(Map<CompositePoLine, CompositePoLine.PaymentStatus> compositePoLinesWithStatus, CompositePoLine compositePoLine) {
    CompositePoLine.PaymentStatus newPaymentStatus = compositePoLinesWithStatus.get(compositePoLine);
    return (!newPaymentStatus.equals(compositePoLine.getPaymentStatus()) &&
      !PO_LINE_PAYMENT_IGNORED_STATUSES.contains(compositePoLine.getPaymentStatus()));
  }
}
