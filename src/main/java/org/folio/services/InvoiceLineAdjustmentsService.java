package org.folio.services;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Adjustment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InvoiceLineAdjustmentsService extends AdjustmentsService {

  @Override
  public boolean isAdjustmentIdsNotUnique(List<Adjustment> adjustments) {
    Map<String, Long> ids = adjustments.stream()
      .filter(adjustment -> StringUtils.isNotEmpty(adjustment.getId()))
      .collect(Collectors.groupingBy(Adjustment::getAdjustmentId, Collectors.counting()));

    return ids.entrySet().stream().anyMatch(entry -> entry.getValue() > 1);
  }
}
