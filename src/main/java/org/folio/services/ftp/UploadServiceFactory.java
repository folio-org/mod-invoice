package org.folio.services.ftp;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;

public class UploadServiceFactory {

  private UploadServiceFactory() {

  }

  public static UploadService get(String uri) throws URISyntaxException {
    String proto = new URI(uri).getScheme();

    if (StringUtils.isEmpty(proto) || proto.equalsIgnoreCase("FTP")) {
      return new FtpUploadService(uri);
    } else {
      return null;
    }
  }

}
