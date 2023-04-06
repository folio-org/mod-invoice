package org.folio.invoices.rest.exceptions;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.utils.ErrorCodes;
import org.folio.rest.jaxrs.model.Errors;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExceptionUtil {

  private static final Pattern ERRORS_PATTERN = Pattern.compile("(errors).*(message).*(code).*(parameters)");

  private ExceptionUtil() {
  }

  public static boolean isErrorsMessageJson(String errorsMessage) {
    if (!StringUtils.isEmpty(errorsMessage)) {
      errorsMessage = errorsMessage.replace("\r", "").replace("\n", "");
      Matcher matcher = ERRORS_PATTERN.matcher(errorsMessage);
      if (matcher.find()) {
        return matcher.groupCount() == 4;
      }
    }
    return false;
  }

  public static Errors mapToErrors(String errorsStr) {
    return new JsonObject(errorsStr).mapTo(Errors.class);
  }

  public static String errorsAsString(Errors errors) {
    return Optional.ofNullable(JsonObject.mapFrom(errors).encode()).orElse(ErrorCodes.GENERIC_ERROR_CODE.getDescription());
  }
}
