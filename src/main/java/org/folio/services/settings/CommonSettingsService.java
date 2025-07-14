package org.folio.services.settings;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.acq.model.settings.CommonSetting;
import org.folio.rest.acq.model.settings.CommonSettingsCollection;
import org.folio.rest.acq.model.settings.Value;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.springframework.stereotype.Service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class CommonSettingsService {

  public static final String VOUCHER_NUMBER_PREFIX_CONFIG_QUERY = "(module==INVOICE and configName==voucherNumber)";
  public static final String VOUCHER_NUMBER_PREFIX_KEY = "voucherNumberPrefix";

  public static final String SETTINGS_QUERY = "(scope==stripes-core.prefs.manage and key==tenantLocaleSettings)";
  public static final String TENANT_LOCALE_SETTINGS = "tenantLocaleSettings";
  public static final String CURRENCY_KEY = "currency";
  public static final String CURRENCY_DEFAULT = "USD";

  private final RestClient restClient;

  /**
   * Get voucher number prefix via config from the deprecated <code>mod-configurations</code>
   *
   * @param requestContext the request context
   * @return a future containing the voucher number prefix if it exists, otherwise an empty string
   */
  public Future<String> getVoucherNumberPrefix(RequestEntry requestEntry, RequestContext requestContext) {
    return restClient.get(requestEntry, Configs.class, requestContext)
      .map(configs -> StreamEx.of(configs.getConfigs())
        .map(Config::getValue)
        .nonNull()
        .map(value -> new JsonObject(value).getString(VOUCHER_NUMBER_PREFIX_KEY))
        .findFirst(StringUtils::isNotBlank)
        .orElse(EMPTY));
  }

  /**
   * Get system currency via tenant locale settings from <code>mod-settings</code>.
   *
   * @param requestEntry the request entry
   * @param requestContext the request context
   * @return a future containing the currency if it exists, otherwise the default currency
   */
  public Future<String> getSystemCurrency(RequestEntry requestEntry, RequestContext requestContext) {
    return loadTenantLocaleSetting(CURRENCY_KEY, CURRENCY_DEFAULT, requestEntry, requestContext);
  }

  /**
   * Load tenant locale settings from <code>mod-settings</code>.
   *
   * @param requestEntry the request entry
   * @param requestContext the request context
   * @return a future containing the list of common settings
   */
  public Future<List<CommonSetting>> loadSettings(RequestEntry requestEntry, RequestContext requestContext) {
    return restClient.get(requestEntry, CommonSettingsCollection.class, requestContext)
      .map(settingsCollection -> Optional.ofNullable(settingsCollection.getItems()).orElse(List.of()));
  }

  private Future<String> loadTenantLocaleSetting(String key, String defaultValue, RequestEntry requestEntry, RequestContext requestContext) {
    return loadSettings(requestEntry, requestContext).map(settings -> settings.stream()
      .filter(setting -> TENANT_LOCALE_SETTINGS.equals(setting.getKey()))
      .findFirst()
      .map(CommonSetting::getValue)
      .map(Value::getAdditionalProperties)
      .map(properties -> properties.get(key))
      .map(Object::toString)
      .orElse(defaultValue));
  }

}
