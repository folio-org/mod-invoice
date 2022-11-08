package org.folio.services.voucher;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isAlpha;
import static org.folio.completablefuture.FolioVertxCompletableFuture.supplyBlockingAsync;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NUMBER_PREFIX_NOT_ALPHA;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_UPDATE_FAILURE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.impl.AbstractHelper.CONFIG_QUERY;
import static org.folio.rest.impl.AbstractHelper.INVOICE_CONFIG_MODULE_NAME;
import static org.folio.rest.impl.AbstractHelper.SYSTEM_CONFIG_QUERY;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ExchangeRateProvider;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.invoices.utils.HelperUtils;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.InvoiceLine;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.services.configuration.ConfigurationService;
import org.folio.services.exchange.ExchangeRateProviderResolver;
import org.folio.services.validator.VoucherValidator;

import io.vertx.core.json.JsonObject;

public class VoucherCommandService {

  private static final String VOUCHER_ENDPOINT = resourcesPath(VOUCHERS_STORAGE);
  private static final String VOUCHER_BY_ID_ENDPOINT = resourcesPath(VOUCHERS_STORAGE) + "/{id}";

  public static final String VOUCHER_NUMBER_CONFIG_NAME = "voucherNumber";
  public static final String VOUCHER_NUMBER_PREFIX_CONFIG = "voucherNumberPrefix";
  public static final String VOUCHER_NUMBER_PREFIX_CONFIG_QUERY = String.format(CONFIG_QUERY, INVOICE_CONFIG_MODULE_NAME, VOUCHER_NUMBER_CONFIG_NAME);


  private final RestClient restClient;
  private final VoucherNumberService voucherNumberService;
  private final VoucherRetrieveService voucherRetrieveService;
  private final VoucherValidator voucherValidator;
  private final ConfigurationService configurationService;
  private final ExchangeRateProviderResolver exchangeRateProviderResolver;

  public VoucherCommandService(RestClient restClient, VoucherNumberService voucherNumberService,
                               VoucherRetrieveService voucherRetrieveService,
                               VoucherValidator voucherValidator, ConfigurationService configurationService,
                               ExchangeRateProviderResolver exchangeRateProviderResolver
  ) {
    this.restClient = restClient;
    this.voucherNumberService = voucherNumberService;
    this.voucherRetrieveService = voucherRetrieveService;
    this.voucherValidator = voucherValidator;
    this.configurationService = configurationService;
    this.exchangeRateProviderResolver = exchangeRateProviderResolver;
  }


  public CompletableFuture<Void> partialVoucherUpdate(String voucherId, Voucher voucher, RequestContext requestContext) {
    return voucherRetrieveService.getVoucherById(voucherId, requestContext)
                                .thenAccept(voucherFromStorage -> voucherValidator.validateProtectedFields(voucher, voucherFromStorage))
                                .thenCompose(aVoid -> updateVoucher(voucherId, voucher, requestContext));
  }


  public CompletableFuture<Void> updateVoucher(String voucherId, Voucher voucher, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_BY_ID_ENDPOINT).withId(voucherId);
    return restClient.put(requestEntry, voucher, requestContext);
  }

  /**
   * Updates associated Voucher status to Paid.
   *
   * @param invoiceId invoice id
   * @return CompletableFuture that indicates when transition is completed
   */
  public CompletableFuture<Void> payInvoiceVoucher(String invoiceId, RequestContext requestContext) {
    return voucherRetrieveService.getVoucherByInvoiceId(invoiceId, requestContext)
      .thenApply(voucher -> Optional.ofNullable(voucher).orElseThrow(() -> new HttpException(404, VOUCHER_NOT_FOUND.toError())))
      .thenCompose(voucher -> updateVoucherStatus(voucher, Voucher.Status.PAID, requestContext));
  }

  /**
   * Updates associated Voucher status to Cancelled, does nothing if no associated voucher is found.
   *
   * @param invoiceId invoice id
   * @return CompletableFuture that indicates when transition is completed
   */
  public CompletableFuture<Void> cancelInvoiceVoucher(String invoiceId, RequestContext requestContext) {
    return voucherRetrieveService.getVoucherByInvoiceId(invoiceId, requestContext)
      .thenCompose(voucher -> {
        if (voucher == null)
          return completedFuture(null);
        return updateVoucherStatus(voucher, Voucher.Status.CANCELLED, requestContext);
      });
  }

  public CompletableFuture<Voucher> createVoucher(Voucher voucher, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_ENDPOINT);
    return restClient.post(requestEntry, voucher, requestContext, Voucher.class)
                                   .thenApply(voucherP -> voucher.withId(voucherP.getId()));
  }

  /**
   * Build new {@link Voucher} based on processed {@link Invoice}
   *
   * @param invoice invoice {@link Invoice} to be approved
   * @return completable future with {@link Voucher}
   */
  public CompletableFuture<Voucher> buildNewVoucher(Invoice invoice, RequestContext requestContext) {
    Voucher voucher = new Voucher();
    voucher.setInvoiceId(invoice.getId());
    return  getVoucherNumberWithPrefix(requestContext)
                    .thenApply(voucher::withVoucherNumber);
  }

  public CompletableFuture<String> generateVoucherNumber(RequestContext requestContext) {
    return voucherNumberService.getNextNumber(requestContext)
                                         .thenApply(SequenceNumber::getSequenceNumber);
  }


  public CompletableFuture<String> getVoucherNumberWithPrefix(RequestContext requestContext) {
    return getVoucherNumberPrefix(requestContext)
              .thenApply(prefix -> { validateVoucherNumberPrefix(prefix); return prefix; })
              .thenCompose(prefix -> generateVoucherNumber(requestContext)
                                            .thenApply(sequenceNumber -> prefix + sequenceNumber));
  }

  public boolean isVoucherNumberPrefixConfig(Config config) {
    return INVOICE_CONFIG_MODULE_NAME.equals(config.getModule()) && VOUCHER_NUMBER_CONFIG_NAME.equals(config.getConfigName());
  }

  public CompletableFuture<Voucher> updateVoucherWithExchangeRate(Voucher voucher, Invoice invoice, RequestContext requestContext) {
    return supplyBlockingAsync(requestContext.getContext(), () -> {
      ConversionQuery conversionQuery = HelperUtils.buildConversionQuery(invoice, voucher.getSystemCurrency());
      ExchangeRateProvider exchangeRateProvider = exchangeRateProviderResolver.resolve(conversionQuery, requestContext);
      ExchangeRate exchangeRate = exchangeRateProvider.getExchangeRate(conversionQuery);
      invoice.setExchangeRate(exchangeRate.getFactor().doubleValue());
      return voucher.withExchangeRate(exchangeRate.getFactor().doubleValue());
    });
  }

  private void validateVoucherNumberPrefix(String prefix) {
    if (StringUtils.isNotEmpty(prefix) && !isAlpha(prefix)) {
      throw new HttpException(400, VOUCHER_NUMBER_PREFIX_NOT_ALPHA);
    }
  }

  private CompletableFuture<String> getVoucherNumberPrefix(RequestContext requestContext) {
    return configurationService.getConfigurationsEntries(requestContext, SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_PREFIX_CONFIG_QUERY)
            .thenApply(configs ->
               configs.getConfigs().stream()
                    .filter(this::isVoucherNumberPrefixConfig)
                    .map(config -> {
                      if(config.getValue() != null) {
                        String prefix = new JsonObject(config.getValue()).getString(VOUCHER_NUMBER_PREFIX_CONFIG);
                        return StringUtils.isNotEmpty(prefix) ? prefix : EMPTY;
                      } else {
                        return EMPTY;
                      }
                    })
                    .findFirst()
                    .orElse(EMPTY)
            );
  }

  /**
   * In case voucher's status is already the given one, returns completed future.
   * Otherwise updates voucher in storage with given status.
   * @param voucher existing voucher
   * @param status new voucher status
   * @return completed future on success or with {@link HttpException} if update fails
   */
  private CompletableFuture<Void> updateVoucherStatus(Voucher voucher, Voucher.Status status, RequestContext requestContext) {
    if (voucher.getStatus() == status) {
      // Voucher already has the status
      return completedFuture(null);
    }
    return updateVoucher(voucher.getId(), voucher.withStatus(status), requestContext)
      .exceptionally(fail -> {
        throw new HttpException(500, VOUCHER_UPDATE_FAILURE.toError());
      });
  }

}
