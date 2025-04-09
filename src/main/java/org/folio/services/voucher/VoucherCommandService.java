package org.folio.services.voucher;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isAlpha;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NUMBER_PREFIX_NOT_ALPHA;
import static org.folio.rest.impl.AbstractHelper.CONFIG_QUERY;
import static org.folio.rest.impl.AbstractHelper.INVOICE_CONFIG_MODULE_NAME;
import static org.folio.rest.impl.AbstractHelper.SYSTEM_CONFIG_QUERY;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.CacheableExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class VoucherCommandService {

  public static final String VOUCHER_NUMBER_CONFIG_NAME = "voucherNumber";
  public static final String VOUCHER_NUMBER_PREFIX_CONFIG = "voucherNumberPrefix";
  public static final String VOUCHER_NUMBER_PREFIX_CONFIG_QUERY = String.format(CONFIG_QUERY, INVOICE_CONFIG_MODULE_NAME, VOUCHER_NUMBER_CONFIG_NAME);

  @Autowired
  private VoucherNumberService voucherNumberService;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private CacheableExchangeRateService cacheableExchangeRateService;

  /**
   * Build new {@link Voucher} based on processed {@link Invoice}
   *
   * @param invoice invoice {@link Invoice} to be approved
   * @return completable future with {@link Voucher}
   */
  public Future<Voucher> buildNewVoucher(Invoice invoice, RequestContext requestContext) {
    Voucher voucher = new Voucher();
    voucher.setInvoiceId(invoice.getId());
    return getVoucherNumberWithPrefix(requestContext)
      .map(voucher::withVoucherNumber);
  }

  public Future<String> generateVoucherNumber(RequestContext requestContext) {
    return voucherNumberService.getNextNumber(requestContext)
      .map(SequenceNumber::getSequenceNumber);
  }

  public Future<String> getVoucherNumberWithPrefix(RequestContext requestContext) {
    return getVoucherNumberPrefix(requestContext)
      .map(prefix -> {
        validateVoucherNumberPrefix(prefix);
        return prefix;
      })
      .compose(prefix -> generateVoucherNumber(requestContext)
        .map(sequenceNumber -> prefix + sequenceNumber));
  }

  public boolean isVoucherNumberPrefixConfig(Config config) {
    return INVOICE_CONFIG_MODULE_NAME.equals(config.getModule()) && VOUCHER_NUMBER_CONFIG_NAME.equals(config.getConfigName());
  }

  public Future<Voucher> updateVoucherWithExchangeRate(Voucher voucher, Invoice invoice, RequestContext requestContext) {
    if (Objects.nonNull(invoice.getExchangeRate())) {
      voucher.setExchangeRate(invoice.getExchangeRate());
      return Future.succeededFuture(voucher);
    }
    return cacheableExchangeRateService.getExchangeRate(invoice.getCurrency(), voucher.getSystemCurrency(), invoice.getExchangeRate(), requestContext)
      .compose(exchangeRateOptional -> {
        var exchangeRate = exchangeRateOptional
          .orElseThrow(() -> new NoSuchElementException("Exchange rate cannot be retrieved"))
          .getExchangeRate();
        invoice.setExchangeRate(exchangeRate);
        voucher.setExchangeRate(exchangeRate);
        return Future.succeededFuture(voucher);
      });
  }

  private void validateVoucherNumberPrefix(String prefix) {
    if (StringUtils.isNotEmpty(prefix) && !isAlpha(prefix)) {
      var param = new Parameter().withKey("prefix").withValue(prefix);
      throw new HttpException(400, VOUCHER_NUMBER_PREFIX_NOT_ALPHA, List.of(param));
    }
  }

  private Future<String> getVoucherNumberPrefix(RequestContext requestContext) {
    return configurationService.getConfigurationsEntries(requestContext, SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_PREFIX_CONFIG_QUERY)
      .map(configs -> configs.getConfigs().stream()
        .filter(this::isVoucherNumberPrefixConfig)
        .map(config -> {
          if (config.getValue() != null) {
            String prefix = new JsonObject(config.getValue()).getString(VOUCHER_NUMBER_PREFIX_CONFIG);
            return StringUtils.isNotEmpty(prefix) ? prefix : EMPTY;
          } else {
            return EMPTY;
          }
        })
        .findFirst()
        .orElse(EMPTY));
  }
}
