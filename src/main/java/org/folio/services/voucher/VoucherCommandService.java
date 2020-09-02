package org.folio.services.voucher;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isAlpha;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NUMBER_PREFIX_NOT_ALPHA;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_UPDATE_FAILURE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHER_NUMBER_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;
import static org.folio.rest.impl.AbstractHelper.CONFIG_QUERY;
import static org.folio.rest.impl.AbstractHelper.INVOICE_CONFIG_MODULE_NAME;
import static org.folio.rest.impl.AbstractHelper.SYSTEM_CONFIG_QUERY;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Invoice;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.services.config.TenantConfigurationService;
import org.folio.services.validator.VoucherValidator;

import io.vertx.core.json.JsonObject;

public class VoucherCommandService {
  public static final String VOUCHER_NUMBER_CONFIG_NAME = "voucherNumber";
  public static final String VOUCHER_NUMBER_PREFIX_CONFIG = "voucherNumberPrefix";
  public static final String VOUCHER_NUMBER_PREFIX_CONFIG_QUERY = String.format(CONFIG_QUERY, INVOICE_CONFIG_MODULE_NAME, VOUCHER_NUMBER_CONFIG_NAME);


  private final RestClient voucherStorageRestClient;
  private final RestClient voucherNumberStorageRestClient;
  private final VoucherRetrieveService voucherRetrieveService;
  private final VoucherValidator voucherValidator;
  private final TenantConfigurationService tenantConfigurationService;

  public VoucherCommandService(RestClient voucherStorageRestClient, RestClient voucherNumberStorageRestClient,
                               VoucherRetrieveService voucherRetrieveService,
                               VoucherValidator voucherValidator, TenantConfigurationService tenantConfigurationService) {
    this.voucherStorageRestClient = voucherStorageRestClient;
    this.voucherNumberStorageRestClient = voucherNumberStorageRestClient;
    this.voucherRetrieveService = voucherRetrieveService;
    this.voucherValidator = voucherValidator;
    this.tenantConfigurationService = tenantConfigurationService;
  }


  public CompletableFuture<Void> partialVoucherUpdate(String voucherId, Voucher voucher, RequestContext requestContext) {
    return voucherRetrieveService.getVoucherById(voucherId, requestContext)
                                .thenAccept(voucherFromStorage -> voucherValidator.validateProtectedFields(voucher, voucherFromStorage))
                                .thenCompose(aVoid -> updateVoucher(voucherId, voucher, requestContext));
  }


  public CompletableFuture<Void> updateVoucher(String voucherId, Voucher voucher, RequestContext requestContext) {
    return voucherStorageRestClient.put(voucherId, voucher, requestContext);
  }

  /**
   * Updates associated Voucher status to Paid.
   *
   * @param invoiceId invoice id
   * @return CompletableFuture that indicates when transition is completed
   */
  public CompletableFuture<Void> payInvoiceVoucher(String invoiceId, RequestContext requestContext) {
    return voucherRetrieveService.getVoucherByInvoiceId(invoiceId, requestContext)
      .thenApply(voucher -> Optional.ofNullable(voucher).orElseThrow(() -> new HttpException(500, VOUCHER_NOT_FOUND.toError())))
      .thenCompose(voucher -> updateVoucherStatusToPaid(voucher, requestContext));
  }

  public CompletableFuture<Voucher> createVoucher(Voucher voucher, RequestContext requestContext) {
    return voucherStorageRestClient.post(voucher, requestContext, Voucher.class)
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
    return voucherNumberStorageRestClient.get(resourcesPath(VOUCHER_NUMBER_STORAGE), requestContext, SequenceNumber.class)
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

  private void validateVoucherNumberPrefix(String prefix) {
    if (StringUtils.isNotEmpty(prefix) && !isAlpha(prefix)) {
      throw new HttpException(500, VOUCHER_NUMBER_PREFIX_NOT_ALPHA);
    }
  }

  private CompletableFuture<String> getVoucherNumberPrefix(RequestContext requestContext) {
    return tenantConfigurationService.getConfigurationsEntries(requestContext, SYSTEM_CONFIG_QUERY, VOUCHER_NUMBER_PREFIX_CONFIG_QUERY)
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
   * In case voucher's status is already Paid, returns completed future. Otherwise updates voucher in storage with Paid status.
   * @param voucher voucher to update status to Paid for
   * @return completed future on success or with {@link HttpException} if update fails
   */
  private CompletableFuture<Void> updateVoucherStatusToPaid(Voucher voucher, RequestContext requestContext) {
    if (voucher.getStatus() == Voucher.Status.PAID) {
      // Voucher already marked as paid
      return completedFuture(null);
    } else {
      return updateVoucher(voucher.getId(), voucher.withStatus(Voucher.Status.PAID), requestContext)
        .exceptionally(fail -> {
          throw new HttpException(500, VOUCHER_UPDATE_FAILURE.toError());
        });
    }
  }

}
