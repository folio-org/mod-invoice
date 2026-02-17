package org.folio.services.settings;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.invoices.utils.ResourcePathResolver.LOCALE_SETTINGS;
import static org.folio.invoices.utils.ResourcePathResolver.resourcesPath;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.acq.model.Setting;
import org.folio.rest.acq.model.SettingCollection;
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

  public static final String VOUCHER_NUMBER_PREFIX_KEY = "voucherNumberPrefix";

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

  /**
   * Get system currency from the tenant locale settings via <code>/locale</code> endpoint
   *
   * @param requestEntry  the request entry
   * @param requestContext the request context
   * @return a future containing the system currency if it exists, otherwise {@link #CURRENCY_DEFAULT}
   */
  public Future<String> getSystemCurrency(RequestEntry requestEntry, RequestContext requestContext) {
    return restClient.getAsJsonObject(resourcesPath(LOCALE_SETTINGS), requestContext)
      .map(jsonObject -> {
        if (jsonObject == null) {
          return CURRENCY_DEFAULT;
        }
        var currency = jsonObject.getString(CURRENCY_KEY);
        return StringUtils.isNotBlank(currency) ? currency : CURRENCY_DEFAULT;
      });
  }

}
