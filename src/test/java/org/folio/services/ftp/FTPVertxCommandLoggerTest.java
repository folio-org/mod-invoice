package org.folio.services.ftp;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    FTPVertxCommandLogger obj = new FTPVertxCommandLogger(log);
    obj.write('\n');
    verify(log).info(anyString());
  }

  @Test
  public void testLogInfoPass() {
    FTPVertxCommandLogger obj = new FTPVertxCommandLogger(log);
    obj.write('P');
    obj.write('A');
    obj.write('S');
    obj.write('S');
    obj.write('T');
    obj.write('E');
    obj.write('S');
    obj.write('T');
    obj.write('\n');
    verify(log).info(anyString());
  }


  @Test
  public void testLogInfoSkipp() {
    FTPVertxCommandLogger obj = new FTPVertxCommandLogger(log);
    obj.write('x');
    verify(log, never()).info(anyString());
  }

  @Test
  public void testPassLogInfo() {
    FTPVertxCommandLogger obj = new FTPVertxCommandLogger(log);
    obj.write("PASS".getBytes(), 0, "PASS".length() - 1);
    verify(log, never()).info(anyString());
  }
}
