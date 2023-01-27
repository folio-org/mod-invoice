package org.folio.services;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.invoices.utils.HelperUtils.buildIdsChunks;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.acq.model.OrganizationCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Invoice;

import io.vertx.core.Future;

public class VendorRetrieveService {

  private static final Logger logger = LogManager.getLogger(VendorRetrieveService.class);

  private static final String ORGANIZATIONS_STORAGE_VENDORS = "/organizations-storage/organizations";
  private static final String ORGANIZATIONS_STORAGE_VENDOR = ORGANIZATIONS_STORAGE_VENDORS + "/{id}";

  private final RestClient restClient;

  public VendorRetrieveService(RestClient restClient) {
    this.restClient = restClient;
  }

  static final int MAX_IDS_FOR_GET_RQ = 15;


  public Future<Map<String, Organization>> getVendorsMap(List<Invoice> invoices, RequestContext requestContext) {
    return getVendorsByChunks(invoices, requestContext).map(organizationCollections -> organizationCollections.stream()
      .map(OrganizationCollection::getOrganizations)
      .collect(toList())
      .stream()
      .flatMap(List::stream)
      .collect(Collectors.toList()))
      .map(organizations -> organizations.stream()
        .distinct()
        .collect(toMap(Organization::getId, Function.identity())));
  }

  public Future<List<OrganizationCollection>> getVendorsByChunks(List<Invoice> invoices,  RequestContext requestContext) {
    List<Future<OrganizationCollection>> invoiceFutureList = buildIdsChunks(invoices, MAX_IDS_FOR_GET_RQ).values()
      .stream()
      .map(this::getVendorIds)
      .map(ids -> getVendors(ids, requestContext))
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private Set<String> getVendorIds(List<Invoice> invoices) {
    return invoices.stream()
      .map(Invoice::getVendorId)
      .collect(Collectors.toSet());
  }

  /**
   * Retrieves set of access providers
   *
   * @param vendorIds - {@link Set<String>} of access providers id
   * @return CompletableFuture with {@link List<Organization>} of vendors
   */
  public Future<OrganizationCollection> getVendors(Set<String> vendorIds, RequestContext requestContext) {
    String query = convertIdsToCqlQuery(new ArrayList<>(vendorIds));
    RequestEntry requestEntry = new RequestEntry(ORGANIZATIONS_STORAGE_VENDORS)
        .withQuery(query)
        .withLimit(vendorIds.size())
        .withOffset(0);
    return restClient.get(requestEntry, OrganizationCollection.class, requestContext);
  }

  public Future<Organization> getVendor(String vendorId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(ORGANIZATIONS_STORAGE_VENDOR).withId(vendorId);
    return restClient.get(requestEntry, Organization.class, requestContext)
      .onFailure(throwable -> logger.error("Failed to retrieve organization with id {}", vendorId, throwable));
  }
}
