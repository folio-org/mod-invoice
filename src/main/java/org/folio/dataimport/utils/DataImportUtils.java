package org.folio.dataimport.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.DataImportEventPayload;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.utils.UserPermissionsUtil;

public class DataImportUtils {
  public static final String DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS = "data-import-payload-okapi-permissions";
  public static final String DATA_IMPORT_PAYLOAD_OKAPI_USER_ID = "data-import-payload-okapi-user-id";
  private static final String USER_ID = "userId";

  private DataImportUtils() {}

  public static Map<String, String> getOkapiHeaders(DataImportEventPayload eventPayload) {
    Map<String, String> result = new HashMap<>();
    result.put(RestVerticle.OKAPI_HEADER_TENANT, eventPayload.getTenant());
    result.put(RestConstants.OKAPI_URL, eventPayload.getOkapiUrl());

    if (!isSystemUserEnabled()) {
      result.put(RestVerticle.OKAPI_HEADER_TOKEN, eventPayload.getToken());
    }

    Optional.ofNullable(eventPayload.getContext().get(DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS))
      .filter(StringUtils::isNotBlank)
      .ifPresent(permissions -> result.put(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS, permissions));

    getUserId(eventPayload)
      .ifPresent(userId -> result.put(RestVerticle.OKAPI_USERID_HEADER, userId));

    return Collections.unmodifiableMap(result);
  }

  private static Optional<String> getUserId(DataImportEventPayload eventPayload) {
    return Optional.ofNullable(eventPayload.getContext().get(DATA_IMPORT_PAYLOAD_OKAPI_USER_ID))
      .filter(StringUtils::isNotBlank)
      .or(() -> Optional.ofNullable(eventPayload.getContext().get(USER_ID))
        .filter(StringUtils::isNotBlank));
  }

  /**
   * Checks if the system user is enabled based on a system property.
   * <p>
   * This method reads the `SYSTEM_USER_ENABLED` system property and parses
   * its value as a boolean. If the property is not found or cannot be parsed,
   * it defaults to `true`. The method then negates the parsed value and returns it.
   * <p>
   * Note: This functionality is specific to the Eureka environment.
   *
   * @return {@code true} if the system user is set for Eureka env; otherwise {@code false}.
   */
  private static boolean isSystemUserEnabled() {
    return !Boolean.parseBoolean(System.getProperty("SYSTEM_USER_ENABLED", "true"));
  }

}
