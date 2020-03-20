package org.folio.invoices.utils;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;

public class UploadHelperFactory {

  private UploadHelperFactory() {

  }

  public static UploadHelper get(String uri) throws URISyntaxException {
    String proto = new URI(uri).getScheme();

    if (StringUtils.isEmpty(proto) || proto.equalsIgnoreCase("FTP")) {
      return new FtpUploadHelper(uri);
    } else {
      return null;
    }
  }

}
