package org.folio.helpers;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.encodeQuery;
import static org.folio.invoices.utils.HelperUtils.handleGetRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


import org.folio.rest.acq.model.Organization;
import org.folio.rest.impl.AbstractHelper;

import io.vertx.core.Context;
import io.vertx.core.json.JsonObject;

public class VendorHelper extends AbstractHelper {
  private static final String ORGANIZATIONS = "organizations";
  private static final String ORGANIZATIONS_STORAGE_VENDORS = "/organizations-storage/organizations/";
  private static final String ORGANIZATIONS_WITH_QUERY_ENDPOINT = "/organizations-storage/organizations?limit=%d&lang=%s&query=%s";

  public VendorHelper(Map<String, String> okapiHeaders, Context ctx, String lang) {
    super(okapiHeaders, ctx, lang);
  }

  /**
   * Retrieves vendor by id
   *
   * @param vendorId vendor's id
   * @return CompletableFuture with {@link Organization} object
   */
  public CompletableFuture<Organization> getVendorById(String vendorId) {
    return handleGetRequest(ORGANIZATIONS_STORAGE_VENDORS + vendorId, httpClient, ctx, okapiHeaders, logger)
      .thenApply(json -> json.mapTo(Organization.class));
  }

  /**
   * Retrieves set of access providers
   *
   * @param vendorIds - {@link Set<String>} of access providers id
   * @return CompletableFuture with {@link List<Organization>} of vendors
   */
  public CompletableFuture<List<Organization>> getVendors(Set<String> vendorIds) {
    String query = convertIdsToCqlQuery(new ArrayList<>(vendorIds));
    String endpoint = String.format(ORGANIZATIONS_WITH_QUERY_ENDPOINT, vendorIds.size(), lang, encodeQuery(query, logger));
    return handleGetRequest(endpoint, httpClient, ctx, okapiHeaders, logger).thenApply(jsons -> jsons.getJsonArray(ORGANIZATIONS)
      .stream()
      .map(obj -> ((JsonObject) obj).mapTo(Organization.class))
      .collect(toList()));
  }
}
