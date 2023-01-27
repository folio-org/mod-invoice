package org.folio.rest.impl;

import java.util.Map;

import org.folio.services.voucher.BatchVoucherService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class BatchVoucherHelper extends AbstractHelper {

  @Autowired
  private BatchVoucherService batchVoucherService;

  public BatchVoucherHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    SpringContextUtil.autowireDependencies(this, ctx);
  }

  /**
   * Gets batch voucher by id
   *
   * @param id batch voucher uuid
   * @return future with {@link String} representation of batch voucher
   */
  public Future<String> getBatchVoucherById(String id, String contentType) {
    return batchVoucherService.getBatchVoucherById(id, buildRequestContext())
      .map(bv -> batchVoucherService.convertBatchVoucher(bv, contentType));
  }

}
