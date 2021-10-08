package org.folio.dataimport.utils;

import org.apache.commons.lang3.StringUtils;
import org.folio.DataImportEventPayload;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.utils.UserPermissionsUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DataImportUtils {
  public static final String DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS = "data-import-payload-okapi-permissions";
  public static final String DATA_IMPORT_PAYLOAD_OKAPI_USER_ID = "data-import-payload-okapi-user-id";

  private DataImportUtils() {}

  public static Map<String, String> getOkapiHeaders(DataImportEventPayload eventPayload) {
    Map<String, String> result = new HashMap<>();
    result.put(RestVerticle.OKAPI_HEADER_TENANT, eventPayload.getTenant());
    result.put(RestVerticle.OKAPI_HEADER_TOKEN, eventPayload.getToken());
    result.put(RestConstants.OKAPI_URL, eventPayload.getOkapiUrl());

    String payloadPermissions = eventPayload.getContext().get(DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS);
    if (StringUtils.isNotBlank(payloadPermissions)) {
      result.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, payloadPermissions);
    }
    String userId = eventPayload.getContext().get(DATA_IMPORT_PAYLOAD_OKAPI_USER_ID);
    if (StringUtils.isNotBlank(userId)) {
      result.put(RestVerticle.OKAPI_USERID_HEADER, userId);
    }
    return Collections.unmodifiableMap(result);
  }
}
