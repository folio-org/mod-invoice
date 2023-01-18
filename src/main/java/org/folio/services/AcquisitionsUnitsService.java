package org.folio.services;

import static org.folio.invoices.utils.HelperUtils.SEARCH_PARAMS;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_MEMBERSHIPS;
import static org.folio.invoices.utils.ResourcePathResolver.ACQUISITIONS_UNITS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.folio.rest.acq.model.units.AcquisitionsUnitCollection;
import org.folio.rest.acq.model.units.AcquisitionsUnitMembershipCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;

import io.vertx.core.Future;

public class AcquisitionsUnitsService {
  public static final String ACQUISITIONS_UNIT_ID = "acquisitionsUnitId";
  static final String GET_UNITS_BY_QUERY = resourcesPath(ACQUISITIONS_UNITS) + SEARCH_PARAMS;
  static final String GET_UNITS_MEMBERSHIPS_BY_QUERY = resourcesPath(ACQUISITIONS_MEMBERSHIPS) + SEARCH_PARAMS;
  private final RestClient restClient;
  public AcquisitionsUnitsService (RestClient restClient) {
    this.restClient = restClient;
  }
  public Future<AcquisitionsUnitCollection> getAcquisitionsUnits(String query, int offset, int limit, RequestContext requestContext) {
    String endpoint = String.format(GET_UNITS_BY_QUERY, limit, offset, getEndpointWithQuery(query));
    return restClient.get(endpoint, AcquisitionsUnitCollection.class, requestContext);
  }

  public Future<AcquisitionsUnitMembershipCollection> getAcquisitionsUnitsMemberships(String query, int offset, int limit, RequestContext requestContext) {
    String endpoint = String.format(GET_UNITS_MEMBERSHIPS_BY_QUERY, limit, offset, getEndpointWithQuery(query));
    return restClient.get(endpoint, AcquisitionsUnitMembershipCollection.class, requestContext);
  }

  /**
   * Check whether the user is a member of at least one group from which the related invoice belongs.
   *
   * @return list of unit ids associated with user.
   */

}
