package org.folio.rest.impl;

import java.util.Map;

import org.folio.rest.acq.model.VoucherLineCollection;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.VoucherLine;
import org.folio.services.VoucherLineService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class VoucherLineHelper extends AbstractHelper {
  private final RequestContext requestContext;
  @Autowired
  VoucherLineService voucherLineService;
  public VoucherLineHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    this.requestContext = new RequestContext(ctx,okapiHeaders);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  public Future<Void> updateVoucherLine(VoucherLine voucherLine) {
    return voucherLineService.updateVoucherLine(voucherLine, requestContext);
  }

  public Future<VoucherLine> getVoucherLine(String id) {
    return voucherLineService.getVoucherLine(id, requestContext);
  }

  /**
   * Gets list of voucher line
   *
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link VoucherLineCollection} on success or an exception if processing fails
   */
  public Future<VoucherLineCollection> getVoucherLines(int limit, int offset, String query) {
    return voucherLineService.getVoucherLines(limit, offset, query, requestContext);
  }

}
