package org.folio.services;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Adjustment;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class InvoiceAdjustmentsService extends AdjustmentsService {

  @Override
  public List<InvoiceLine> applyProratedAdjustments(List<InvoiceLine> lines, Invoice invoice) {
    List<Adjustment> proratedAdjustments = getProratedAdjustments(invoice.getAdjustments());

    // Remove previously applied prorated adjustments if they are no longer available at invoice level
    List<InvoiceLine> updatedLines = filterDeletedAdjustments(proratedAdjustments, lines);

    // Apply prorated adjustments to each invoice line
    updatedLines.addAll(super.applyProratedAdjustments(lines, invoice));

    // Return only unique invoice lines
    return updatedLines.stream()
      .distinct()
      .collect(toList());
  }

  @Override
  public boolean isAdjustmentIdsNotUnique(List<Adjustment> adjustments) {
    Map<String, Long> ids = adjustments.stream()
      .filter(adjustment -> StringUtils.isNotEmpty(adjustment.getId()))
      .collect(Collectors.groupingBy(Adjustment::getId, Collectors.counting()));

    return ids.entrySet().stream().anyMatch(entry -> entry.getValue() > 1);
  }
}
