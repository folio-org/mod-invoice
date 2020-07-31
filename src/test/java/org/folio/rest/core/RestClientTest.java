package org.folio.rest.core;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.invoices.utils.HelperUtils.OKAPI_URL;
import static org.folio.invoices.utils.ResourcePathResolver.FINANCE_STORAGE_TRANSACTIONS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_TOKEN;
import static org.folio.rest.impl.ApiTestBase.X_OKAPI_USER_ID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.json.JsonObject;

public class RestClientTest {
  public static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoiceimpltest");

  @Mock
  private EventLoopContext ctxMock;
  @Mock
  private HttpClientInterface httpClient;

  private Map<String, String> okapiHeaders;
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.initMocks(this);
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + 8081);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    requestContext = new RequestContext(ctxMock, okapiHeaders);
  }

  @Test
  public void testGetShouldSearchById() throws Exception {
    RestClient restClient = Mockito.spy(new RestClient(FINANCE_STORAGE_TRANSACTIONS));

    String uuid = UUID.randomUUID().toString();
    String endpoint = FINANCE_STORAGE_TRANSACTIONS + "/" + uuid;
    Transaction expTransaction = new Transaction().withId(uuid);
    Response response = new Response();
    response.setBody(JsonObject.mapFrom(expTransaction));
    response.setCode(200);

    doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);
    doReturn(completedFuture(response)).when(httpClient).request(HttpMethod.GET, endpoint, okapiHeaders);

    Transaction actTransaction = restClient.getById(uuid, requestContext, Transaction.class).join();

    assertThat(actTransaction, equalTo(expTransaction));
  }

  @Test
  public void testGetShouldThrowExceptionWhenSearchById() throws Exception {
    Assertions.assertThrows(CompletionException.class, () -> {
      RestClient restClient = Mockito.spy(new RestClient(FINANCE_STORAGE_TRANSACTIONS));

      String uuid = UUID.randomUUID().toString();
      doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);

      restClient.getById(uuid, requestContext, Transaction.class).join();
    });
  }

  @Test
  public void testPostShouldCreateEntity() throws Exception {
    RestClient restClient = Mockito.spy(new RestClient(FINANCE_STORAGE_TRANSACTIONS));

    String uuid = UUID.randomUUID().toString();
    Transaction expTransaction = new Transaction().withId(uuid);
    Response response = new Response();
    response.setBody(JsonObject.mapFrom(expTransaction));
    response.setCode(201);

    doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);
    doReturn(completedFuture(response)).when(httpClient).request(eq(HttpMethod.POST), any(), eq(FINANCE_STORAGE_TRANSACTIONS), eq(okapiHeaders));

    Transaction actTransaction = restClient.post(expTransaction, requestContext, Transaction.class).join();

    assertThat(actTransaction, equalTo(expTransaction));
  }

  @Test
  public void testShouldThrowExceptionWhenCreatingEntity() throws Exception {
    Assertions.assertThrows(CompletionException.class, () -> {
      RestClient restClient = Mockito.spy(new RestClient(FINANCE_STORAGE_TRANSACTIONS));

      String uuid = UUID.randomUUID().toString();
      Transaction expTransaction = new Transaction().withId(uuid);
      doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);

      restClient.post(expTransaction, requestContext, Transaction.class).join();
    });
  }
}
