package org.folio.services.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.folio.CopilotGenerated;
import org.folio.rest.acq.model.settings.CommonSetting;
import org.folio.rest.acq.model.settings.CommonSettingsCollection;
import org.folio.rest.acq.model.settings.Value;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

@CopilotGenerated(model = "Claude Sonnet 4")
class CommonSettingsServiceTest {

  private CommonSettingsService commonSettingsService;
  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = mock(RestClient.class);
    commonSettingsService = new CommonSettingsService(restClient);
  }

  @Test
  void voucherNumberPrefixExists() {
    Configs configs = new Configs().withConfigs(List.of(
      new Config().withValue("{\"voucherNumberPrefix\":\"INV\"}")
    ));
    RequestEntry requestEntry = mock(RequestEntry.class);
    RequestContext requestContext = mock(RequestContext.class);

    when(restClient.get(requestEntry, Configs.class, requestContext)).thenReturn(Future.succeededFuture(configs));

    Future<String> result = commonSettingsService.getVoucherNumberPrefix(requestEntry, requestContext);

    assertEquals("INV", result.result());
  }

  @Test
  void voucherNumberPrefixNotExists() {
    Configs configs = new Configs().withConfigs(List.of(
      new Config().withValue("{\"otherKey\":\"value\"}")
    ));
    RequestEntry requestEntry = mock(RequestEntry.class);
    RequestContext requestContext = mock(RequestContext.class);

    when(restClient.get(requestEntry, Configs.class, requestContext)).thenReturn(Future.succeededFuture(configs));

    Future<String> result = commonSettingsService.getVoucherNumberPrefix(requestEntry, requestContext);

    assertEquals("", result.result());
  }

  @Test
  void systemCurrencyExists() {
    CommonSetting setting = new CommonSetting()
      .withKey("tenantLocaleSettings")
      .withValue(new Value().withAdditionalProperty("currency", "EUR"));
    CommonSettingsCollection collection = new CommonSettingsCollection().withItems(List.of(setting));
    RequestEntry requestEntry = mock(RequestEntry.class);
    RequestContext requestContext = mock(RequestContext.class);

    when(restClient.get(requestEntry, CommonSettingsCollection.class, requestContext)).thenReturn(Future.succeededFuture(collection));

    Future<String> result = commonSettingsService.getSystemCurrency(requestEntry, requestContext);

    assertEquals("EUR", result.result());
  }

  @Test
  void systemCurrencyDefault() {
    CommonSettingsCollection collection = new CommonSettingsCollection().withItems(List.of());
    RequestEntry requestEntry = mock(RequestEntry.class);
    RequestContext requestContext = mock(RequestContext.class);

    when(restClient.get(requestEntry, CommonSettingsCollection.class, requestContext)).thenReturn(Future.succeededFuture(collection));

    Future<String> result = commonSettingsService.getSystemCurrency(requestEntry, requestContext);

    assertEquals("USD", result.result());
  }

  @Test
  void loadSettingsReturnsEmptyList() {
    CommonSettingsCollection collection = new CommonSettingsCollection().withItems(null);
    RequestEntry requestEntry = mock(RequestEntry.class);
    RequestContext requestContext = mock(RequestContext.class);

    when(restClient.get(requestEntry, CommonSettingsCollection.class, requestContext)).thenReturn(Future.succeededFuture(collection));

    Future<List<CommonSetting>> result = commonSettingsService.loadSettings(requestEntry, requestContext);

    assertTrue(result.result().isEmpty());
  }

  @Test
  void loadSettingsReturnsNonEmptyList() {
    CommonSetting setting = new CommonSetting().withKey("key").withValue(new Value());
    CommonSettingsCollection collection = new CommonSettingsCollection().withItems(List.of(setting));
    RequestEntry requestEntry = mock(RequestEntry.class);
    RequestContext requestContext = mock(RequestContext.class);

    when(restClient.get(requestEntry, CommonSettingsCollection.class, requestContext)).thenReturn(Future.succeededFuture(collection));

    Future<List<CommonSetting>> result = commonSettingsService.loadSettings(requestEntry, requestContext);

    assertEquals(1, result.result().size());
    assertEquals("key", result.result().getFirst().getKey());
  }
}
