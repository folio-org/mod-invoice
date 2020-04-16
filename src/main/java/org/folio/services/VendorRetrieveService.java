package org.folio.services;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.invoices.utils.HelperUtils.buildIdsChunks;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.OrganizationCollection;
import org.folio.rest.impl.VendorHelper;
import org.folio.rest.jaxrs.model.Invoice;

import io.vertx.core.Context;

public class VendorRetrieveService {
  static final int MAX_IDS_FOR_GET_RQ = 15;
  private final VendorHelper vendorHelper;

  public VendorRetrieveService(Map<String, String> okapiHeaders, Context ctx, String lang) {
    this.vendorHelper = new VendorHelper(okapiHeaders, ctx, lang);
  }

  public CompletableFuture<Map<String, Organization>> getVendorsMap(List<Invoice> invoices) {
    CompletableFuture<Map<String, Organization>> future = new CompletableFuture<>();
    getVendorsByChunks(invoices, MAX_IDS_FOR_GET_RQ)
      .thenApply(organizationCollections ->
        organizationCollections.stream()
          .map(OrganizationCollection::getOrganizations)
          .collect(toList()).stream()
          .flatMap(List::stream)
          .collect(Collectors.toList()))
      .thenAccept(organizations -> future.complete(organizations.stream().collect(toMap(Organization::getId, Function.identity()))))
      .thenAccept(v -> vendorHelper.closeHttpClient())
      .exceptionally(t -> {
        future.completeExceptionally(t);
        vendorHelper.closeHttpClient();
        return null;
      });
    return future;
  }

  public CompletableFuture<List<OrganizationCollection>> getVendorsByChunks(List<Invoice> invoices, int maxRecordsPerGet) {
    List<CompletableFuture<OrganizationCollection>> invoiceFutureList = buildIdsChunks(invoices, maxRecordsPerGet).values()
      .stream()
      .map(this::getVendorIds)
      .map(vendorHelper::getVendors)
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private Set<String> getVendorIds(List<Invoice> invoices) {
    return invoices.stream()
      .map(Invoice::getVendorId)
      .collect(Collectors.toSet());
  }
}
