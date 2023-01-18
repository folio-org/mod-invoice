package org.folio.rest;

public final class RestConstants {
  public static final String OKAPI_URL = "X-Okapi-Url";
  public static final String SEARCH_ENDPOINT = "%s?limit=%s&offset=%s%s";
  public static final int MAX_IDS_FOR_GET_RQ = 15;
  public static final String ID = "id";
  public static final int SEMAPHORE_MAX_ACTIVE_THREADS = 10;
  public static final String EXCEPTION_EXPECTED_MESSAGE = "Exception expected";

  private RestConstants () {

  }
}
