package org.folio.rest.impl.protection;

import org.folio.rest.impl.ApiTestBase;

import io.restassured.http.Headers;
import io.restassured.response.Response;

enum ProtectedOperations {

  CREATE(201) {
    @Override
    Response process(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
      return apiTestBase.verifyPostResponse(url, body, headers, expectedContentType, expectedCode);
    }
  };

  private int code;
  private String contentType;

  ProtectedOperations(int code, String contentType) {
    this.code = code;
    this.contentType = contentType;
  }

  ProtectedOperations(int code) {
    this(code, "");
  }

  public int getCode() {
    return code;
  }

  public String getContentType() {
    return contentType;
  }

  private static ApiTestBase apiTestBase = new ApiTestBase();

  abstract Response process(String url, String body, Headers headers, String expectedContentType, int expectedCode);

}
