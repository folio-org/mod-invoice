package org.folio.services.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.folio.CopilotGenerated;
import org.folio.rest.acq.model.Setting;
import org.folio.rest.acq.model.SettingCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

@CopilotGenerated(model = "Claude Sonnet 4")
class CommonSettingsServiceTest {

  private CommonSettingsService commonSettingsService;
  private RestClient restClient;
  private RequestContext requestContext;

  @BeforeEach
  void setUp() {
    restClient = mock(RestClient.class);
    requestContext = mock(RequestContext.class);
    commonSettingsService = new CommonSettingsService(restClient);
  }

  @Test
  void voucherNumberPrefixExists() {
    SettingCollection settings = new SettingCollection().withSettings(List.of(
      new Setting().withValue("{\"voucherNumberPrefix\":\"INV\"}")
    ));
    RequestEntry requestEntry = mock(RequestEntry.class);

    when(restClient.get(requestEntry, SettingCollection.class, requestContext)).thenReturn(Future.succeededFuture(settings));

    Future<String> result = commonSettingsService.getVoucherNumberPrefix(requestEntry, requestContext);

    assertEquals("INV", result.result());
  }

  @Test
  void voucherNumberPrefixNotExists() {
    SettingCollection settings = new SettingCollection().withSettings(List.of(
      new Setting().withValue("{\"otherKey\":\"value\"}")
    ));
    RequestEntry requestEntry = mock(RequestEntry.class);

    when(restClient.get(requestEntry, SettingCollection.class, requestContext)).thenReturn(Future.succeededFuture(settings));

    Future<String> result = commonSettingsService.getVoucherNumberPrefix(requestEntry, requestContext);

    assertEquals("", result.result());
  }

  @Test
  void systemCurrencyExists() {
    var localeResponse = new JsonObject()
      .put("locale", "en-US")
      .put("currency", "EUR")
      .put("timezone", "UTC")
      .put("numberingSystem", "latn");

    when(restClient.getAsJsonObject(anyString(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(localeResponse));

    Future<String> result = commonSettingsService.getSystemCurrency(null, requestContext);

    assertEquals("EUR", result.result());
  }

  @Test
  void systemCurrencyDefault() {
    when(restClient.getAsJsonObject(anyString(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(new JsonObject()));

    Future<String> result = commonSettingsService.getSystemCurrency(null, requestContext);

    assertEquals("USD", result.result());
  }

  @Test
  void systemCurrencyDefaultWhenResponseIsNull() {
    when(restClient.getAsJsonObject(anyString(), eq(requestContext)))
      .thenReturn(Future.succeededFuture(null));

    Future<String> result = commonSettingsService.getSystemCurrency(null, requestContext);

    assertEquals("USD", result.result());
  }

}
