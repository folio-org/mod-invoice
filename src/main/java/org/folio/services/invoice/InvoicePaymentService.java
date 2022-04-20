package org.folio.services.invoice;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.InvoiceWorkflowDataHolderBuilder;
import org.folio.completablefuture.FolioVertxCompletableFuture;
import org.folio.models.InvoiceWorkflowDataHolder;
import org.folio.rest.acq.model.orders.CompositePoLine;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.services.finance.transaction.PaymentCreditWorkflowService;
import org.folio.services.order.OrderLineService;
import org.folio.services.voucher.VoucherCommandService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.toList;

public class InvoicePaymentService {

  private final InvoiceWorkflowDataHolderBuilder holderBuilder;
  private final PaymentCreditWorkflowService paymentCreditWorkflowService;
  private final VoucherCommandService voucherCommandService;
  private final OrderLineService orderLineService;

  public InvoicePaymentService(InvoiceWorkflowDataHolderBuilder holderBuilder,
                               PaymentCreditWorkflowService paymentCreditWorkflowService,
                               VoucherCommandService voucherCommandService,
                               OrderLineService orderLineService) {
    this.holderBuilder = holderBuilder;
    this.paymentCreditWorkflowService = paymentCreditWorkflowService;
    this.voucherCommandService = voucherCommandService;
    this.orderLineService = orderLineService;
  }

  /**
   * Handles transition of given invoice to PAID status.
   *
   * @param invoice Invoice to be paid
   * @return CompletableFuture that indicates when transition is completed
   */

  @VisibleForTesting
  public CompletableFuture<Void> payInvoice(Invoice invoice, List<InvoiceLine> invoiceLines, RequestContext requestContext) {

    //  Set payment date, when the invoice is being paid.
    invoice.setPaymentDate(invoice.getMetadata().getUpdatedDate());
    return getInvoiceWorkflowDataHolders(invoice, invoiceLines, requestContext)
      .thenCompose(holders -> paymentCreditWorkflowService.handlePaymentsAndCreditsCreation(holders, requestContext))
      .thenCompose(vVoid -> FolioVertxCompletableFuture.allOf(requestContext.getContext(), payPoLines(invoiceLines, requestContext),
        voucherCommandService.payInvoiceVoucher(invoice.getId(), requestContext))
      );
  }

  private CompletableFuture<List<InvoiceWorkflowDataHolder>> getInvoiceWorkflowDataHolders(Invoice invoice, List<InvoiceLine> lines, RequestContext requestContext) {
    List<InvoiceWorkflowDataHolder> dataHolders = holderBuilder.buildHoldersSkeleton(lines, invoice);
    return holderBuilder.withFunds(dataHolders, requestContext)
      .thenCompose(holders -> holderBuilder.withLedgers(holders, requestContext))
      .thenCompose(holders -> holderBuilder.withBudgets(holders, requestContext))
      .thenApply(holderBuilder::checkMultipleFiscalYears)
      .thenCompose(holders -> holderBuilder.withFiscalYear(holders, requestContext))
      .thenCompose(holders -> holderBuilder.withEncumbrances(holders, requestContext))
      .thenCompose(holders -> holderBuilder.withExpenseClasses(holders, requestContext))
      .thenCompose(holders -> holderBuilder.withExchangeRate(holders, requestContext));
  }

  /**
   * Updates payment status of the associated PO Lines.
   *
   * @param invoiceLines the invoice lines to be paid
   * @return CompletableFuture that indicates when transition is completed
   */
  private CompletableFuture<Void> payPoLines(List<InvoiceLine> invoiceLines, RequestContext requestContext) {
    Map<String, List<InvoiceLine>> poLineIdInvoiceLinesMap = groupInvoiceLinesByPoLineId(invoiceLines);
    return fetchPoLines(poLineIdInvoiceLinesMap, requestContext)
      .thenApply(this::updatePoLinesPaymentStatus)
      .thenCompose(poLines -> orderLineService.updateCompositePoLines(poLines, requestContext));
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
  private CompletableFuture<Map<CompositePoLine, CompositePoLine.PaymentStatus>> fetchPoLines(Map<String, List<InvoiceLine>> poLineIdsWithInvoiceLines,
                                                                                              RequestContext requestContext) {
    List<CompletableFuture<CompositePoLine>> futures = poLineIdsWithInvoiceLines.keySet()
      .stream()
      .map(poLine -> orderLineService.getPoLine(poLine, requestContext))
      .collect(toList());

    return allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
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
      !compositePoLine.getPaymentStatus().equals(CompositePoLine.PaymentStatus.ONGOING));
  }
}
