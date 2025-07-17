package org.folio.services.voucher;

import static org.apache.commons.lang3.StringUtils.isAlpha;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NUMBER_PREFIX_NOT_ALPHA;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.services.caches.CommonSettingsCache;
import org.folio.services.exchange.CacheableExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;

public class VoucherCommandService {

  @Autowired
  private VoucherNumberService voucherNumberService;
  @Autowired
  private CommonSettingsCache commonSettingsCache;
  @Autowired
  private CacheableExchangeRateService cacheableExchangeRateService;

  /**
   * Build new {@link Voucher} based on processed {@link Invoice}
   *
   * @param invoice invoice {@link Invoice} to be approved
   * @return completable future with {@link Voucher}
   */
  public Future<Voucher> buildNewVoucher(Invoice invoice, RequestContext requestContext) {
    return getVoucherNumberWithPrefix(requestContext)
      .map(voucherNumber -> new Voucher()
        .withInvoiceId(invoice.getId())
        .withVoucherNumber(voucherNumber));
  }

  public Future<Voucher> updateVoucherWithExchangeRate(Voucher voucher, Invoice invoice, RequestContext requestContext) {
    return cacheableExchangeRateService.getExchangeRate(invoice.getCurrency(), voucher.getSystemCurrency(), invoice.getExchangeRate(), invoice.getOperationMode(), requestContext)
      .compose(exchangeRate -> {
        invoice.setExchangeRate(exchangeRate.getExchangeRate());
        invoice.setOperationMode(exchangeRate.getOperationMode().name());
        voucher.setExchangeRate(exchangeRate.getExchangeRate());
        voucher.setOperationMode(exchangeRate.getOperationMode().name());
        return Future.succeededFuture(voucher);
      });
  }

  public Future<String> getVoucherNumberWithPrefix(RequestContext requestContext) {
    return commonSettingsCache.getVoucherNumberPrefix(requestContext)
      .map(this::validateVoucherNumberPrefix)
      .compose(prefix -> voucherNumberService.getNextNumber(requestContext)
        .map(SequenceNumber::getSequenceNumber)
        .map(prefix::concat));
  }

  private String validateVoucherNumberPrefix(String prefix) {
    if (StringUtils.isNotEmpty(prefix) && !isAlpha(prefix)) {
      var param = new Parameter().withKey("prefix").withValue(prefix);
      throw new HttpException(400, VOUCHER_NUMBER_PREFIX_NOT_ALPHA, List.of(param));
    }
    return prefix;
  }

}
