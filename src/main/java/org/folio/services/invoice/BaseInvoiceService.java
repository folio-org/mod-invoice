package org.folio.services.invoice;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.calculateAdjustmentsTotal;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertToDoubleWithRounding;
import static org.folio.invoices.utils.ResourcePathResolver.FOLIO_INVOICE_NUMBER;
import static org.folio.invoices.utils.ResourcePathResolver.INVOICES;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceCollection;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.services.adjusment.AdjustmentsService;
import org.folio.services.order.OrderService;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.springframework.stereotype.Service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

@Service
public class BaseInvoiceService implements InvoiceService {
  private static final Logger logger = LogManager.getLogger(BaseInvoiceService.class);
  private static final String INVOICE_ENDPOINT = resourcesPath(INVOICES);
  private static final String INVOICE_BY_ID_ENDPOINT = INVOICE_ENDPOINT + "/{id}";

  private final AdjustmentsService adjustmentsService;
  private final RestClient restClient;
  private final InvoiceLineService invoiceLineService;
  private final OrderService orderService;

  public BaseInvoiceService(RestClient restClient, InvoiceLineService invoiceLineService, OrderService orderService) {
    this.restClient = restClient;
    this.invoiceLineService = invoiceLineService;
    this.orderService = orderService;
    this.adjustmentsService = new AdjustmentsService();
  }

  @Override
  public Future<InvoiceCollection> getInvoices(String query, int offset, int limit, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_ENDPOINT)
      .withQuery(query)
      .withOffset(offset)
      .withLimit(limit);
    return restClient.get(requestEntry, InvoiceCollection.class, requestContext);
  }

  @Override
  public Future<Invoice> getInvoiceById(String invoiceId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_BY_ID_ENDPOINT).withId(invoiceId);
    return restClient.get(requestEntry, Invoice.class, requestContext);
  }

  @Override
  public Future<Invoice> createInvoice(Invoice invoice, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_ENDPOINT);
    return restClient.post(requestEntry, invoice, Invoice.class, requestContext);
  }

  @Override
  public Future<Void> updateInvoice(Invoice invoice, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_BY_ID_ENDPOINT).withId(invoice.getId());
    return restClient.put(requestEntry, invoice, requestContext);
  }

  @Override
  public Future<Void> deleteInvoice(String invoiceId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(INVOICE_BY_ID_ENDPOINT).withId(invoiceId);
    return restClient.delete(requestEntry, requestContext)
      .compose(v -> orderService.deleteOrderInvoiceRelationshipByInvoiceId(invoiceId, requestContext));
  }

  public Future<String> generateFolioInvoiceNumber(RequestContext requestContext) {
    return restClient.get(resourcesPath(FOLIO_INVOICE_NUMBER), SequenceNumber.class, requestContext)
      .map(SequenceNumber::getSequenceNumber);
  }

  @Override
  public void calculateTotals(Invoice invoice, List<InvoiceLine> lines) {
    CurrencyUnit currency = Monetary.getCurrency(invoice.getCurrency());

    // 1. Sub-total
    MonetaryAmount subTotal = HelperUtils.summarizeSubTotals(lines, currency, false);

    // 2. Calculate Adjustments Total
    // If there are no invoice lines then adjustmentsTotal = sum of all invoice adjustments
    // If lines are present then adjustmentsTotal = notProratedInvoiceAdjustments + sum of invoiceLines adjustmentsTotal
    MonetaryAmount adjustmentsTotal;
    if (lines.isEmpty()) {
      List<Adjustment> proratedAdjustments = new ArrayList<>(adjustmentsService.getProratedAdjustments(invoice));
      proratedAdjustments.addAll(adjustmentsService.getNotProratedAdjustments(invoice));
      adjustmentsTotal = calculateAdjustmentsTotal(proratedAdjustments, subTotal);
    } else {
      adjustmentsTotal = calculateAdjustmentsTotal(adjustmentsService.getNotProratedAdjustments(invoice), subTotal)
        .add(calculateInvoiceLinesAdjustmentsTotal(lines, currency));
    }

    // 3. Total
    invoice.setTotal(convertToDoubleWithRounding(subTotal.add(adjustmentsTotal)));
    invoice.setAdjustmentsTotal(convertToDoubleWithRounding(adjustmentsTotal));
    invoice.setSubTotal(convertToDoubleWithRounding(subTotal));
  }

  @Override
  public void calculateTotals(Invoice invoice) {
    calculateTotals(invoice, Collections.emptyList());
  }

  /**
   * Updates total values of the invoice and invoice lines
   *
   * @param invoice invoice to update totals for
   * @param lines   List<InvoiceLine> invoice lines to update totals for
   * @return {code true} if adjustments total value is different from original one
   */
  @Override
  public boolean recalculateTotals(Invoice invoice, List<InvoiceLine> lines) {
    lines.forEach(line -> HelperUtils.calculateInvoiceLineTotals(line, invoice));
    return recalculateInvoiceTotals(invoice, lines);
  }

  /**
   * Gets invoice lines from the storage and updates total values of the invoice
   *
   * @param invoice invoice to update totals for
   * @return {code true} if adjustments total is different from original one
   */
  @Override
  public Future<Boolean> recalculateTotals(Invoice invoice, RequestContext requestContext) {
    return invoiceLineService.getInvoiceLinesWithTotals(invoice, requestContext)
      .map(invoiceLines -> recalculateTotals(invoice, invoiceLines));
  }

  @Override
  public Future<Void> updateInvoicesTotals(InvoiceCollection invoiceCollection, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(invoiceCollection.getInvoices())) {
      return succeededFuture(null);
    }
    List<Future<Void>> invoiceListFutures = new ArrayList<>(invoiceCollection.getInvoices().size());
    invoiceCollection.getInvoices().forEach(invoice -> invoiceListFutures.add(
      invoiceLineService.getInvoiceLinesWithTotals(invoice, requestContext)
        .map(invoiceLines -> {
          // clone invoice lines
          List<InvoiceLine> updatedInvoiceLines = invoiceLines.stream()
            .map(invoiceLine -> JsonObject.mapFrom(invoiceLine).mapTo(InvoiceLine.class))
            .collect(toList());

          recalculateTotals(invoice, updatedInvoiceLines);
          return null;
        })
    ));
    return collectResultsOnSuccess(invoiceListFutures)
      .onSuccess(results -> logger.debug("Invoice totals updated : {}", results.size()))
      .mapEmpty();
  }

  private MonetaryAmount calculateInvoiceLinesAdjustmentsTotal(List<InvoiceLine> lines, CurrencyUnit currency) {
    return lines.stream()
      .map(line -> Money.of(line.getAdjustmentsTotal(), currency))
      .collect(MonetaryFunctions.summarizingMonetary(currency))
      .getSum();
  }

  /**
   * Updates total values of the invoice
   *
   * @param invoice invoice to update totals for
   * @param lines   invoice lines for the invoice
   * @return {code true} if adjustments total value is different from original one
   */
  private boolean recalculateInvoiceTotals(Invoice invoice, List<InvoiceLine> lines) {
    Double adjustmentsTotal = invoice.getAdjustmentsTotal();
    Double subTotal = invoice.getSubTotal();
    Double total = invoice.getTotal();
    calculateTotals(invoice, lines);
    return !Objects.equals(adjustmentsTotal, invoice.getAdjustmentsTotal())
      || !Objects.equals(subTotal, invoice.getSubTotal())
      || !Objects.equals(total, invoice.getTotal());
  }

  public Future<Void> updateVoucherNumberInInvoice(Voucher voucher, RequestContext requestContext) {
    return getInvoiceById(voucher.getInvoiceId(), requestContext)
      .compose(invoice -> {
        invoice.setVoucherNumber(voucher.getVoucherNumber());
        return updateInvoice(invoice, requestContext)
          .onSuccess(result -> logger.debug("updateVoucherNumberInInvoice:: VoucherNumber '{}' was set to invoice '{}'", voucher.getVoucherNumber(), invoice.getId()))
          .onFailure(error -> logger.error("An error occurred", error));
      });
  }
}
