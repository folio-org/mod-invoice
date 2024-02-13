package org.folio.services.voucher;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_NOT_FOUND;
import static org.folio.invoices.utils.ErrorCodes.VOUCHER_UPDATE_FAILURE;
import static org.folio.invoices.utils.ResourcePathResolver.VOUCHERS_STORAGE;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.validator.VoucherValidator;

public class VoucherService {
  public static final String QUERY_BY_INVOICE_ID = "invoiceId==%s";
  public static final String VOUCHER_ENDPOINT = resourcesPath(VOUCHERS_STORAGE);
  public static final String VOUCHER_BY_ID_ENDPOINT = resourcesPath(VOUCHERS_STORAGE) + "/{id}";

  private final RestClient restClient;
  private final VoucherValidator voucherValidator;

  public VoucherService(RestClient restClient, VoucherValidator voucherValidator) {
    this.restClient = restClient;
    this.voucherValidator = voucherValidator;
  }

  /**
   * Updates associated Voucher status to Paid.
   *
   * @param invoiceId invoice id
   * @return CompletableFuture that indicates when transition is completed
   */
  public Future<Void> payInvoiceVoucher(String invoiceId, RequestContext requestContext) {
    return getVoucherByInvoiceId(invoiceId, requestContext)
      .map(voucher -> Optional.ofNullable(voucher).orElseThrow(() -> new HttpException(404, VOUCHER_NOT_FOUND.toError())))
      .compose(voucher -> updateVoucherStatus(voucher, Voucher.Status.PAID, requestContext));
  }

  /**
   * Gets list of voucher
   *
   * @param limit  Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query  A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link VoucherCollection} on success or an exception if processing fails
   */
  public Future<VoucherCollection> getVouchers(String query, int offset, int limit, RequestContext requestContext) {
    return getVouchers(limit, offset, query, requestContext);
  }

  /**
   * Handles update of the voucher. Allows update of editable fields:
   * <ul>
   *   <li>voucher.dispersementNumber</li>
   *   <li>voucher.dispersementDate</li>
   *   <li>voucher.dispersementAmount</li>
   *   <li>voucher.voucherNumber</li>
   * </ul>
   * Attempting to edit any other fields will result in an {@link HttpException}.
   *
   * @param voucherId      updated {@link Voucher} voucher id
   * @param voucher updated {@link Voucher} voucher
   * @return future holding response indicating success or error if failed
   */
  public Future<Void> partialVoucherUpdate(String voucherId, Voucher voucher, RequestContext requestContext) {
    return getVoucherById(voucherId, requestContext)
      .map(voucherFromStorage -> {
        voucherValidator.validateProtectedFields(voucher, voucherFromStorage);
        return null;
      })
      .compose(aVoid -> updateVoucher(voucherId, voucher, requestContext));
  }

  public Future<Voucher> getVoucherById(String voucherId, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_BY_ID_ENDPOINT).withId(voucherId);
    return restClient.get(requestEntry, Voucher.class, requestContext);
  }

  public Future<VoucherCollection> getVouchers(int limit, int offset, String query, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_ENDPOINT).withQuery(query).withLimit(limit).withOffset(offset);
    return restClient.get(requestEntry, VoucherCollection.class, requestContext);
  }

  public Future<Voucher> getVoucherByInvoiceId(String invoiceId, RequestContext requestContext) {
    return getVouchers(1, 0, String.format(QUERY_BY_INVOICE_ID, invoiceId), requestContext).map(VoucherCollection::getVouchers)
      .map(vouchers -> vouchers.isEmpty() ? null : vouchers.get(0));
  }

  /**
   * In case voucher's status is already the given one, returns completed future.
   * Otherwise updates voucher in storage with given status.
   * @param voucher existing voucher
   * @param status new voucher status
   * @return completed future on success or with {@link HttpException} if update fails
   */
  private Future<Void> updateVoucherStatus(Voucher voucher, Voucher.Status status, RequestContext requestContext) {
    if (voucher.getStatus() == status) {
      // Voucher already has the status
      return succeededFuture(null);
    }
    return updateVoucher(voucher.getId(), voucher.withStatus(status), requestContext)
      .recover(fail -> {
        var param1 = new Parameter().withKey("voucherId").withValue(voucher.getId());
        var param2 = new Parameter().withKey("voucherStatus").withValue(voucher.getStatus().value());
        var errorParam = new Parameter().withKey("errorMessage").withValue(fail.getMessage());
        throw new HttpException(500, VOUCHER_UPDATE_FAILURE, List.of(param1, param2, errorParam));
      });
  }

  public Future<Void> updateVoucher(String voucherId, Voucher voucher, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_BY_ID_ENDPOINT).withId(voucherId);
    return restClient.put(requestEntry, voucher, requestContext);
  }

  /**
   * Updates associated Voucher status to Cancelled, does nothing if no associated voucher is found.
   *
   * @param invoiceId invoice id
   * @return CompletableFuture that indicates when transition is completed
   */
  public Future<Void> cancelInvoiceVoucher(String invoiceId, RequestContext requestContext) {
    return getVoucherByInvoiceId(invoiceId, requestContext)
      .compose(voucher -> {
        if (voucher == null)
          return succeededFuture(null);
        return updateVoucherStatus(voucher, Voucher.Status.CANCELLED, requestContext);
      });
  }
  public Future<Voucher> createVoucher(Voucher voucher, RequestContext requestContext) {
    RequestEntry requestEntry = new RequestEntry(VOUCHER_ENDPOINT);
    return restClient.post(requestEntry, voucher, Voucher.class, requestContext);
  }
}
