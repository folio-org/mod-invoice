package org.folio.dataimport.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.folio.DataImportEventPayload;
import org.folio.rest.RestConstants;
import org.folio.rest.RestVerticle;
import org.folio.utils.UserPermissionsUtil;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Maps;

public class DataImportUtilsTest {

  private static final String TENANT = "diku";
  private static final String TOKEN = "token";
  private static final String OKAPI_URL = "okapi_url";
  private static final String PERMISSIONS_ARRAY = "[\"invoices.acquisitions-units-assignments.assign\"]";
  private static final String USER_ID = "userId";

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

  @Test
  public void shouldAlsoReturnUserIdIfExistsInContext() {
    HashMap<String, String> context = new HashMap<>();
    context.put(DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_USER_ID, USER_ID);
    DataImportEventPayload payload = getPayload(context);

    Map<String, String> okapiHeaders = DataImportUtils.getOkapiHeaders(payload);

    assertEquals(4, okapiHeaders.size());
    assertEquals(TENANT, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    assertEquals(TOKEN, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TOKEN));
    assertEquals(OKAPI_URL, okapiHeaders.get(RestConstants.OKAPI_URL));
    assertEquals(USER_ID, okapiHeaders.get(RestVerticle.OKAPI_USERID_HEADER));
  }

  @Test
  public void shouldReturlAllPossibleAuthValues() {
    HashMap<String, String> context = new HashMap<>();
    context.put(DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS, PERMISSIONS_ARRAY);
    context.put(DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_USER_ID, USER_ID);
    DataImportEventPayload payload = getPayload(context);

    Map<String, String> okapiHeaders = DataImportUtils.getOkapiHeaders(payload);

    assertEquals(5, okapiHeaders.size());
    assertEquals(TENANT, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    assertEquals(TOKEN, okapiHeaders.get(RestVerticle.OKAPI_HEADER_TOKEN));
    assertEquals(OKAPI_URL, okapiHeaders.get(RestConstants.OKAPI_URL));
    assertEquals(PERMISSIONS_ARRAY, okapiHeaders.get(UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS));
    assertEquals(USER_ID, okapiHeaders.get(RestVerticle.OKAPI_USERID_HEADER));
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
