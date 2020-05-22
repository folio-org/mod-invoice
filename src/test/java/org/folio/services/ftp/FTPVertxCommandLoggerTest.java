package org.folio.services.ftp;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.vertx.core.logging.Logger;

public class FTPVertxCommandLoggerTest {
  Logger log = mock(Logger.class);

  @BeforeEach
  void prepare() {
    doNothing().when(log).debug(anyString());
    doNothing().when(log).info(anyString());
    doNothing().when(log).warn(anyString());
    doNothing().when(log).error(anyString());
  }

  @Test
  public void testLogInfo() {
    FTPVertxCommandLogger obj = new FTPVertxCommandLogger(log, Level.INFO);
    obj.write('\n');
    verify(log).info(anyString());
  }
}
