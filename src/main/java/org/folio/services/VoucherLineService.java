package org.folio.services;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.HelperUtils.SEARCH_PARAMS;
import static org.folio.invoices.utils.HelperUtils.buildIdsChunks;
import static org.folio.invoices.utils.HelperUtils.collectResultsOnSuccess;
import static org.folio.invoices.utils.HelperUtils.convertIdsToCqlQuery;
import static org.folio.invoices.utils.HelperUtils.getEndpointWithQuery;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_LINES;
import static org.folio.invoices.utils.ResourcePathResolver.resourceByIdPath;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.RestConstants.MAX_IDS_FOR_GET_RQ;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.rest.jaxrs.model.VoucherLine;

import io.vertx.core.Future;

public class VoucherLineService {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  private static final String GET_VOUCHER_LINE_BY_QUERY = resourcesPath(VOUCHER_LINES) + SEARCH_PARAMS;
  private final RestClient restClient;
  public VoucherLineService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Future<Void> updateVoucherLine(VoucherLine voucherLine, RequestContext requestContext) {
    String path = resourceByIdPath(VOUCHER_LINES, voucherLine.getId());
    return restClient.put(path, voucherLine, requestContext);
  }

  public Future<VoucherLine> getVoucherLine(String id, RequestContext requestContext) {
    String endpoint = resourceByIdPath(VOUCHER_LINES, id);
    return restClient.get(endpoint, VoucherLine.class, requestContext);
  }

  /**
   * Gets list of voucher line
   *
   * @param limit          Limit the number of elements returned in the response
   * @param offset         Skip over a number of elements by specifying an offset value for the query
   * @param query          A query expressed as a CQL string using valid searchable fields
   * @param requestContext
   * @return completable future with {@link VoucherLineCollection} on success or an exception if processing fails
   */
  public Future<VoucherLineCollection> getVoucherLines(int limit, int offset, String query, RequestContext requestContext) {
    String queryParam = getEndpointWithQuery(query);
    String endpoint = String.format(GET_VOUCHER_LINE_BY_QUERY, limit, offset, queryParam);
    return restClient.get(endpoint, VoucherLineCollection.class, requestContext);
  }

  public Future<VoucherLine> createVoucherLine(VoucherLine voucherLine, RequestContext requestContext) {
    return restClient.post(resourcesPath(VOUCHER_LINES), voucherLine, VoucherLine.class, requestContext);
  }

  public Future<Void> deleteVoucherLine(String id, RequestContext requestContext) {
    return restClient.delete(resourceByIdPath(VOUCHER_LINES, id), requestContext);
  }


  public Future<Map<String, List<org.folio.rest.acq.model.VoucherLine>>> getVoucherLinesMap(VoucherCollection voucherCollection,
    RequestContext requestContext) {
    return getVoucherLinesByChunks(voucherCollection.getVouchers(), requestContext)
      .map(voucherLineCollections -> voucherLineCollections.stream()
        .map(VoucherLineCollection::getVoucherLines)
        .collect(toList())
        .stream()
        .flatMap(List::stream)
        .collect(Collectors.toList()))
      .map(voucherLines -> voucherLines.stream()
        .collect(groupingBy(org.folio.rest.acq.model.VoucherLine::getVoucherId)));
  }

  public Future<List<VoucherLineCollection>> getVoucherLinesByChunks(List<Voucher> vouchers, RequestContext requestContext) {
    List<Future<VoucherLineCollection>> invoiceFutureList = buildIdsChunks(vouchers, MAX_IDS_FOR_GET_RQ).values()
      .stream()
      .map(this::buildVoucherLinesQuery)
      .map(query -> getVoucherLines(Integer.MAX_VALUE, 0, query, requestContext))
      .collect(Collectors.toList());

    return collectResultsOnSuccess(invoiceFutureList);
  }

  private String buildVoucherLinesQuery(List<Voucher> vouchers) {
    List<String> voucherIds = vouchers.stream()
      .map(Voucher::getId)
      .collect(Collectors.toList());
    return convertIdsToCqlQuery(voucherIds, "voucherId", true);
  }
}
