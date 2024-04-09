package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.folio.converters.AddressConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.Address;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.VendorRetrieveService;
import org.folio.services.invoice.BaseInvoiceService;
import org.folio.services.voucher.VoucherNumberService;
import org.folio.services.voucher.VoucherService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class VoucherHelper extends AbstractHelper {

  @Autowired
  private VendorRetrieveService vendorRetrieveService;
  @Autowired
  private VoucherService voucherService;
  @Autowired
  private AddressConverter addressConverter;
  @Autowired
  private VoucherNumberService voucherNumberService;
  @Autowired
  private BaseInvoiceService baseInvoiceService;

  public VoucherHelper(Map<String, String> okapiHeaders, Context ctx) {
    super(okapiHeaders, ctx);
    SpringContextUtil.autowireDependencies(this, ctx);
  }
  public Future<Voucher> getVoucher(String id, RequestContext requestContext) {
    return voucherService.getVoucherById(id, requestContext)
      .compose(voucher -> Optional.ofNullable(voucher.getVendorId())
      .map(vendorId -> vendorRetrieveService.getVendor(voucher.getVendorId(), requestContext)
        .map(organization -> populateVendorAddress(voucher, organization)))
      .orElseGet(() -> succeededFuture(voucher)));
  }

  private Voucher populateVendorAddress(Voucher voucher, Organization organization) {
    Optional.ofNullable(organization)
      .map(Organization::getAddresses)
      .stream()
      .flatMap(Collection::stream)
      .filter(Address::getIsPrimary)
      .findFirst()
      .ifPresent(address -> voucher.setVendorAddress(addressConverter.convert(address)));
    return voucher;
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
   * @param id      updated {@link Voucher} voucher id
   * @param voucher updated {@link Voucher} voucher
   * @return completable future holding response indicating success or error if failed
   */
  public Future<Void> partialVoucherUpdate(String id, Voucher voucher, RequestContext requestContext) {
    return voucherService.partialVoucherUpdate(id, voucher, requestContext)
      .compose(update -> baseInvoiceService.updateVoucherNumberInInvoice(voucher, requestContext))
      .onSuccess(result -> logger.debug("The voucher number on the invoice has been updated"))
      .onFailure(error -> logger.error("An error occurred", error));
  }

  public Future<VoucherCollection> getVouchers(int limit, int offset, String query, RequestContext requestContext) {
    return voucherService.getVouchers(limit, offset, query, requestContext);
  }

  public Future<SequenceNumber> getStartValue(RequestContext requestContext) {
    return voucherNumberService.getStartValue(requestContext);
  }

  /**
   * This endpoint is a means for the UI to set/reset the start value of the voucher-number sequence
   * @param value start value to be set/reset
   * @return  future on success or {@code null} if validation fails or an exception if any issue happens
   */
  public Future<Void> setStartValue(String value, RequestContext requestContext) {
    return voucherNumberService.setStartValue(value, requestContext);
  }
}
