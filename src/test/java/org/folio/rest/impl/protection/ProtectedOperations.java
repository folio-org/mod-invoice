package org.folio.rest.impl.protection;

import org.folio.rest.impl.ApiTestBase;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;


enum ProtectedOperations {

  CREATE(201) {
    @Override
    Response process(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
      return apiTestBase.verifyPostResponse(url, body, headers, expectedContentType, expectedCode);
    }
  },
  READ(200) {
    @Override
    Response process(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
      JsonObject obj = new JsonObject(body);
      String id = obj.getString("id");
      return apiTestBase.verifyGet(String.format("%s/%s", url, id), headers, expectedContentType, expectedCode);
      }
    },
  UPDATE(204) {
    @Override
    Response process(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
      return null;
    }
  },
  DELETE(204) {
    @Override
    Response process(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
      return null;
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
