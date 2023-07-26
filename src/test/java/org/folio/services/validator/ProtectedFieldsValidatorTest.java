package org.folio.services.validator;

import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;

public class ProtectedFieldsValidatorTest {

  @Test
  void test() {
    UtilityClassTester.assertUtilityClass(ProtectedFieldsValidator.class);
  }

}
