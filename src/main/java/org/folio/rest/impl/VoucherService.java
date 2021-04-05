package org.folio.rest.impl;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.converters.AddressConverter;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.acq.model.Address;
import org.folio.rest.acq.model.Organization;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Voucher;
import org.folio.rest.jaxrs.model.VoucherCollection;
import org.folio.services.VendorRetrieveService;
import org.folio.services.voucher.VoucherCommandService;
import org.folio.services.voucher.VoucherRetrieveService;
import org.springframework.beans.factory.annotation.Autowired;

public class VoucherService {

  private final VoucherRetrieveService voucherRetrieveService;
  private final VoucherCommandService voucherCommandService;
  private final VendorRetrieveService vendorRetrieveService;
  @Autowired
  private AddressConverter addressConverter;

  public VoucherService(VoucherRetrieveService voucherRetrieveService, VoucherCommandService voucherCommandService,
                        VendorRetrieveService vendorRetrieveService) {
    this.voucherRetrieveService = voucherRetrieveService;
    this.voucherCommandService = voucherCommandService;
    this.vendorRetrieveService = vendorRetrieveService;
  }

  public CompletableFuture<Voucher> getVoucher(String id, RequestContext requestContext) {
    return voucherRetrieveService.getVoucherById(id, requestContext)
        .thenCompose(voucher -> Optional.ofNullable(voucher.getVendorId())
            .map(vendorId ->  vendorRetrieveService.getVendor(voucher.getVendorId(), requestContext)
                .thenApply(organization -> populateVendorAddress(voucher, organization)))
            .orElseGet(() -> CompletableFuture.completedFuture(voucher)));
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
   * @param limit Limit the number of elements returned in the response
   * @param offset Skip over a number of elements by specifying an offset value for the query
   * @param query A query expressed as a CQL string using valid searchable fields
   * @return completable future with {@link VoucherCollection} on success or an exception if processing fails
   */
  public CompletableFuture<VoucherCollection> getVouchers(String query, int offset, int limit, RequestContext requestContext) {
    return voucherRetrieveService.getVouchers(limit, offset, query, requestContext);
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
   * @param id updated {@link Voucher} voucher id
   * @param voucher updated {@link Voucher} voucher
   * @return completable future holding response indicating success or error if failed
   */
  public CompletableFuture<Void> partialVoucherUpdate(String id, Voucher voucher, RequestContext requestContext) {
    return voucherCommandService.partialVoucherUpdate(id, voucher, requestContext);
  }
}
