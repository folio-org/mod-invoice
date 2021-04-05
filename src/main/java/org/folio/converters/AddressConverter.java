package org.folio.converters;

import org.folio.rest.acq.model.Address;
import org.folio.rest.jaxrs.model.VendorAddress;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;

@Service
public class AddressConverter implements Converter<Address, VendorAddress> {

  private static class SingletonHolder {
    public static final AddressConverter HOLDER_INSTANCE = new AddressConverter();
  }

  public static AddressConverter getInstance() {
    return AddressConverter.SingletonHolder.HOLDER_INSTANCE;
  }

  @Override
  public VendorAddress convert(Address address) {
    return new VendorAddress()
      .withAddressLine1(address.getAddressLine1())
      .withAddressLine2(address.getAddressLine2())
      .withCity(address.getCity())
      .withStateRegion(address.getStateRegion())
      .withZipCode(address.getZipCode())
      .withCountry(address.getCountry());
  }
}
