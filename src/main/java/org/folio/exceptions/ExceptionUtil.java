package org.folio.exceptions;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.invoices.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;
import static org.folio.invoices.utils.ErrorCodes.GENERIC_ERROR_CODE;

public class ExceptionUtil {
  private static final String ERROR_CAUSE = "cause";

  private ExceptionUtil() {
  }

  public static Errors convertToErrors(Throwable throwable) {
    final Throwable cause = Optional.ofNullable(throwable.getCause()).orElse(throwable);
    Errors errors;
    if (cause instanceof HttpException) {
      errors = ((HttpException) cause).getErrors();
      List<Error> errorList = errors.getErrors().stream().map(ExceptionUtil::mapToError).collect(toList());
      errors.setErrors(errorList);
    } else {
      errors = new Errors().withErrors(Collections.singletonList(GENERIC_ERROR_CODE.toError()
                           .withAdditionalProperty(ERROR_CAUSE, cause.getMessage())))
                           .withTotalRecords(1);
    }
    return errors;
  }

  @SuppressWarnings("java:S5852")
  public static boolean isErrorMessageJson(String errorMessage) {
    if (!StringUtils.isEmpty(errorMessage)) {
      Pattern pattern = Pattern.compile("(message).*(code).*(parameters)");
      Matcher matcher = pattern.matcher(errorMessage);
      if (matcher.find()) {
        return matcher.groupCount() == 3;
      }
    }
    return false;
  }


  private static Error mapToError(Error error) {
    if (isErrorMessageJson(error.getMessage())) {
      String jsonMessage = error.getMessage().substring(error.getMessage().indexOf("{"), error.getMessage().lastIndexOf("}") + 1);
      return new JsonObject(jsonMessage).mapTo(Error.class);
    }
    return error;
  }
}
