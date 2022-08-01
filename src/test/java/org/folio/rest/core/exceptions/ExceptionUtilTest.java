package org.folio.rest.core.exceptions;

import org.folio.exceptions.ExceptionUtil;
import org.folio.rest.jaxrs.model.Errors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExceptionUtilTest {

  @Test
  void testIfBadRequestAndExceptionIsVertxHttp() {
    io.vertx.ext.web.handler.HttpException reply = new io.vertx.ext.web.handler.HttpException(400, "Test");

    Errors errors = ExceptionUtil.convertToErrors(reply);

    assertEquals("Test", errors.getErrors().get(0).getMessage());
    assertEquals("400", errors.getErrors().get(0).getCode());
  }
  @Test
  void testIsExceptionMessageIsJSON() {
    String errorMsg = "{\"message\":\"Test\",\"code\":\"Test\",\"parameters\":[]}";
    boolean act = ExceptionUtil.isErrorMessageJson(errorMsg);
    Assertions.assertTrue(act);
  }
}
