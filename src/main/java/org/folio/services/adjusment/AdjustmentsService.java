package org.folio.services.adjusment;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.invoices.utils.HelperUtils.summarizeSubTotals;
import static org.folio.rest.impl.InvoiceLineHelper.HYPHEN_SEPARATOR;
import static org.folio.rest.jaxrs.model.Adjustment.Prorate.NOT_PRORATED;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryOperators;

import io.vertx.core.json.JsonObject;

public class AdjustmentsService {

  private final Logger logger = LogManager.getLogger(this.getClass());

  public static final Predicate<Adjustment> NOT_PRORATED_ADJUSTMENTS_PREDICATE = adj -> adj.getProrate() == NOT_PRORATED;
  public static final Predicate<Adjustment> PRORATED_ADJUSTMENTS_PREDICATE = NOT_PRORATED_ADJUSTMENTS_PREDICATE.negate();
  public static final Predicate<Adjustment> INVOICE_LINE_PRORATED_ADJUSTMENT_PREDICATE = adjustment -> isNotEmpty(adjustment.getAdjustmentId());

  public List<Adjustment> getProratedAdjustments(Invoice invoice) {
    return filterAdjustments(invoice.getAdjustments(), PRORATED_ADJUSTMENTS_PREDICATE);
  }

  public List<Adjustment> getNotProratedAdjustments(Invoice invoice) {
    return filterAdjustments(invoice.getAdjustments(), NOT_PRORATED_ADJUSTMENTS_PREDICATE);
  }

  public List<Adjustment> getProratedAdjustments(InvoiceLine invoiceLine) {
    return filterAdjustments(invoiceLine.getAdjustments(), INVOICE_LINE_PRORATED_ADJUSTMENT_PREDICATE);
  }

  public List<Adjustment> filterAdjustments(List<Adjustment> adjustments, Predicate<Adjustment> predicate) {
    return adjustments.stream()
      .filter(predicate)
      .collect(toList());
  }

  public List<InvoiceLine> applyProratedAdjustments(List<InvoiceLine> lines, Invoice invoice) {
    CurrencyUnit currencyUnit = Monetary.getCurrency(invoice.getCurrency());
    sortByInvoiceLineNumber(lines);
    List<Adjustment> proratedAdjustments = getProratedAdjustments(invoice);
    List<InvoiceLine> updatedLines = new ArrayList<>();
    for (Adjustment adjustment : proratedAdjustments) {
      switch (adjustment.getProrate()) {
        case BY_LINE:
          updatedLines.addAll(applyProratedAdjustmentByLines(adjustment, lines, currencyUnit));
          break;
        case BY_AMOUNT:
          updatedLines.addAll(applyAdjustmentsAndUpdateLines(adjustment, lines, currencyUnit));
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
      .toList();
  }

  private List<InvoiceLine> applyAdjustmentsAndUpdateLines(Adjustment adjustment, List<InvoiceLine> lines,
                                                           CurrencyUnit currencyUnit) {
    if (adjustment.getRelationToTotal() == Adjustment.RelationToTotal.INCLUDED_IN) {
      return applyProratedAmountTypeIncludedInAdjustments(adjustment, lines, currencyUnit);
    } else {
      return applyProratedAdjustmentByAmount(adjustment, lines, currencyUnit);
    }
  }

  public void processProratedAdjustments(List<InvoiceLine> lines, Invoice invoice) {
    List<Adjustment> proratedAdjustments = getProratedAdjustments(invoice);

    // Remove previously applied prorated adjustments if they are no longer available at invoice level
    filterDeletedAdjustments(proratedAdjustments, lines);

    // Apply prorated adjustments to each invoice line
    applyProratedAdjustments(lines, invoice);
  }

  /**
   * Removes adjustments at invoice line level based on invoice's prorated adjustments which are no longer available
   * @param proratedAdjustments list of prorated adjustments available at invoice level
   * @param invoiceLines list of invoice lines associated with current invoice
   */
  void filterDeletedAdjustments(List<Adjustment> proratedAdjustments, List<InvoiceLine> invoiceLines) {
    List<String> adjIds = proratedAdjustments.stream()
      .map(Adjustment::getId)
      .toList();

    invoiceLines.forEach(line -> line.getAdjustments()
        .removeIf(adj -> Objects.nonNull(adj.getAdjustmentId()) && !adjIds.contains(adj.getAdjustmentId())));
  }

    /**
     * Removes the invoice number from invoiceLineNumber if needed, and sorts lines
     * on the line number as an integer.
     *
     * @param lines to be sorted
     */
    private void sortByInvoiceLineNumber(List<InvoiceLine> lines) {
      lines.sort(Comparator.comparing(invoiceLine -> {
        String lineNumber = invoiceLine.getInvoiceLineNumber();
        if (lineNumber.contains(HYPHEN_SEPARATOR)) // only needed until MODINVOSTO-75 is done
          lineNumber = lineNumber.split(HYPHEN_SEPARATOR)[1];
        return Integer.parseInt(lineNumber);
      }));
    }

  /**
   * Each invoiceLine gets adjustment value divided by quantity of lines
   */
  private List<InvoiceLine> applyProratedAdjustmentByLines(Adjustment adjustment, List<InvoiceLine> lines,
                                                           CurrencyUnit currencyUnit) {
    if (Adjustment.Type.PERCENTAGE == adjustment.getType()) {
      return applyPercentageAdjustmentsByLines(adjustment, lines, currencyUnit);
    } else {
      return applyAmountTypeProratedAdjustments(adjustment, lines, currencyUnit, prorateByLines(lines));
    }
  }

  private List<InvoiceLine> applyPercentageAdjustmentsByLines(Adjustment adjustment, List<InvoiceLine> lines, CurrencyUnit currencyUnit) {
    Adjustment amountAdjustment = convertToAmountAdjustment(adjustment, lines, currencyUnit);

    return applyAmountTypeProratedAdjustments(amountAdjustment, lines, currencyUnit, prorateByLines(lines));
  }

  private Adjustment convertToAmountAdjustment(Adjustment adjustment, List<InvoiceLine> lines, CurrencyUnit currencyUnit) {
    MonetaryAmount subTotal = summarizeSubTotals(lines, currencyUnit, false);
    Adjustment amountAdjustment = JsonObject.mapFrom(adjustment)
      .mapTo(adjustment.getClass());
    amountAdjustment.setValue(subTotal.with(MonetaryOperators.percent(adjustment.getValue())).with(Monetary.getDefaultRounding()).getNumber().doubleValue());
    amountAdjustment.setType(Adjustment.Type.AMOUNT);
    return amountAdjustment;
  }

  private Adjustment prepareAdjustmentForLine(Adjustment adjustment) {
    return JsonObject.mapFrom(adjustment)
      .mapTo(adjustment.getClass())
      .withId(null)
      .withAdjustmentId(adjustment.getId())
      .withProrate(NOT_PRORATED);
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

  private List<InvoiceLine> applyAmountTypeProratedAdjustments(Adjustment adjustment, List<InvoiceLine> lines,
                                                               CurrencyUnit currencyUnit, BiFunction<MonetaryAmount, InvoiceLine, MonetaryAmount> prorateFunction) {
    List<InvoiceLine> updatedLines = new ArrayList<>();

    MonetaryAmount expectedAdjustmentTotal = Money.of(adjustment.getValue(), currencyUnit);
    Map<String, MonetaryAmount> lineIdAdjustmentValueMap = calculateAdjValueForEachLine(lines, prorateFunction,
      expectedAdjustmentTotal);

    MonetaryAmount remainder = expectedAdjustmentTotal.abs()
      .subtract(getActualAdjustmentTotal(lineIdAdjustmentValueMap, currencyUnit).abs());
    int remainderSignum = remainder.signum();
    MonetaryAmount smallestUnit = getSmallestUnit(expectedAdjustmentTotal, remainderSignum);

    for (ListIterator<InvoiceLine> iterator = getIterator(lines, remainderSignum); isIteratorHasNext(iterator, remainderSignum); ) {
      final InvoiceLine line = iteratorNext(iterator, remainderSignum);
      MonetaryAmount amount = lineIdAdjustmentValueMap.get(line.getId());

      if (!remainder.isZero()) {
        amount = amount.add(smallestUnit);
        remainder = remainder.abs().subtract(smallestUnit.abs()).multiply(remainderSignum);
      }

      Adjustment proratedAdjustment = prepareAdjustmentForLine(adjustment);
      proratedAdjustment.setValue(amount.getNumber().doubleValue());
      if (addAdjustmentToLine(line, proratedAdjustment)) {
        updatedLines.add(line);
      }
    }

    return updatedLines;
  }

  private List<InvoiceLine> applyProratedAmountTypeIncludedInAdjustments(Adjustment adjustment, List<InvoiceLine> lines,
                                                                         CurrencyUnit currencyUnit) {
    List<InvoiceLine> updatedLines = new ArrayList<>();
    for (InvoiceLine line : lines) {
      if (invoiceLineWasAdjustedById(adjustment, line)) {
        continue;
      }
      Adjustment preparedAdjustment = prepareIncludedInAdjustmentValue(adjustment, currencyUnit, line);
      if (addAdjustmentToLine(line, preparedAdjustment)) {
        updatedLines.add(line);
      }
    }
    return updatedLines;
  }

  private boolean invoiceLineWasAdjustedById(Adjustment adjustment, InvoiceLine line) {
    return Objects.nonNull(adjustment.getId()) && line.getAdjustments().stream()
      .map(Adjustment::getAdjustmentId)
      .filter(Objects::nonNull)
      .anyMatch(lineAdjustmentId -> lineAdjustmentId.equals(adjustment.getId()));
  }

  private Adjustment prepareIncludedInAdjustmentValue(Adjustment adjustment, CurrencyUnit currencyUnit, InvoiceLine line) {
    MonetaryAmount lineSubtotal = Money.of(line.getSubTotal(), currencyUnit);
    MonetaryAmount amountAdjustmentValue = lineSubtotal.multiply(adjustment.getValue())
      .divide(Money.of(100, currencyUnit).add(Money.of(adjustment.getValue(), currencyUnit)).getNumber().doubleValue())
      .with(Monetary.getDefaultRounding());
    Adjustment preparedAdjustment = prepareAdjustmentForLine(adjustment.withType(Adjustment.Type.AMOUNT))
      .withValue(amountAdjustmentValue.getNumber().doubleValue());
    line.withSubTotal(lineSubtotal.subtract(amountAdjustmentValue).getNumber().doubleValue());
    line.withAdjustmentsTotal(amountAdjustmentValue.getNumber().doubleValue());
    return preparedAdjustment;
  }

  /**
   * Each invoiceLine gets a portion of the amount proportionate to the invoiceLine's contribution to the invoice subTotal.
   * Prorated percentage adjustments of this type aren't split but rather each invoiceLine gets an adjustment of that percentage
   */
  private List<InvoiceLine> applyProratedAdjustmentByAmount(Adjustment adjustment, List<InvoiceLine> lines,
                                                            CurrencyUnit currencyUnit) {
    if (adjustment.getType() == Adjustment.Type.PERCENTAGE) {
      adjustment = convertToAmountAdjustment(adjustment, lines, currencyUnit);
    }

    MonetaryAmount grandSubTotal = summarizeSubTotals(lines, currencyUnit, true);
    if (grandSubTotal.isZero()) {
      // If summarized subTotal (by abs) is zero, each line has zero amount (e.g. gift) so adjustment should be prorated "By line"
      return applyProratedAdjustmentByLines(adjustment, lines, currencyUnit);
    }

    return applyAmountTypeProratedAdjustments(adjustment, lines, currencyUnit, prorateByAmountFunction(grandSubTotal));
  }

  private BiFunction<MonetaryAmount, InvoiceLine, MonetaryAmount> prorateByAmountFunction(MonetaryAmount grandSubTotal) {
    return (amount, line) -> amount
      // The adjustment amount should be calculated by absolute value of subTotal
      .multiply(Math.abs(line.getSubTotal()))
      .divide(grandSubTotal.getNumber()).with(Monetary.getDefaultRounding());
  }

  /**
   * Each invoiceLine gets an portion of the amount proportionate to the invoiceLine's quantity.
   */
  private List<InvoiceLine> applyProratedAdjustmentByQuantity(Adjustment adjustment, List<InvoiceLine> lines,
                                                              CurrencyUnit currencyUnit) {
    if (adjustment.getType() == Adjustment.Type.PERCENTAGE) {
      return applyPercentageAdjustmentsByQuantity(adjustment, lines, currencyUnit);
    }
    return applyAmountTypeProratedAdjustments(adjustment, lines, currencyUnit, prorateByAmountFunction(lines));
  }

  private List<InvoiceLine> applyPercentageAdjustmentsByQuantity(Adjustment adjustment, List<InvoiceLine> lines, CurrencyUnit currencyUnit) {
    Adjustment amountAdjustment = convertToAmountAdjustment(adjustment, lines, currencyUnit);
    return applyAmountTypeProratedAdjustments(amountAdjustment, lines, currencyUnit, prorateByAmountFunction(lines));
  }

  private BiFunction<MonetaryAmount, InvoiceLine, MonetaryAmount> prorateByAmountFunction(List<InvoiceLine> invoiceLines) {
    // The adjustment amount should be calculated by absolute value of subTotal
    return (amount, line) -> amount.multiply(line.getQuantity()).divide(getTotalQuantities(invoiceLines)).with(Monetary.getDefaultRounding());
  }

  private Integer getTotalQuantities(List<InvoiceLine> lines) {
    return lines.stream().map(InvoiceLine::getQuantity).reduce(0, Integer::sum);
  }

  private Map<String, MonetaryAmount> calculateAdjValueForEachLine(List<InvoiceLine> lines,
                                                                   BiFunction<MonetaryAmount, InvoiceLine, MonetaryAmount> prorateFunction, MonetaryAmount expectedAdjustmentTotal) {
    return lines.stream()
      .collect(toMap(InvoiceLine::getId, line -> prorateFunction.apply(expectedAdjustmentTotal, line)));
  }

  private MonetaryAmount getActualAdjustmentTotal(Map<String, MonetaryAmount> lineIdAdjustmentValueMap, CurrencyUnit currencyUnit) {
    return lineIdAdjustmentValueMap.values().stream().reduce(MonetaryAmount::add).orElse(Money.zero(currencyUnit));
  }

  private MonetaryAmount getSmallestUnit(MonetaryAmount expectedAdjustmentValue, int remainderSignum) {
    CurrencyUnit currencyUnit = expectedAdjustmentValue.getCurrency();
    int decimalPlaces = currencyUnit.getDefaultFractionDigits();
    int smallestUnitSignum = expectedAdjustmentValue.signum() * remainderSignum;
    return Money.of(1 / Math.pow(10, decimalPlaces), currencyUnit).multiply(smallestUnitSignum);
  }

  private ListIterator<InvoiceLine> getIterator(List<InvoiceLine> lines, int remainder) {
    return remainder > 0 ? lines.listIterator(lines.size()) : lines.listIterator();
  }

  private boolean isIteratorHasNext(ListIterator<InvoiceLine> iterator, int remainder) {
    return remainder > 0 ? iterator.hasPrevious() : iterator.hasNext();
  }

  private InvoiceLine iteratorNext(ListIterator<InvoiceLine> iterator, int remainder) {
    return remainder > 0 ? iterator.previous() : iterator.next();
  }

  private BiFunction<MonetaryAmount, InvoiceLine, MonetaryAmount> prorateByLines(List<InvoiceLine> lines) {
    return (amount, line) -> amount.divide(lines.size()).with(Monetary.getDefaultRounding());
  }
}
