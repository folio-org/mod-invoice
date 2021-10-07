package org.folio.dataimport.utils;

import com.google.common.collect.Maps;
import org.folio.DataImportEventPayload;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.utils.UserPermissionsUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataImportUtilsTest {

  private static final String TENANT = "diku";
  private static final String TOKEN = "token";
  private static final String OKAPI_URL = "okapi_url";
  public static final String PERMISSIONS_ARRAY = "[\"invoices.acquisitions-units-assignments.assign\"]";

  @Test
  public void shouldNotReturnOkapiPermissionsIfNotExistsInContext() {
    DataImportEventPayload payload = getPayload(Maps.newHashMap());

    Map<String, String> okapiHeaders = DataImportUtils.getOkapiHeaders(payload);

    assertEquals(3, okapiHeaders.size());
    assertEquals(TENANT, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    assertEquals(TOKEN, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TOKEN));
    assertEquals(OKAPI_URL, okapiHeaders.get(RestConstants.OKAPI_URL));
  }

  @Test
  public void shouldAlsoReturnOkapiPermissionsIfExistsInContext() {
    HashMap<String, String> context = new HashMap<>();
    context.put(DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS, PERMISSIONS_ARRAY);
    DataImportEventPayload payload = getPayload(context);

    Map<String, String> okapiHeaders = DataImportUtils.getOkapiHeaders(payload);

    assertEquals(4, okapiHeaders.size());
    assertEquals(TENANT, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    assertEquals(TOKEN, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TOKEN));
    assertEquals(OKAPI_URL, okapiHeaders.get(RestConstants.OKAPI_URL));
    assertEquals(PERMISSIONS_ARRAY, okapiHeaders.get(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS));
  }

  private DataImportEventPayload getPayload(HashMap<String, String> context) {
    DataImportEventPayload payload = new DataImportEventPayload();
    payload.setTenant(TENANT);
    payload.setToken(TOKEN);
    payload.setOkapiUrl(OKAPI_URL);
    payload.setContext(context);
    return payload;
  }
}
