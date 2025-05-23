package org.folio.services.finance.transaction;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.TestMockDataConstants.MOCK_ENCUMBRANCES_LIST;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.Context;
import org.folio.rest.acq.model.finance.Transaction;
import org.folio.rest.acq.model.finance.TransactionCollection;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.impl.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.verification.Times;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class BaseTransactionServiceTest extends ApiTestBase {
  @Mock
  private Context ctxMock;
  @Mock
  private RestClient restClient;

  private Map<String, String> okapiHeaders;
  public static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "invoiceimpltest");

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.openMocks(this);
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + 8081);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
  }

  @Test
  public void testShouldSuccessRetrieveExistedTransactions(VertxTestContext vertxTestContext) throws Exception {
    //given
    BaseTransactionService service = spy(new BaseTransactionService(restClient));

    JsonObject encumbranceList = new JsonObject(getMockData(MOCK_ENCUMBRANCES_LIST));

    List<Transaction> encumbrances = encumbranceList.getJsonArray("transactions").stream()
      .map(obj -> ((JsonObject) obj).mapTo(Transaction.class)).collect(toList());
    TransactionCollection trCollection = new TransactionCollection().withTransactions(encumbrances);

    RequestContext requestContext = new RequestContext(ctxMock, okapiHeaders);
    doReturn(succeededFuture(trCollection)).when(restClient).get(any(RequestEntry.class), eq(TransactionCollection.class), any(RequestContext.class));
    //When

    List<String> transactionIds = encumbrances.stream().map(Transaction::getId).collect(toList());
    var future = service.getTransactionsByIds(transactionIds, requestContext);
    vertxTestContext.assertComplete(future)
      .onComplete(result -> {
        assertThat(result.result(), hasSize(1));
        assertThat(result.result().get(0).getId(), equalTo(encumbrances.get(0).getId()));
        verify(restClient).get(any(RequestEntry.class), eq(TransactionCollection.class), any(RequestContext.class));
        vertxTestContext.completeNow();
      });
  }

  @Test
  public void testShouldSuccessRetrieveEmptyListIfIdListIsEmpty(VertxTestContext vertxTestContext) {
    //given
    BaseTransactionService service = spy(new BaseTransactionService(restClient));
    RequestContext requestContext = new RequestContext(ctxMock, okapiHeaders);
     //When
    List<String> transactionIds = new ArrayList<>();
    var future = service.getTransactionsByIds(transactionIds, requestContext);
    //Then
    vertxTestContext.assertComplete(future)
      .onComplete(result -> {
        assertThat(result.result(), hasSize(0));
        verify(restClient, new Times(0)).get(any(RequestEntry.class), eq(TransactionCollection.class), any(RequestContext.class));
        vertxTestContext.completeNow();
      });

  }
}
