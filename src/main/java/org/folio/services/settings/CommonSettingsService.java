package org.folio.services.settings;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.acq.model.Setting;
import org.folio.rest.acq.model.SettingCollection;
import org.folio.rest.acq.model.settings.CommonSetting;
import org.folio.rest.acq.model.settings.CommonSettingsCollection;
import org.folio.rest.acq.model.settings.Value;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
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

  public static final String VOUCHER_NUMBER_PREFIX_KEY = "USD";

  public static final String CURRENCY_KEY = "currency";
  public static final String CURRENCY_DEFAULT = "USD";

  private final RestClient restClient;

  /**
   * Get voucher number prefix via config from the internal <code>mod-invoice-storage</code> settings table
   *
   * @param requestContext the request context
   * @return a future containing the voucher number prefix if it exists, otherwise an empty string
   */
  public Future<String> getVoucherNumberPrefix(RequestEntry requestEntry, RequestContext requestContext) {
    return restClient.get(requestEntry, SettingCollection.class, requestContext)
      .map(settingCollection -> StreamEx.of(settingCollection.getSettings())
        .map(Setting::getValue)
        .nonNull()
        .map(value -> new JsonObject(value).getString(VOUCHER_NUMBER_PREFIX_KEY))
        .findFirst(StringUtils::isNotBlank)
        .orElse(EMPTY));
  }

  public Future<String> getSystemCurrency(RequestEntry requestEntry, RequestContext requestContext) {
    return getGlobalSetting(CURRENCY_KEY, CURRENCY_DEFAULT, requestEntry, requestContext);
  }

  private Future<String> getGlobalSetting(String key, String defaultValue, RequestEntry requestEntry, RequestContext requestContext) {
    return restClient.get(requestEntry, CommonSettingsCollection.class, requestContext)
      .map(settingsCollection -> Optional.ofNullable(settingsCollection.getItems()).orElse(List.of()))
      .map(settings -> settings.stream()
        .findFirst()
        .map(CommonSetting::getValue)
        .map(Value::getAdditionalProperties)
        .map(properties -> properties.get(key))
        .map(Object::toString)
        .orElse(defaultValue));
  }

}
